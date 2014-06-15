package fr.mtlx.odm.spring;

/*
 * #%L
 * fr.mtlx.odm
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2012 - 2013 Alexandre Mathieu <me@mtlx.fr>
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.SizeLimitExceededException;
import org.springframework.ldap.control.PagedResultsCookie;
import org.springframework.ldap.control.PagedResultsDirContextProcessor;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DirContextProcessor;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;

import com.google.common.collect.Sets;

import fr.mtlx.odm.AttributeMetadata;
import fr.mtlx.odm.ClassAssistant;
import fr.mtlx.odm.ClassMetadata;
import fr.mtlx.odm.MappingException;
import fr.mtlx.odm.OperationsImplementation;
import fr.mtlx.odm.cache.TypeSafeCache;
import fr.mtlx.odm.converters.Converter;
import fr.mtlx.odm.utils.TypeCheckConverter;

public class SpringOperationsImpl<T> implements OperationsImplementation<T> {

    public static final String[] RETURN_NO_ATTRIBUTES = new String[] {};

    private static final Logger log = LoggerFactory.getLogger(SpringOperationsImpl.class);
    
    private static final DirContextProcessor nullDirContextProcessor = new DirContextProcessor() {
        @Override
        public void postProcess(DirContext ctx) throws NamingException {
            // Do nothing
        }

        @Override
        public void preProcess(DirContext ctx) throws NamingException {
            // Do nothing
        }
    };
    
    private final SpringSessionImpl session;
    
    private final ClassMetadata<T> metadata;

    private final transient MappingContextMapper contextMapper;
    
    private final transient Class<T> persistentClass;

    private final transient ClassAssistant<T> assistant;

    private final transient LdapOperations operations;

    private final transient TypeCheckConverter<T> typeChecker;

    private final transient TypeCheckConverter<ClassMetadata<? extends T>> metadataChecker;

    public SpringOperationsImpl(final SpringSessionImpl session, final ClassMetadata<T> metadata) {
        this.session = checkNotNull(session);
       
        this.metadata = checkNotNull(metadata);
        
        this.assistant = new ClassAssistant<>(metadata);

        this.contextMapper = new MappingContextMapper();

        this.operations = new LdapTemplate(session.getSessionFactory().getContextSource());
        
        this.persistentClass = metadata.getPersistentClass();
        
        this.typeChecker = new TypeCheckConverter<>(persistentClass);

        this.metadataChecker = new TypeCheckConverter<>(metadata.getClass());
    }

    @Override
    public Name doBind(T transientObject) throws NameNotFoundException {
        Name dn;

        dn = assistant.getIdentifier(transientObject);

        DirContextOperations context = new DirContextAdapter(dn);

        mapToContext(transientObject, context);

        operations.bind(context);

        session.getContextCache().store(dn, context);
        
        return dn;
    }

    @Override
    public void doUnbind(final Name dn) {
        operations.unbind(dn);

        session.getContextCache().remove(dn);
    }

    @Override
    public T doLookup(Name dn) {
        final DirContextOperations context = session.getContextCache().retrieve(dn).orElse(doContextLookup(dn));

        // XXX : il faut stocker le context dans le cache avant de faire le
        // mapping !
        session.getContextCache().store(dn, context);

        return contextMapper.doMapFromContext(context);
    }
    
    @Override
    public Iterable<T> doSearch(Name base, SearchControls controls, String encodeFilter) throws javax.naming.SizeLimitExceededException {
        return doSearch(base, controls, encodeFilter, Optional.of(nullDirContextProcessor));
    }

    @Override
    public long count(final Name base, final SearchControls controls, final String filter) {
        controls.setReturningAttributes(RETURN_NO_ATTRIBUTES);

        CountContextMapper cm = new CountContextMapper();

        operations.search(base, filter, controls, cm, nullDirContextProcessor);

        return cm.getCount();
    }
    
    @Override
    public Iterable<List<T>> pages(final int pageSize, String filter, Name base, final SearchControls controls) {

        class PagedResultIterator implements Iterator<List<T>> {

            private PagedResultsCookie cookie = null;

            @Override
            public boolean hasNext() {
                return cookie == null || cookie.getCookie() != null;
            }

            @Override
            public List<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                PagedResultsDirContextProcessor processor = new PagedResultsDirContextProcessor(pageSize, cookie);

                try {
                    List<T> results = doSearch(base, controls, filter, Optional.of(processor));

                    cookie = processor.getCookie();

                    return results;
                } catch (javax.naming.SizeLimitExceededException ex) {
                    throw new NoSuchElementException(ex.getExplanation());
                }
            }
        }

        return () -> new PagedResultIterator();
    }
    
    private List<T> doSearch(final Name base, final SearchControls controls, final String filter,
            final Optional<DirContextProcessor> processor) throws javax.naming.SizeLimitExceededException {

        final TypeSafeCache<DirContextOperations> contextCache = session.getContextCache();

        final ContextMapper<T> cm = new AbstractContextMapper<T>() {
            @Override
            protected T doMapFromContext(final DirContextOperations ctx) {
                final Name dn = ctx.getDn();

                contextCache.store(dn, ctx);

                final Object object = session.getFromCacheStack(persistentClass, dn).orElseGet(() -> {

                    T entry = contextMapper.doMapFromContext(ctx);

                    return entry;
                });

                return typeChecker.convert(object);
            }
        };

        try {
            return operations.search(base, filter, controls, cm, processor.orElse(nullDirContextProcessor));
        } catch (SizeLimitExceededException ex) {
            throw new javax.naming.SizeLimitExceededException(ex.getExplanation());
        }
    }

    private void mergeObjectClasses(final DirContextOperations context) {
        final Set<String> ctxObjectClasses = Sets.newHashSet(Optional.ofNullable(context.getStringAttributes("objectClass"))
                .orElse(RETURN_NO_ATTRIBUTES));

        final Set<String> mdObjectClasses = Sets.newHashSet(metadata.getObjectClassHierarchy());

        for (String objectClass : Sets.difference(mdObjectClasses, ctxObjectClasses)) {
            context.addAttributeValue("objectClass", objectClass);
        }
    }

    private void mapToContext(final T transientObject, final DirContextOperations context) {

        mergeObjectClasses(context);

        for (String propertyName : metadata.getProperties()) {
            final AttributeMetadata ameta = metadata.getAttributeMetadata(propertyName);

            assert ameta != null;

            final Converter converter;
            try {
                converter = session.getSyntaxConverter(ameta.getSyntax());
            } catch (MappingException e) {
                assert false;
                continue;
            }

            final String attributeId = ameta.getAttirbuteName();

            if (ameta.isMultivalued()) {
                final Collection<?> values = (Collection<?>) assistant.getValue(transientObject, propertyName);

                if (values != null) {
                    for (Object value : values) {
                        if (value != null) {
                            context.addAttributeValue(attributeId, converter.toDirectory(value));
                        }
                    }
                } else {
                    // remove the attribute from the entry
                    context.setAttributeValues(attributeId, null);
                }
            } else {

                final Object currentValue = converter.toDirectory(assistant.getValue(transientObject, propertyName));

                final Object persistedValue = converter.fromDirectory(context.getObjectAttribute(attributeId));

                mergeValue(context, attributeId, currentValue, persistedValue);
            }
        }
    }

    private void mergeValue(DirContextOperations context, String attributeId, Object currentValue, Object persistedValue) {
        if (persistedValue != null && currentValue != null) {
            if (!persistedValue.equals(currentValue)) {
                context.removeAttributeValue(attributeId, persistedValue);

                context.addAttributeValue(attributeId, currentValue);
            }
        } else if (persistedValue != null) {
            context.removeAttributeValue(attributeId, persistedValue);
        } else if (currentValue != null) {
            context.removeAttributeValue(attributeId, persistedValue); // null
            context.addAttributeValue(attributeId, currentValue);
        }
    }

    private DirContextOperations doContextLookup(final Name dn) {
        final DirContextOperations ctx = operations.lookupContext(checkNotNull(dn, "dn is null"));

        final Name _dn = ctx.getDn();

        assert dn.equals(_dn) : "lookup DN does not match context DN";

        if (log.isDebugEnabled()) {
            log.debug("lookup context {} done", _dn);
        }

        return ctx;
    }

    private static class CountContextMapper extends AbstractContextMapper<Long> {

        private long cp;

        CountContextMapper() {
            cp = 0;
        }

        @Override
        public Long doMapFromContext(DirContextOperations context) {
            cp++;
            return cp;
        }

        public long getCount() {
            return cp;
        }
    }

    private class MappingContextMapper extends AbstractContextMapper<T> {

        @Override
        protected T doMapFromContext(final DirContextOperations ctx) {
            final T entry;
            final Name dn = ctx.getDn();

            try {
                entry = typeChecker.convert(dirContextObjectFactory(checkNotNull(ctx)));

                metadata.getIdentifier().set(entry, dn);
            } catch (InstantiationException | InvalidNameException | IllegalAccessException | InvocationTargetException
                    | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            return entry;
        }
    }

    private T dirContextObjectFactory(final DirContextOperations context) throws ClassNotFoundException,
            InstantiationException, InvalidNameException, IllegalAccessException, InvocationTargetException {
        final String[] objectClasses = context.getStringAttributes("objectClass");

        assert objectClasses != null && objectClasses.length > 0;

        SpringSessionFactoryImpl sessionFactory = session.getSessionFactory();

        Class<? extends T> realPersistentClass;

        try {
            realPersistentClass = metadataChecker.convert(sessionFactory.getClassMetadata(objectClasses)).getPersistentClass();
        } catch (ClassNotFoundException e) {
            realPersistentClass = metadata.getPersistentClass();
        }

        return sessionFactory.getProxyFactory(realPersistentClass, new Class<?>[0]).getProxy(session, context);
    }
}

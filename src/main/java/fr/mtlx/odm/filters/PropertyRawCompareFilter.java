package fr.mtlx.odm.filters;

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
import fr.mtlx.odm.AttributeMetadata;
import fr.mtlx.odm.ClassMetadata;

class PropertyRawCompareFilter<T> extends FilterImpl
{
	private final String property;

	private final Object value;

	private final Comparison op;

	private final Class<T> persistentClass;

	PropertyRawCompareFilter( final Class<T> persistentClass, final Comparison op, final String property, final Object value )
	{
		this.property = checkNotNull( property );
		this.value = value;

		this.op = op;
		
		this.persistentClass = checkNotNull( persistentClass );
	}

	@Override
	public String encode()
	{
		ClassMetadata<?> metadata = getSessionFactory().getClassMetadata( persistentClass );
		
		if ( metadata == null )
			throw new UnsupportedOperationException( String.format( "%s is not a persistent class", persistentClass ) );

		AttributeMetadata attribute = metadata.getAttributeMetadataByPropertyName( property );

		if ( attribute == null )
			throw new UnsupportedOperationException( String.format( "property %s not found in %s", property, checkNotNull( metadata ).getEntryClass() ) );

		Filter filter = new RawCompareFilter( op, attribute.getAttirbuteName(), value );

		return filter.encode();
	}
}

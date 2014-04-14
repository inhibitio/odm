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

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import fr.mtlx.odm.Session;

public class OrFilter implements Filter
{
	private final List<Filter> filterlist;
	
	
	public OrFilter( )
	{
		this.filterlist = Lists.newArrayList();
	}
	
	public OrFilter( final Filter[] filters )
	{
		this.filterlist = Arrays.asList( checkNotNull( filters ) );
	}

	public OrFilter add( Filter filter )
	{
		filterlist.add( checkNotNull( filter ) );
		
		return this;
	}
	
	@Override
	public String encode( @Nullable final Class<?> persistentClass, @Nullable final Session session )
	{
		final StringBuilder sb = new StringBuilder( "(" ).append( '|' );

		for ( Filter filter : filterlist )
		{
			sb.append( filter.encode( persistentClass, session ) );
		}

		sb.append( ")" );

		String retval = sb.toString();

		assert retval != null && retval.startsWith( "(" ) && retval.endsWith( ")" );

		return retval;
	}
}

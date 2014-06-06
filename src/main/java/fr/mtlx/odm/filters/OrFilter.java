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

import fr.mtlx.odm.SessionFactoryImpl;

public class OrFilter extends CompositeFilter {
	OrFilter(SessionFactoryImpl sessionFactory, Filter... filters) {
		super(filters);
	}

	@Override
	public OrFilter add(final Filter filter) {
		if (filter instanceof OrFilter) {
			filters.addAll(((OrFilter) filter).filters);
		} else {
			super.add(filter);
		}

		return this;
	}

	@Override
	public String encode() {
		final StringBuilder sb = new StringBuilder("(").append('|');

		for (Filter filter : filters) {
			sb.append(filter.encode());
		}

		sb.append(")");

		String retval = sb.toString();

		assert retval != null && retval.startsWith("(") && retval.endsWith(")");

		return retval;
	}
}

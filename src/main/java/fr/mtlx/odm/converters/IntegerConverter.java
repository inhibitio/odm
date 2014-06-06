package fr.mtlx.odm.converters;

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

public class IntegerConverter extends SyntaxConverter<String, Integer> {
	public IntegerConverter() {
		super(String.class, Integer.class);
	}

	@Override
	public String to(final Integer object) throws ConvertionException {
		return object.toString();
	}

	@Override
	public Integer from(final String value) throws ConvertionException {
		try {
			return new Integer(value);
		} catch (NumberFormatException e) {
			throw new ConvertionException(e);
		}
	}
}

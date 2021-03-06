/*
 * MyGlob Android Application
 * 
 * Copyright (C) 2013 Petar Petrov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package net.vexelon.myglob.fragments;

import net.vexelon.myglob.users.User;

/**
 * 
 * Events valid across all fragments
 * <p>
 * This actually is not a very good idea! It is better to implement separate interface for each fragment.
 *
 */
public interface IFragmentEvents {

	void onFEvent_UserChanged();
	
	void onFEvent_InvoiceUpdated(User forUser);
}

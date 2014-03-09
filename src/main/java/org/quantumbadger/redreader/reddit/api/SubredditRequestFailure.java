/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.reddit.api;

import org.apache.http.StatusLine;
import org.quantumbadger.redreader.cache.RequestFailureType;

public class SubredditRequestFailure {
	public final RequestFailureType requestFailureType;
	public final Throwable t;
	public final StatusLine statusLine;
	public final String readableMessage;

	public SubredditRequestFailure(RequestFailureType requestFailureType, Throwable t,
								   StatusLine statusLine, String readableMessage) {
		this.requestFailureType = requestFailureType;
		this.t = t;
		this.statusLine = statusLine;
		this.readableMessage = readableMessage;
	}

	public SubredditRequestFailure(Throwable t) {
		this(null, t, null, t.getClass().getName() + ": " + t.getMessage());
	}

	public SubredditRequestFailure(RequestFailureType requestFailureType, Throwable t) {
		this(requestFailureType, t, null, t.getClass().getName() + ": " + t.getMessage());
	}
}

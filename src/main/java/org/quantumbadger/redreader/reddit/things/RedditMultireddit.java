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

package org.quantumbadger.redreader.reddit.things;

import org.quantumbadger.redreader.io.WritableHashSet;
import org.quantumbadger.redreader.io.WritableObject;
import org.quantumbadger.redreader.jsonwrap.JsonBufferedObject;

public class RedditMultireddit implements WritableObject<RedditMultireddit.MultiredditId> {

	public MultiredditId getKey() {
		return new MultiredditId(multiredditUser, multiredditName);
	}

	public long getTimestamp() {
		return downloadTime;
	}

	public int dbVersion() {
		return 1;
	}

	public long downloadTime, created, created_utc;
	public String name, visibility, path;
	public boolean can_edit;

	public String multiredditUser, multiredditName;

	public WritableHashSet<String> subreddits;

	public static class MultiredditId {
		public final String user, multiredditName;

		public MultiredditId(String user, String multiredditName) {
			this.user = user;
			this.multiredditName = multiredditName;
		}

		@Override
		public String toString() {
			return user.toLowerCase() + "::" + multiredditName.toLowerCase();
		}
	}

	public RedditMultireddit(JsonBufferedObject object) {
		// TODO
	}
}

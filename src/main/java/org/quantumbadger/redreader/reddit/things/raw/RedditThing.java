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

package org.quantumbadger.redreader.reddit.things.raw;

import org.quantumbadger.redreader.jsonwrap.JsonBufferedObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;


public final class RedditThing {

	public static enum Kind {
		POST, USER, COMMENT, MESSAGE, SUBREDDIT, MORE_COMMENTS, LISTING, MULTIREDDIT
	}
	
	private static final Hashtable<String, Kind> kinds;
	
	static {
		kinds = new Hashtable<String, Kind>();
		kinds.put("t1", Kind.COMMENT);
		kinds.put("t2", Kind.USER);
		kinds.put("t3", Kind.POST);
		kinds.put("t4", Kind.MESSAGE);
		kinds.put("t5", Kind.SUBREDDIT);
		kinds.put("more", Kind.MORE_COMMENTS);
		kinds.put("Listing", Kind.LISTING);
		kinds.put("LabeledMulti", Kind.MULTIREDDIT);
	}
	
	public String kind;
	public JsonBufferedObject data;
	
	public Kind getKind() {
		return kinds.get(kind);
	}

	public RawRedditComment asComment() throws InstantiationException, IllegalAccessException, InterruptedException, IOException, NoSuchMethodException, InvocationTargetException {
		return data.asObject(RawRedditComment.class);
	}
	
	public RawRedditPost asPost() throws InstantiationException, IllegalAccessException, InterruptedException, IOException, NoSuchMethodException, InvocationTargetException {
		return data.asObject(RawRedditPost.class);
	}

	public RawRedditSubreddit asSubreddit() throws InstantiationException, IllegalAccessException, InterruptedException, IOException, NoSuchMethodException, InvocationTargetException {
		return data.asObject(RawRedditSubreddit.class);
	}

	public RawRedditUser asUser() throws InstantiationException, IllegalAccessException, InterruptedException, IOException, NoSuchMethodException, InvocationTargetException {
		return data.asObject(RawRedditUser.class);
	}

	public RawRedditMessage asMessage() throws IllegalAccessException, InterruptedException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException {
		return data.asObject(RawRedditMessage.class);
	}
}

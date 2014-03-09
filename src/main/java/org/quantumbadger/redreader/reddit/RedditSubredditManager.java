package org.quantumbadger.redreader.reddit;

import android.content.Context;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.TimestampBound;
import org.quantumbadger.redreader.io.*;
import org.quantumbadger.redreader.reddit.api.RedditAPIIndividualSubredditDataRequester;
import org.quantumbadger.redreader.reddit.api.SubredditRequestFailure;
import org.quantumbadger.redreader.reddit.things.raw.RawRedditMultireddit;
import org.quantumbadger.redreader.reddit.things.raw.RawRedditSubreddit;

import java.util.Collection;
import java.util.HashMap;

public class RedditSubredditManager {

	private final RedditAccount user;

	public void offerRawSubredditData(Collection<RawRedditSubreddit> toWrite, long timestamp) {
		subredditCache.performWrite(toWrite);
	}

	// TODO need way to cancel web update and start again?
	// TODO anonymous user

	// TODO Ability to temporarily flag subreddits as subscribed/unsubscribed
	// TODO Ability to temporarily add/remove subreddits from multireddits

	// TODO store favourites in preference

	public static enum SubredditListType { SUBSCRIBED, MODERATED, MULTIREDDITS, MOST_POPULAR }

	private static RedditSubredditManager singleton;
	private static RedditAccount singletonUser;

	private final PermanentCache<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure> multiredditCache;
	private final WeakCache<String, RawRedditSubreddit, SubredditRequestFailure> subredditCache;
	private final Context context;

	public static synchronized RedditSubredditManager getInstance(Context context, RedditAccount user) {

		if(singleton == null || !user.equals(singletonUser)) {
			singletonUser = user;
			singleton = new RedditSubredditManager(context, user);
		}

		return singleton;
	}

	private RedditSubredditManager(Context context, RedditAccount user) {

		this.user = user;
		this.context = context;

		// Multireddit cache

		final RawObjectDB<RawRedditMultireddit.MultiredditId, RawRedditMultireddit> multiredditDb
				= new RawObjectDB<RawRedditMultireddit.MultiredditId, RawRedditMultireddit>(context,
				getDbFilename("multireddits", user), RawRedditMultireddit.class);

		final ThreadedRawObjectDB<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure> multiredditDbWrapper
				= new ThreadedRawObjectDB<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure>(multiredditDb, new MultiredditDataRequester());

		multiredditCache = new PermanentCache<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure>(multiredditDbWrapper);

		// Subreddit cache

		final RawObjectDB<String, RawRedditSubreddit> subredditDb
				= new RawObjectDB<String, RawRedditSubreddit>(context, getDbFilename("subreddits", user), RawRedditSubreddit.class);

		final ThreadedRawObjectDB<String, RawRedditSubreddit, SubredditRequestFailure> subredditDbWrapper
				= new ThreadedRawObjectDB<String, RawRedditSubreddit, SubredditRequestFailure>(subredditDb, new RedditAPIIndividualSubredditDataRequester(context, user));

		subredditCache = new WeakCache<String, RawRedditSubreddit, SubredditRequestFailure>(subredditDbWrapper);
	}

	private static String getDbFilename(String type, RedditAccount user) {
		return General.sha1(user.username.getBytes()) + "_" + type + "_subreddits.db";
	}

	public void getSubreddit(String id,
							 TimestampBound timestampBound,
							 RequestResponseHandler<RawRedditSubreddit, SubredditRequestFailure> handler,
							 UpdatedVersionListener<String, RawRedditSubreddit> updatedVersionListener) {

		subredditCache.performRequest(id, timestampBound, handler, updatedVersionListener);
	}

	public void getMultireddit(RawRedditMultireddit.MultiredditId id,
							   TimestampBound timestampBound,
							   RequestResponseHandler<RawRedditMultireddit, SubredditRequestFailure> handler,
							   UpdatedVersionListener<RawRedditMultireddit.MultiredditId, RawRedditMultireddit> updatedVersionListener) {

		multiredditCache.performRequest(id, timestampBound, handler, updatedVersionListener);
	}

	private final class MultiredditDataRequester implements CacheDataSource<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure> {

		public void performRequest(RawRedditMultireddit.MultiredditId key, TimestampBound timestampBound, RequestResponseHandler<RawRedditMultireddit, SubredditRequestFailure> handler) {
		}

		public void performRequest(Collection<RawRedditMultireddit.MultiredditId> keys, TimestampBound timestampBound, RequestResponseHandler<HashMap<RawRedditMultireddit.MultiredditId, RawRedditMultireddit>, SubredditRequestFailure> handler) {
		}

		public void performWrite(RawRedditMultireddit value) {
			throw new UnsupportedOperationException();
		}

		public void performWrite(Collection<RawRedditMultireddit> values) {
			throw new UnsupportedOperationException();
		}
	}
}

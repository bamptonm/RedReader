package org.quantumbadger.redreader.reddit;

import android.content.Context;
import android.net.Uri;
import org.apache.http.StatusLine;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.cache.CacheManager;
import org.quantumbadger.redreader.cache.CacheRequest;
import org.quantumbadger.redreader.cache.RequestFailureType;
import org.quantumbadger.redreader.common.Constants;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.TimestampBound;
import org.quantumbadger.redreader.common.UnexpectedInternalStateException;
import org.quantumbadger.redreader.io.*;
import org.quantumbadger.redreader.jsonwrap.JsonBuffered;
import org.quantumbadger.redreader.jsonwrap.JsonBufferedArray;
import org.quantumbadger.redreader.jsonwrap.JsonBufferedObject;
import org.quantumbadger.redreader.jsonwrap.JsonValue;
import org.quantumbadger.redreader.reddit.things.RawRedditMultireddit;
import org.quantumbadger.redreader.reddit.things.RawRedditSubreddit;
import org.quantumbadger.redreader.reddit.things.RedditThing;

import java.net.URI;
import java.util.*;

public class RedditSubredditManager {

	private final RedditAccount user;

	// TODO need way to cancel web update and start again?
	// TODO anonymous user

	// TODO Ability to temporarily flag subreddits as subscribed/unsubscribed
	// TODO Ability to temporarily add/remove subreddits from multireddits

	// TODO store favourites in preference

	public static enum SubredditListType { SUBSCRIBED, MODERATED, MULTIREDDITS, MOST_POPULAR }

	private static RedditAccount currentUser;
	private static RedditSubredditManager singleton;

	private final PermanentCache<RawRedditMultireddit.MultiredditId, RawRedditMultireddit, SubredditRequestFailure> multiredditCache;
	private final PermanentCache<SubredditListType, WritableHashSet<SubredditListType>, SubredditRequestFailure> subredditListCache;
	private final WeakCache<String, RawRedditSubreddit, SubredditRequestFailure> subredditCache;
	private final Context context;

	public static synchronized RedditSubredditManager getInstance(Context context, RedditAccount user) {

		if(singleton == null || !user.equals(currentUser)) {
			currentUser = user;
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
				= new ThreadedRawObjectDB<String, RawRedditSubreddit, SubredditRequestFailure>(subredditDb, new IndividualSubredditDataRequester());

		subredditCache = new WeakCache<String, RawRedditSubreddit, SubredditRequestFailure>(subredditDbWrapper);

		// Subreddit list cache

		@SuppressWarnings("unchecked")
		final RawObjectDB<SubredditListType, WritableHashSet<SubredditListType>> subredditListDb
				= new RawObjectDB<SubredditListType, WritableHashSet<SubredditListType>>(context,
				getDbFilename("sublists", user), (Class)WritableHashSet.class);

		final ThreadedRawObjectDB<SubredditListType, WritableHashSet<SubredditListType>, SubredditRequestFailure>
				subredditListDbWrapper = new ThreadedRawObjectDB<SubredditListType, WritableHashSet<SubredditListType>,
				SubredditRequestFailure>(subredditListDb, new IndividualSubredditListRequester());

		subredditListCache = new PermanentCache<SubredditListType, WritableHashSet<SubredditListType>, SubredditRequestFailure>(subredditListDbWrapper);
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

	public void getSubredditList(SubredditListType listType,
							   TimestampBound timestampBound,
							   RequestResponseHandler<WritableHashSet<SubredditListType>, SubredditRequestFailure> handler,
							   UpdatedVersionListener<SubredditListType, WritableHashSet<SubredditListType>> updatedVersionListener) {

		subredditListCache.performRequest(listType, timestampBound, handler, updatedVersionListener);
	}

	public static class SubredditRequestFailure {
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

	private class IndividualSubredditDataRequester implements CacheDataSource<String, RawRedditSubreddit, SubredditRequestFailure> {

		public void performRequest(final String key,
								   final TimestampBound timestampBound,
								   final RequestResponseHandler<RawRedditSubreddit, SubredditRequestFailure> handler) {

			final CacheRequest aboutSubredditCacheRequest = new CacheRequest(
					Constants.Reddit.getUri("/r/" + key + "/about.json"),
					user,
					null,
					Constants.Priority.API_SUBREDDIT_INVIDIVUAL,
					0,
					CacheRequest.DownloadType.FORCE,
					Constants.FileType.SUBREDDIT_ABOUT,
					true,
					true,
					false,
					context
			) {

				@Override
				protected void onCallbackException(Throwable t) {
					handler.onRequestFailed(new SubredditRequestFailure(t));
				}

				@Override protected void onDownloadNecessary() {}
				@Override protected void onDownloadStarted() {}
				@Override protected void onProgress(long bytesRead, long totalBytes) {}

				@Override
				protected void onFailure(RequestFailureType type, Throwable t, StatusLine status, String readableMessage) {
					handler.onRequestFailed(new SubredditRequestFailure(type, t, status, readableMessage));
				}

				@Override
				protected void onSuccess(CacheManager.ReadableCacheFile cacheFile, long timestamp, UUID session,
										 boolean fromCache, String mimetype) {}

				@Override
				public void onJsonParseStarted(JsonValue result, long timestamp, UUID session, boolean fromCache) {

					try {
						final RedditThing subredditThing = result.asObject(RedditThing.class);
						final RawRedditSubreddit subreddit = subredditThing.asSubreddit();
						subreddit.downloadTime = timestamp;
						handler.onRequestSuccess(subreddit, timestamp);

					} catch(Exception e) {
						handler.onRequestFailed(new SubredditRequestFailure(RequestFailureType.PARSE, e));
					}
				}
			};

			CacheManager.getInstance(context).makeRequest(aboutSubredditCacheRequest);
		}

		public void performRequest(Collection<String> keys,
								   TimestampBound timestampBound,
								   RequestResponseHandler<HashMap<String, RawRedditSubreddit>, SubredditRequestFailure> handler) {
			// TODO batch API? or just make lots of requests and build up a hash map?
			throw new UnsupportedOperationException();
		}

		public void performWrite(RawRedditSubreddit value) {
			throw new UnsupportedOperationException();
		}

		public void performWrite(Collection<RawRedditSubreddit> values) {
			throw new UnsupportedOperationException();
		}
	}

	private class IndividualSubredditListRequester
			implements CacheDataSource<SubredditListType, WritableHashSet<SubredditListType>, SubredditRequestFailure> {

		public void performRequest(final SubredditListType type,
								   final TimestampBound timestampBound,
								   final RequestResponseHandler<WritableHashSet<SubredditListType>, SubredditRequestFailure> handler) {

			if(type == SubredditListType.MOST_POPULAR) {
				doSubredditListRequest(SubredditListType.MOST_POPULAR, handler, null);

			} else if(user.isAnonymous()) {
				switch(type) {

					case SUBSCRIBED:
						performRequest(SubredditListType.MOST_POPULAR, timestampBound, handler);
						return;

					case MODERATED: {
						final long curTime = System.currentTimeMillis();
						handler.onRequestSuccess(new WritableHashSet<SubredditListType>(
								new HashSet<String>(), curTime, SubredditListType.MODERATED), curTime);
						return;
					}

					case MULTIREDDITS: {
						final long curTime = System.currentTimeMillis();
						handler.onRequestSuccess(new WritableHashSet<SubredditListType>(
								new HashSet<String>(), curTime, SubredditListType.MULTIREDDITS), curTime);
						return;
					}

					default:
						throw new RuntimeException("Internal error: unknown subreddit list type '" + type.name() + "'");
				}

			} else {
				doSubredditListRequest(type, handler, null);
			}
		}

		private void doSubredditListRequest(final SubredditListType type,
											final RequestResponseHandler<WritableHashSet<SubredditListType>,
													SubredditRequestFailure> handler,
											final String after) {

			URI uri;

			switch(type) {
				case SUBSCRIBED:
					uri = Constants.Reddit.getUri(Constants.Reddit.PATH_SUBREDDITS_MINE_SUBSCRIBER);
					break;
				case MODERATED:
					uri = Constants.Reddit.getUri(Constants.Reddit.PATH_SUBREDDITS_MINE_MODERATOR);
					break;
				case MOST_POPULAR:
					uri = Constants.Reddit.getUri(Constants.Reddit.PATH_SUBREDDITS_POPULAR);
					break;
				default:
					throw new UnexpectedInternalStateException(type.name());
			}

			if(after != null) {
				// TODO move this logic to General?
				final Uri.Builder builder = Uri.parse(uri.toString()).buildUpon();
				builder.appendQueryParameter("after", after);
				uri = General.uriFromString(builder.toString());
			}

			final CacheRequest aboutSubredditCacheRequest = new CacheRequest(
					uri,
					user,
					null,
					Constants.Priority.API_SUBREDDIT_INVIDIVUAL,
					0,
					CacheRequest.DownloadType.FORCE,
					Constants.FileType.SUBREDDIT_LIST,
					true,
					true,
					false,
					context
			) {

				@Override
				protected void onCallbackException(Throwable t) {
					handler.onRequestFailed(new SubredditRequestFailure(t));
				}

				@Override protected void onDownloadNecessary() {}
				@Override protected void onDownloadStarted() {}
				@Override protected void onProgress(long bytesRead, long totalBytes) {}

				@Override
				protected void onFailure(RequestFailureType type, Throwable t, StatusLine status, String readableMessage) {
					handler.onRequestFailed(new SubredditRequestFailure(type, t, status, readableMessage));
				}

				@Override
				protected void onSuccess(CacheManager.ReadableCacheFile cacheFile, long timestamp, UUID session,
										 boolean fromCache, String mimetype) {}

				@Override
				public void onJsonParseStarted(JsonValue result, long timestamp, UUID session, boolean fromCache) {

					try {

						final HashSet<String> output = new HashSet<String>();
						final ArrayList<RawRedditSubreddit> toWrite = new ArrayList<RawRedditSubreddit>();

						final JsonBufferedObject redditListing = result.asObject().getObject("data");

						final JsonBufferedArray subreddits = redditListing.getArray("children");

						final JsonBuffered.Status joinStatus = subreddits.join();
						if(joinStatus == JsonBuffered.Status.FAILED) {
							handler.onRequestFailed(new SubredditRequestFailure(RequestFailureType.PARSE, null, null, "Unknown parse error"));
							return;
						}

						if(type == SubredditListType.SUBSCRIBED && subreddits.getCurrentItemCount() == 0) {
							doSubredditListRequest(SubredditListType.MOST_POPULAR, handler, null);
							return;
						}

						for(final JsonValue v : subreddits) {
							final RedditThing thing = v.asObject(RedditThing.class);
							final RawRedditSubreddit subreddit = thing.asSubreddit();

							toWrite.add(subreddit);
							output.add(subreddit.name.toLowerCase()); // TODO is name the correct param?
						}

						subredditCache.performWrite(toWrite);

						final String receivedAfter = redditListing.getString(after);
						if(receivedAfter != null) {

							doSubredditListRequest(type, new RequestResponseHandler<WritableHashSet<SubredditListType>, SubredditRequestFailure>() {
								public void onRequestFailed(SubredditRequestFailure failureReason) {
									handler.onRequestFailed(failureReason);
								}

								public void onRequestSuccess(WritableHashSet<SubredditListType> result, long timeCached) {
									output.addAll(result.toHashset());
									handler.onRequestSuccess(new WritableHashSet<SubredditListType>(output, timeCached, type), timeCached);
								}
							}, receivedAfter);

						} else {
							handler.onRequestSuccess(new WritableHashSet<SubredditListType>(output, timestamp, type), timestamp);
						}

					} catch(Exception e) {
						handler.onRequestFailed(new SubredditRequestFailure(RequestFailureType.PARSE, e));
					}
				}
			};

			CacheManager.getInstance(context).makeRequest(aboutSubredditCacheRequest);
		}

		public void performRequest(Collection<SubredditListType> keys, TimestampBound timestampBound,
								   RequestResponseHandler<HashMap<SubredditListType, WritableHashSet<SubredditListType>>,
										   SubredditRequestFailure> handler) {
			// TODO batch API? or just make lots of requests and build up a hash map?
			throw new UnsupportedOperationException();
		}

		public void performWrite(WritableHashSet<SubredditListType> value) {
			throw new UnsupportedOperationException();
		}

		public void performWrite(Collection<WritableHashSet<SubredditListType>> values) {
			throw new UnsupportedOperationException();
		}
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

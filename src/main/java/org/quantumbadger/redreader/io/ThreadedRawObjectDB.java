package org.quantumbadger.redreader.io;

import org.quantumbadger.redreader.common.TimestampBound;
import org.quantumbadger.redreader.common.TriggerableThread;
import org.quantumbadger.redreader.common.collections.UniqueSynchronizedQueue;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class ThreadedRawObjectDB<K, V extends WritableObject<K>, F>
		implements CacheDataSource<K, V, F> {

	private final TriggerableThread writeThread = new TriggerableThread(new Runnable() {
		public void run() {
			doWrite();
		}
	}, 1500);
	private final TriggerableThread readThread = new TriggerableThread(new Runnable() {
		public void run() {
			doRead();
		}
	}, 0);

	private final HashMap<K, V> toWrite = new HashMap<K, V>();
	private final UniqueSynchronizedQueue<ReadOperation> toRead = new UniqueSynchronizedQueue<ReadOperation>();
	private final Object ioLock = new Object();

	private final RawObjectDB<K, V> db;
	private final CacheDataSource<K, V, F> alternateSource;

	public ThreadedRawObjectDB(RawObjectDB<K, V> db, CacheDataSource<K, V, F> alternateSource) {
		this.db = db;
		this.alternateSource = alternateSource;
	}

	private void doWrite() {
		synchronized(ioLock) {

			final Collection<V> values;

			synchronized(toWrite) {
				values = toWrite.values();
				toWrite.clear();
			}

			db.putAll(values);
		}
	}

	private void doRead() {
		synchronized(ioLock) {
			ReadOperation op;
			while((op = toRead.dequeue()) != null) {
				op.run();
			}
		}
	}

	public void performRequest(K key, TimestampBound timestampBound,
							   RequestResponseHandler<V, F> handler) {

		toRead.enqueue(new SingleReadOperation(timestampBound, handler, key));
		readThread.trigger();
	}

	public void performRequest(Collection<K> keys, TimestampBound timestampBound,
							   RequestResponseHandler<HashMap<K, V>, F> handler) {

		toRead.enqueue(new BulkReadOperation(timestampBound, handler, keys));
		readThread.trigger();
	}

	public void performWrite(V value) {

		synchronized(toWrite) {
			toWrite.put(value.getKey(), value);
		}

		writeThread.trigger();
	}

	public void performWrite(Collection<V> values) {

		synchronized(toWrite) {
			for(V value : values) {
				toWrite.put(value.getKey(), value);
			}
		}

		writeThread.trigger();
	}

	private class BulkReadOperation extends ReadOperation {

		public final Collection<K> keys;
		public final RequestResponseHandler<HashMap<K, V>, F> responseHandler;

		private BulkReadOperation(TimestampBound timestampBound,
								  RequestResponseHandler<HashMap<K, V>, F> responseHandler, Collection<K> keys) {
			super(timestampBound);
			this.responseHandler = responseHandler;
			this.keys = keys;
		}

		public void run() {

			final HashSet<K> keysRemaining = new HashSet<K>(keys);
			final HashMap<K, V> existingResult = new HashMap<K, V>(keys.size());
			long oldestTimestamp = Long.MAX_VALUE;

			synchronized(toWrite) {
				for(final K key : keys) {
					final V writeCacheResult = toWrite.get(key);
					if(writeCacheResult != null && timestampBound.verifyTimestamp(writeCacheResult.getTimestamp())) {
						keysRemaining.remove(key);
						existingResult.put(key, writeCacheResult);
						oldestTimestamp = Math.min(oldestTimestamp, writeCacheResult.getTimestamp());
					}
				}
			}

			if(keysRemaining.size() == 0) {
				responseHandler.onRequestSuccess(existingResult, oldestTimestamp);
				return;
			}

			for(final K key : keys) {
				final V dbResult = db.getById(key);
				if(dbResult != null && timestampBound.verifyTimestamp(dbResult.getTimestamp())) {
					keysRemaining.remove(key);
					existingResult.put(key, dbResult);
					oldestTimestamp = Math.min(oldestTimestamp, dbResult.getTimestamp());
				}
			}

			if(keysRemaining.size() == 0) {
				responseHandler.onRequestSuccess(existingResult, oldestTimestamp);
				return;
			}

			final long outerOldestTimestamp = oldestTimestamp;

			alternateSource.performRequest(keysRemaining, timestampBound, new RequestResponseHandler<HashMap<K, V>, F>() {
				public void onRequestFailed(F failureReason) {
					responseHandler.onRequestFailed(failureReason);
				}

				public void onRequestSuccess(HashMap<K, V> result, long timeCached) {
					performWrite(result.values());
					existingResult.putAll(result);
					responseHandler.onRequestSuccess(existingResult, Math.min(timeCached, outerOldestTimestamp));
				}
			});
		}
	}

	private class SingleReadOperation extends ReadOperation {

		public final K key;
		public final RequestResponseHandler<V, F> responseHandler;

		private SingleReadOperation(TimestampBound timestampBound, RequestResponseHandler<V, F> responseHandler, K key) {
			super(timestampBound);
			this.responseHandler = responseHandler;
			this.key = key;
		}

		public void run() {

			synchronized(toWrite) {
				final V writeCacheResult = toWrite.get(key);
				if(writeCacheResult != null && timestampBound.verifyTimestamp(writeCacheResult.getTimestamp())) {
					responseHandler.onRequestSuccess(writeCacheResult, writeCacheResult.getTimestamp());
					return;
				}
			}

			final V dbResult = db.getById(key);
			if(dbResult != null && timestampBound.verifyTimestamp(dbResult.getTimestamp())) {
				responseHandler.onRequestSuccess(dbResult, dbResult.getTimestamp());
				return;
			}

			alternateSource.performRequest(key, timestampBound, new RequestResponseHandler<V, F>() {
				public void onRequestFailed(F failureReason) {
					responseHandler.onRequestFailed(failureReason);
				}

				public void onRequestSuccess(V result, long timeCached) {
					performWrite(result);
				}
			});
		}
	}

	private abstract class ReadOperation {

		public final TimestampBound timestampBound;

		private ReadOperation(TimestampBound timestampBound) {
			this.timestampBound = timestampBound;
		}

		public abstract void run();
	}
}

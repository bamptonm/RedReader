package org.quantumbadger.redreader.io;

import java.util.Collection;
import java.util.HashSet;

public class WritableHashSet<K> implements WritableObject<K> {

	private transient HashSet<String> hashSet = null;
	private String serialised;

	private long timestamp;
	private K key;

	public WritableHashSet(HashSet<String> data, long timestamp, K key) {
		this.hashSet = data;
		this.timestamp = timestamp;
		this.key = key;
		serialised = listToEscapedString(hashSet);
	}

	public WritableHashSet(String data) {
		serialised = data;
		key = null;
		timestamp = -1;
	}

	@Override
	public String toString() {
		return serialised;
	}

	public synchronized HashSet<String> toHashset() {
		if(hashSet != null) return hashSet;
		return (hashSet = escapedStringToSet(serialised));
	}

	public K getKey() {
		return key;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public int dbVersion() {
		return 1;
	}

	public static String listToEscapedString(final Collection<String> list) {

		if(list.size() == 0) return "";

		final StringBuilder sb = new StringBuilder();

		for(final String str : list) {
			for(int i = 0; i < str.length(); i++) {

				final char c = str.charAt(i);

				switch(c) {
					case '\\':
						sb.append("\\\\");
						break;
					case ';':
						sb.append("\\;");
						break;
					default:
						sb.append(c);
						break;
				}
			}

			sb.append(';');
		}

		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public static HashSet<String> escapedStringToSet(final String str) {

		final HashSet<String> result = new HashSet<String>();

		boolean isEscaped = false;
		final StringBuilder sb = new StringBuilder();

		for(int i = 0; i < str.length(); i++) {

			final char c = str.charAt(i);

			if(c == ';' && !isEscaped) {
				result.add(sb.toString());
				sb.setLength(0);

			} else if(c == '\\') {
				if(isEscaped) sb.append('\\');

			} else {
				sb.append(c);
			}

			isEscaped = c == '\\' && !isEscaped;
		}

		return result;
	}
}

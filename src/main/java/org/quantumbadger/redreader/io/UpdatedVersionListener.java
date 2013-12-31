package org.quantumbadger.redreader.io;

public interface UpdatedVersionListener<K, V extends WritableObject<K>> {
	public void onUpdatedVersion(V data);
}

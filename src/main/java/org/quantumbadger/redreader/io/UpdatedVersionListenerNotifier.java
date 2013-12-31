package org.quantumbadger.redreader.io;

import org.quantumbadger.redreader.common.collections.WeakReferenceListManager;

public class UpdatedVersionListenerNotifier<K, V extends WritableObject<K>>
		implements WeakReferenceListManager.ArgOperator<UpdatedVersionListener<K, V>, V> {

	public void operate(UpdatedVersionListener<K, V> listener, V data) {
		listener.onUpdatedVersion(data);
	}
}

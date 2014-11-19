package org.smartcolors.marshal;

import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public abstract class HashableSerializable implements Serializable {
	private HashCode cachedHash;

	public abstract void serialize(Serializer ser) throws SerializationException;

	public HashCode getHash() {
		if (cachedHash != null)
			return cachedHash;
		HashSerializer serializer = new HashSerializer();
		try {
			serialize(serializer);
		} catch (SerializationException e) {
			Throwables.propagate(e);
		}
		cachedHash = HashSerializer.calcHash(serializer, getHmacKey());
		return cachedHash;
	}
}

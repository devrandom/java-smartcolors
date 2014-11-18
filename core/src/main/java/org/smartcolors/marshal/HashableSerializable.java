package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public abstract class HashableSerializable implements Serializable {
	private byte[] cachedHash;

	public abstract void serialize(Serializer ser);

	public byte[] getHash() {
		if (cachedHash != null)
			return cachedHash;
		HashSerializer serializer = new HashSerializer();
		serialize(serializer);
		cachedHash = HashSerializer.calcHash(serializer, getHmacKey());
		return cachedHash;
	}
}

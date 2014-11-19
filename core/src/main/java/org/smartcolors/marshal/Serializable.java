package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public interface Serializable {
	public void serialize(Serializer serializer) throws SerializationException;

	byte[] getHash();

	byte[] getHmacKey();
}

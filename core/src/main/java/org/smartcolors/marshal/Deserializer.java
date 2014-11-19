package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public interface Deserializer {
	long readVaruint() throws SerializationException;
	byte[] readBytes() throws SerializationException;

	byte[] readBytes(int expectedLength) throws SerializationException;
}

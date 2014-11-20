package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public interface Deserializer {
	long readVarulong() throws SerializationException;
	int readVaruint() throws SerializationException;
	byte[] readBytes() throws SerializationException;

	byte[] readBytes(int expectedLength) throws SerializationException;

	public static interface ObjectReader<T> {
		T readObject(Deserializer des) throws SerializationException;
	}

	public <T> T readObject(ObjectReader<T> reader) throws SerializationException;
}

package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public interface Deserializer {
	long readVaruint();
	byte[] readBytes();

	byte[] readBytes(int expectedLength);
}

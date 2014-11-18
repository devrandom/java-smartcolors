package org.smartcolors.marshal;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public interface Serializer {
	void write(long value);

	void write(byte[] bytes);
	void writeWithLength(byte[] bytes);

	void write(Serializable obj);
}

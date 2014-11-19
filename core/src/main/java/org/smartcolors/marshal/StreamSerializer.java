package org.smartcolors.marshal;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class StreamSerializer implements Serializer {
	protected final OutputStream os;

	public StreamSerializer(OutputStream os) {
		this.os = os;
	}

	@Override
	public void write(long value) throws SerializationException {
		try {
			if (value == 0)
				os.write(0);
			while (value != 0) {
				int b = (int) (value & 0x7f);
				value = (value >> 7) & Long.MAX_VALUE;
				if (value != 0)
					b |= 0x80;
				os.write(b);
			}
		} catch (IOException e) {
			throw new SerializationException(e);
		}
	}

	@Override
	public void write(byte[] bytes) throws SerializationException {
		try {
			os.write(bytes);
		} catch (IOException e) {
			throw new SerializationException(e);
		}
	}

	@Override
	public void writeWithLength(byte[] bytes) throws SerializationException {
		write(bytes.length);
		try {
			os.write(bytes);
		} catch (IOException e) {
			throw new SerializationException(e);
		}
	}

	@Override
	public void write(Serializable obj) throws SerializationException {
		obj.serialize(this);
	}
}

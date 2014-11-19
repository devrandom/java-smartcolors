package org.smartcolors.marshal;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class StreamDeserializer implements Deserializer {
	public static final int MAX_BYTES = 1024 * 1024;
	protected final InputStream is;

	public StreamDeserializer(InputStream is) {
		this.is = is;
	}

	@Override
	public long readVaruint() throws SerializationException {
		long value = 0;
		int shift = 0;
		while (true) {
			long b = 0;
			try {
				b = is.read();
			} catch (IOException e) {
				throw new SerializationException(e);
			}
			value |= (b & 0x7f) << shift;
			if ((b & 0x80) == 0)
				break;
			shift += 7;
		}
		return value;
	}

	@Override
	public byte[] readBytes(int expectedLength) throws SerializationException {
		byte[] buf = new byte[expectedLength];
		try {
			is.read(buf);
		} catch (IOException e) {
			throw new SerializationException(e);
		}
		return buf;
	}

	@Override
	public byte[] readBytes() throws SerializationException {
		long length = readVaruint();
		if (length > MAX_BYTES || length < 0)
			throw new RuntimeException("bytes longer than max");
		byte[] buf = new byte[(int) length];
		try {
			is.read(buf);
		} catch (IOException e) {
			throw new SerializationException(e);
		}
		return buf;
	}

	public <T> T readObject(ObjectReader<T> reader) throws SerializationException {
		return reader.readObject(this);
	}
}
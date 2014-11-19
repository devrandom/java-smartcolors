package org.smartcolors.marshal;

import com.google.common.base.Throwables;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class BytesDeserializer implements Deserializer {
	public static final int MAX_BYTES = 1024 * 1024;
	private final ByteArrayInputStream is;

	public BytesDeserializer(byte[] bytes) {
		is = new ByteArrayInputStream(bytes);
	}

	@Override
	public long readVaruint() {
		long value = 0;
		int shift = 0;
		while (true) {
			long b = is.read();
			value |= (b & 0x7f) << shift;
			if ((b & 0x80) == 0)
				break;
			shift += 7;
		}
		return value;
	}

	@Override
	public byte[] readBytes(int expectedLength) {
		byte[] buf = new byte[expectedLength];
		try {
			is.read(buf);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		return buf;
	}

	@Override
	public byte[] readBytes() {
		long length = readVaruint();
		if (length > MAX_BYTES || length < 0)
			throw new RuntimeException("bytes longer than max");
		byte[] buf = new byte[(int)length];
		try {
			is.read(buf);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		return buf;
	}
}

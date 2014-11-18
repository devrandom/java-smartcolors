package org.smartcolors.marshal;

import com.google.common.base.Throwables;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class BytesSerializer implements Serializer {
	private ByteArrayOutputStream os = new ByteArrayOutputStream();

	@Override
	public void write(long value) {
		if (value == 0)
			os.write(0);
		while (value != 0) {
			int b = (int)(value & 0x7f);
			value = (value >> 7) & Long.MAX_VALUE;
			if (value != 0)
				b |= 0x80;
			os.write(b);
		}
	}

	@Override
	public void write(byte[] bytes) {
		try {
			os.write(bytes);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	@Override
	public void writeWithLength(byte[] bytes) {
		write(bytes.length);
		try {
			os.write(bytes);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	@Override
	public void write(Serializable obj) {
		obj.serialize(this);
	}

	public byte[] getBytes() {
		return os.toByteArray();
	}
}

package org.smartcolors.core;

import org.bitcoinj.core.Utils;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.HashableSerializable;
import org.smartcolors.marshal.Serializer;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public abstract class ColorProof extends HashableSerializable {
	public static final int VERSION = 1;
	protected ColorDefinition def;
	protected long quantity;

	@Override
	public void serialize(Serializer ser) {
		ser.write(getType());
		ser.write(VERSION);
		ser.write(def);
		if (ser instanceof HashSerializer)
			ser.write(quantity);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("b96dae8e52cb124d01804353736a8384");
	}

	abstract int getType();
}

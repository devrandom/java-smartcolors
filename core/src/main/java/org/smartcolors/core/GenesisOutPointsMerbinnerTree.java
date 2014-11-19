package org.smartcolors.core;

import com.google.common.collect.Maps;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.Serializer;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class GenesisOutPointsMerbinnerTree extends MerbinnerTree<TransactionOutPoint, Long> {
	private NetworkParameters params;

	@Override
	public void serializeKey(Serializer ser, TransactionOutPoint key) {
		if (ser instanceof HashSerializer)
			ser.write(getKeyHash(key));
		else
			ser.write(key.bitcoinSerialize());
	}

	@Override
	public void serializeValue(Serializer ser, Long value) {
		ser.write(value);
	}

	@Override
	public long getSum(Long value) {
		return value;
	}

	@Override
	public byte[] getKeyHash(TransactionOutPoint key) {
		return HashSerializer.calcHash(key.bitcoinSerialize(), Utils.HEX.decode("eac9aef052700336a94accea6a883e59"));
	}

	@Override
	protected void deserializeNode(Deserializer des) {
		TransactionOutPoint key = new TransactionOutPoint(params, des.readBytes(36), 0);
		long value = des.readVaruint();
		entries.put(key, value);
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) {
		ser.write(sum);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("d8497e1258c3f8e747341cb361676cee");
	}

	public GenesisOutPointsMerbinnerTree(NetworkParameters params, Map<TransactionOutPoint, Long> nodes) {
		super(nodes);
		this.params = params;
	}

	public GenesisOutPointsMerbinnerTree(NetworkParameters params) {
		super(Maps.<TransactionOutPoint, Long>newHashMap());
		this.params = params;
	}
}

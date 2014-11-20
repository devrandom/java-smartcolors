package org.smartcolors.core;

import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;
import org.smartcolors.marshal.SerializerHelper;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class PrevoutProofsMerbinnerTree extends MerbinnerTree<TransactionOutPoint, ColorProof> {
	private NetworkParameters params;

	@Override
	public void serializeKey(Serializer ser, TransactionOutPoint key) throws SerializationException {
		ser.write(key, new SerializerHelper<TransactionOutPoint>() {
			@Override
			public void serialize(Serializer ser, TransactionOutPoint obj) throws SerializationException {
				ser.write(obj.bitcoinSerialize());
			}

			@Override
			public HashCode getHash(TransactionOutPoint obj) {
				return Hashes.calcHash(obj);
			}
		});
	}

	@Override
	public void serializeValue(Serializer ser, ColorProof value) throws SerializationException {
		ser.write(value);
	}

	@Override
	public long getSum(ColorProof value) {
		return value.quantity;
	}

	@Override
	public HashCode getKeyHash(TransactionOutPoint key) {
		return Hashes.calcHash(key);
	}

	@Override
	protected void deserializeNode(Deserializer des) throws SerializationException {
		TransactionOutPoint key = des.readObject(new Deserializer.ObjectReader<TransactionOutPoint>() {
			@Override
			public TransactionOutPoint readObject(Deserializer des) throws SerializationException {
				return new TransactionOutPoint(params, des.readBytes(36), 0);
			}
		});
		ColorProof value = des.readObject(new Deserializer.ObjectReader<ColorProof>() {
			@Override
			public ColorProof readObject(Deserializer des) throws SerializationException {
				return ColorProof.deserialize(params, des);
			}
		});
		entries.put(key, value);
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) throws SerializationException {
		ser.write(sum);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("486a3b9f0cc1adc7f0f7f3e388b89dbc");
	}

	public PrevoutProofsMerbinnerTree(NetworkParameters params, Map<TransactionOutPoint, ColorProof> nodes) {
		super(nodes);
		this.params = params;
	}

	public PrevoutProofsMerbinnerTree(NetworkParameters params) {
		super(Maps.<TransactionOutPoint, ColorProof>newHashMap());
		this.params = params;
	}
}

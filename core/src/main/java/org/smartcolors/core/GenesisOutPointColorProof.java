package org.smartcolors.core;

import com.google.common.hash.HashCode;

import org.bitcoinj.core.TransactionOutPoint;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;
import org.smartcolors.marshal.SerializerHelper;

/**
 * Created by devrandom on 2014-Nov-19.
 */
public class GenesisOutPointColorProof extends ColorProof {
	public static final int PROOF_TYPE = 1;

	private TransactionOutPoint outpoint;

	public GenesisOutPointColorProof() {
	}

	public GenesisOutPointColorProof(ColorDefinition def, TransactionOutPoint outpoint) {
		this.def = def;
		this.outpoint = outpoint;
		this.quantity = calcQuantity();
	}

	private Long calcQuantity() {
		return def.getOutPointGenesisPoints().get(outpoint);
	}

	@Override
	protected void deserialize(Deserializer des) throws SerializationException {
		outpoint = des.readObject(new Deserializer.ObjectReader<TransactionOutPoint>() {
			@Override
			public TransactionOutPoint readObject(Deserializer des) throws SerializationException {
				return new TransactionOutPoint(params, des.readBytes(36), 0);
			}
		});
		this.quantity = calcQuantity();
	}

	@Override
	public void serialize(Serializer ser) throws SerializationException {
		super.serialize(ser);
		ser.write(outpoint, new SerializerHelper<TransactionOutPoint>() {
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
	int getType() {
		return PROOF_TYPE;
	}
}

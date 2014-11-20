package org.smartcolors.core;

import com.google.common.hash.HashCode;

import org.bitcoinj.core.Transaction;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;
import org.smartcolors.marshal.SerializerHelper;

/**
 * Created by devrandom on 2014-Nov-19.
 */
public class GenesisScriptColorProof extends ColorProof {
	public static final int PROOF_TYPE = 2;

	private Transaction tx;
	private long index;

	public GenesisScriptColorProof() {
	}

	public GenesisScriptColorProof(ColorDefinition def, Transaction tx, long index) {
		this.def = def;
		this.tx = tx;
		this.index = index;
		quantity = calcQuantity();
	}

	@Override
	protected void deserialize(Deserializer des) throws SerializationException {
		index = des.readVaruint();
		tx = des.readObject(new Deserializer.ObjectReader<Transaction>() {
			@Override
			public Transaction readObject(Deserializer des) throws SerializationException {
				return new Transaction(params, des.readBytes(), 0);
			}
		});
	}

	@Override
	public void serialize(Serializer ser) throws SerializationException {
		super.serialize(ser);
		ser.write(index);
		ser.write(tx, new SerializerHelper<Transaction>() {
			@Override
			public void serialize(Serializer ser, Transaction obj) throws SerializationException {
				ser.writeWithLength(obj.bitcoinSerialize());
			}

			@Override
			public HashCode getHash(Transaction obj) {
				return Hashes.calcHash(obj);
			}
		});
		quantity = calcQuantity();
	}

	private long calcQuantity() {
		return SmartColors.removeMsbdropValuePadding(tx.getOutput((int) index).getValue().getValue());
	}

	@Override
	int getType() {
		return PROOF_TYPE;
	}
}

package org.smartcolors.core;

import com.google.common.hash.HashCode;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;
import org.smartcolors.marshal.SerializerHelper;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-19.
 */
public class TransferColorProof extends ColorProof {
	public static final int PROOF_TYPE = 3;

	private Transaction tx;
	private long index;
	private PrevoutProofsMerbinnerTree prevouts;

	public TransferColorProof() {
	}

	public TransferColorProof(ColorDefinition def, Transaction tx, long index, Map<TransactionOutPoint, ColorProof> nodes) {
		this.def = def;
		this.tx = tx;
		this.index = index;
		this.prevouts = new PrevoutProofsMerbinnerTree(params, nodes);
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
		prevouts = des.readObject(new Deserializer.ObjectReader<PrevoutProofsMerbinnerTree>() {
			@Override
			public PrevoutProofsMerbinnerTree readObject(Deserializer des) throws SerializationException {
				PrevoutProofsMerbinnerTree tree = new PrevoutProofsMerbinnerTree(params);
				tree.deserialize(des);
				return tree;
			}
		});
		quantity = calcQuantity();
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
		ser.write(prevouts);
	}

	private long calcQuantity() {
		Long[] colorIns = new Long[tx.getInputs().size()];
		for (int i = 0; i < colorIns.length; i++) {
			ColorProof proof = prevouts.get(tx.getInput(i).getOutpoint());
			if (proof != null)
				colorIns[i] = proof.quantity;
		}

		Long[] colorOuts = def.applyKernel(tx, colorIns);
		if (index < 0 || index >= colorOuts.length)
			return 0; // FIXME exception instead?
		if (colorOuts[(int)index] == null)
			return 0; // FIXME
		return colorOuts[(int)index];
	}

	@Override
	int getType() {
		return PROOF_TYPE;
	}
}

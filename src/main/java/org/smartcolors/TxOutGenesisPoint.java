package org.smartcolors;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;

import java.io.IOException;
import java.io.OutputStream;

public class TxOutGenesisPoint extends GenesisPoint {
	public static final byte POINT_TYPE = 0x01;
	private TransactionOutPoint outPoint;

	public TxOutGenesisPoint(NetworkParameters params, TransactionOutPoint outPoint) {
		this.params = params;
		this.outPoint = outPoint;
	}

	public TxOutGenesisPoint() {
	}

	@Override
	public byte getType() {
		return POINT_TYPE;
	}

	@Override
	protected void parse() {
		int cursor = 1;
		outPoint = new TransactionOutPoint(params, payload, cursor);
		cursor += outPoint.getMessageSize();
	}

	@Override
	public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
		super.bitcoinSerializeToStream(stream);
		outPoint.bitcoinSerialize(stream);
	}

	@Override
	public byte[] getBloomFilterElement() {
		return outPoint.bitcoinSerialize();
	}

	public TransactionOutPoint getOutPoint() {
		return outPoint;
	}

	@Override
	public int hashCode() {
		return outPoint.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TxOutGenesisPoint))
			return false;
		return ((TxOutGenesisPoint)obj).outPoint.equals(outPoint);
	}
}
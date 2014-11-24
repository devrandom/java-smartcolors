package org.smartcolors;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.smartcolors.core.ColorDefinition;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public abstract class ColorTrack {
	protected final ColorDefinition definition;

	public ColorTrack(ColorDefinition definition) {
		this.definition = definition;
	}

	public abstract Sha256Hash getStateHash();

	public abstract Long[] applyKernel(Transaction tx);

	public abstract Long getColor(TransactionOutPoint point);

	public ColorDefinition getDefinition() {
		return definition;
	}

	public abstract void reset();

	public abstract boolean isColored(TransactionOutPoint point);
}

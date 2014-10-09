package org.smartcolors;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * The low-level definition of a color
 *
 * <p>Commits to all valid genesis points for this color in a merkle tree. This
 * lets even very large color definitions be used efficiently by SPV clients.
 */
// TODO serialization
public class ColorDefinition {
	public static final int MAX_COLOR_OUTPUTS = 32;

	private final Set<GenesisPoint> genesisPoints;
	private long creationTime;

	public ColorDefinition(Set<GenesisPoint> genesisPoints) {
		this.genesisPoints = genesisPoints;
		// TODO ordered?
		try {
			this.creationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2014-10-01T00:00:00+0000").getTime();
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		// TODO creationTime
	}

	public boolean contains(GenesisPoint point) {
		return genesisPoints.contains(point);
	}

	/**
	 * Calculate the color transferred by a specific txin
	 *
	 * @param input     the input
	 * @param colorIn   color qty of input
	 * @param colorOuts color qty on each output - modified in place
	 * @param tx        transferring transaction
	 */
	public void applyColorTransferred(TransactionInput input, long colorIn, Long colorOuts[], Transaction tx) {
		long remainingColorIn = colorIn;
		int numOutputs = Math.min(tx.getOutputs().size(), MAX_COLOR_OUTPUTS);
		// Which outputs the color in is being sent to is specified by nSequence.

		for (int j = 0; j < numOutputs; j++) {
			TransactionOutput output = tx.getOutput(j);
			// An output is marked as colored if the corresponding bit
			// in nSequence is set to one. This is chosen to allow
			// standard transactions with standard-looking nSquence's to
			// move color.
			if (remainingColorIn > 0 && ((input.getSequenceNumber() >> j) & 1) == 1) {
				// Mark the output as being colored if it hasn't been already.
				if (colorOuts[j] == null)
					colorOuts[j] = 0L;
				// Color is allocated to outputs "bucket-style", where
				// each colored input adds to colored outputs until the
				// output is "full". As color_out is modified in place the
				// allocation is stateful - a previous txin can change where the
				// next txin sends its quantity of color.
				long maxColorOut = SmartColors.removeMsbdropValuePadding(output.getValue().value);
				long transferred = Math.min(remainingColorIn, maxColorOut - colorOuts[j]);
				colorOuts[j] += transferred;
				remainingColorIn -= transferred;

				checkState(colorOuts[j] >= 0);
				checkState(remainingColorIn >= 0);
			}
			// Any remaining color that hasn't been sent to an output by the
			// txin is simply destroyed. This ensures all color transfers
			// happen explicitly, rather than implicitly, which may be
			// useful in the future to reduce proof sizes for large
			// transactions.
		}
	}

	/**
	 * Apply the color kernel to a transaction
	 * <p/>
	 * The kernel only tracks the movement of color from input to output; the
	 * creation of genesis txouts is handled separately.
	 *
	 * @param colorIns color input values, by tx input
	 * @return amount of color out indexed by vout index. Colored outputs are a non-zero integers, uncolored outputs are null.
	 */
	public Long[] applyKernel(Transaction tx, Long colorIns[]) {
		Long[] colorOuts = new Long[tx.getOutputs().size()];
		int numInputs = tx.getInputs().size();
		for (int i = 0; i < numInputs; i++) {
			if (colorIns[i] != null) {
				applyColorTransferred(tx.getInput(i), colorIns[i], colorOuts, tx);
			}
		}
		return colorOuts;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public Set<GenesisPoint> getGenesisPoints() {
		return genesisPoints;
	}
}

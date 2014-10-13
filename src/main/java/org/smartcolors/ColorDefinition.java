package org.smartcolors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkState;

/**
 * The low-level definition of a color
 *
 * <p>Commits to all valid genesis points for this color in a merkle tree. This
 * lets even very large color definitions be used efficiently by SPV clients.
 */
// TODO serialization
public class ColorDefinition {
	protected byte[] payload;
	public static final int MAX_COLOR_OUTPUTS = 32;
	public static final int VERSION = 0;

	private final ImmutableSortedSet<GenesisPoint> genesisPoints;
	private final ImmutableMap<String, String> metadata;
	private long creationTime;

	public ColorDefinition(SortedSet<GenesisPoint> genesisPoints, Map<String, String> metadata) {
		this.genesisPoints = ImmutableSortedSet.copyOf(genesisPoints);
		try {
			this.creationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2014-09-24T00:00:00+0000").getTime() / 1000;
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		this.metadata = ImmutableMap.copyOf(metadata);
		// TODO creationTime
	}

	public ColorDefinition(SortedSet<GenesisPoint> points) {
		this(points, Maps.<String, String>newHashMap());
	}


	public static ColorDefinition fromPayload(NetworkParameters params, byte[] payload) throws ProtocolException {
		return fromPayload(params, payload, Maps.<String, String>newHashMap());
	}

	public static ColorDefinition fromPayload(NetworkParameters params, byte[] payload, Map<String, String> metadata) throws ProtocolException {
		int cursor = 0;
		long version = Utils.readUint32(payload, cursor);
		cursor += 4;
		if (version != VERSION)
			throw new ProtocolException("unexpected version " + version);
		long blockheight = Utils.readUint32(payload, cursor); // TODO convert to timestamp
		cursor += 4;
		cursor += 32; // TODO prevdef hash
		VarInt numPoints = new VarInt(payload, cursor);
		cursor += numPoints.getOriginalSizeInBytes();
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		for (int i = 0; i < numPoints.value; i++) {
			GenesisPoint point = GenesisPoint.fromPayload(params, payload, cursor);
			points.add(point);
		}
		return new ColorDefinition(points, metadata);
	}

	public String getName() {
		return metadata.get("name");
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

	/** Creation time in seconds since the epoch */
	public long getCreationTime() {
		return creationTime;
	}

	/** Creation time in seconds since the epoch */
	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
	}

	public ImmutableSortedSet<GenesisPoint> getGenesisPoints() {
		return genesisPoints;
	}

	@Override
	public String toString() {
		return getName();
	}

	public String toStringFull() {
		StringBuilder builder = new StringBuilder();
		builder.append("[ColorDefinition:\n");
		for (GenesisPoint point: genesisPoints) {
			builder.append("  ");
			builder.append(point.toString());
			builder.append("\n");
		}
		builder.append("]");
		return builder.toString();
	}
}

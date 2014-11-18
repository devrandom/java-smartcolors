package org.smartcolors.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
	public static final TypeReference<ColorDefinition> TYPE_REFERENCE = new TypeReference<ColorDefinition>() {};
	protected byte[] payload;
	public static final int MAX_COLOR_OUTPUTS = 32;
	public static final int VERSION = 0;
	public static final ColorDefinition UNKNOWN = makeUnknown();
	public static final ColorDefinition BITCOIN = makeBitcoin();

	public static final String METADATA_NAME = "name";
	public static final String METADATA_EXTRAHASH = "extrahash";
	public static final String METADATA_ISSUER = "issuer";
	public static final String METADATA_UNIT = "unit";
	public static final String METADATA_TOTAL_UNITS = "total_unit";
	public static final String METADATA_ATOMIC_SIZE = "atomic_size";
	public static final String METADATA_SIGNATURE = "signature";
	public static final String METADATA_GENESIS_TXID = "genesis_txid";
	public static final String METADATA_COLORDEF_URL = "url";

	private static ColorDefinition makeUnknown() {
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put(METADATA_NAME, "UNKNOWN");
		metadata.put(METADATA_EXTRAHASH, "UNKNOWN");
		return new ColorDefinition(Sets.<GenesisPoint>newTreeSet(), metadata);
	}

	private static ColorDefinition makeBitcoin() {
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put(METADATA_NAME, "Bitcoin");
		metadata.put(METADATA_EXTRAHASH, "Bitcoin");
		// FIXME protect the extra hash field from being input by untrusted parties
		return new ColorDefinition(Sets.<GenesisPoint>newTreeSet(), metadata);
	}

	private final ImmutableSortedSet<GenesisPoint> genesisPoints;
	private final Map<String, String> metadata;
	private final long blockheight;
	private final Sha256Hash prevdefHash;
	private long creationTime;
	private Sha256Hash hash;

	// For JSON deserialization
	ColorDefinition(@JsonProperty("definition")String defHex) {
		this.creationTime = SmartColors.getSmartwalletEpoch();
		this.metadata = Maps.newHashMap();
		ColorDefinition def = ColorDefinition.fromPayload(SmartColors.ASSET_PARAMETERS, Utils.parseAsHexOrBase58(defHex), metadata);
		this.genesisPoints = def.genesisPoints;
		this.blockheight = def.blockheight;
		this.prevdefHash = def.prevdefHash;
	}

	public ColorDefinition(SortedSet<GenesisPoint> points, Map<String, String> metadata) {
		this(points, metadata, 0, new byte[32]);
	}

	public ColorDefinition(SortedSet<GenesisPoint> points, Map<String, String> metadata, long blockheight, byte[] prevdefHash) {
		this.genesisPoints = ImmutableSortedSet.copyOf(points);
		this.creationTime = SmartColors.getSmartwalletEpoch();
		this.metadata = metadata;
		this.blockheight = blockheight;
		this.prevdefHash = new Sha256Hash(prevdefHash);
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
		byte[] prevdefHash = Arrays.copyOfRange(payload, cursor, cursor+32);
		cursor += 32; // TODO prevdef hash
		VarInt numPoints = new VarInt(payload, cursor);
		cursor += numPoints.getOriginalSizeInBytes();
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		for (int i = 0; i < numPoints.value; i++) {
			GenesisPoint point = GenesisPoint.fromPayload(params, payload, cursor);
			points.add(point);
		}
		return new ColorDefinition(points, metadata, blockheight, prevdefHash);
	}

	public void bitcoinSerialize(ByteArrayOutputStream bos) throws IOException {
		Utils.uint32ToByteStreamLE(VERSION, bos);
		Utils.uint32ToByteStreamLE(blockheight, bos);
		bos.write(prevdefHash.getBytes());
		VarInt numPoints = new VarInt(genesisPoints.size());
		bos.write(numPoints.encode());
		for (GenesisPoint point: genesisPoints) {
			point.bitcoinSerializeToStream(bos);
		}
	}

	@JsonIgnore
	public String getName() {
		return metadata.get(METADATA_NAME);
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
	@JsonIgnore
	public long getCreationTime() {
		return creationTime;
	}

	/** Creation time in seconds since the epoch */
	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
	}

	@JsonIgnore
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

	@JsonIgnore
	public Sha256Hash getHash() {
		if (hash != null)
			return hash;
		byte[] bytes = getDefinition();
		hash = Sha256Hash.createDouble(bytes);
		return hash;
	}

	@JsonIgnore
	public Map<String, String> getMetadata() {
		return ImmutableMap.copyOf(metadata);
	}

	@JsonAnyGetter
	Map<String, String> anyGetter() {
		byte[] bytes = getDefinition();
		return ImmutableMap.<String, String>builder().putAll(metadata).put("definition", Utils.HEX.encode(bytes)).build();
	}

	/** Bitcoin serialized definition */
	@JsonIgnore
	public byte[] getDefinition() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			bitcoinSerialize(bos);
			if (metadata.containsKey(METADATA_EXTRAHASH)) {
				bos.write(metadata.get(METADATA_EXTRAHASH).getBytes());
			}
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		return bos.toByteArray();
	}

	@JsonAnySetter
	void anySetter(String key, String value) {
		metadata.put(key, value);
	}
}

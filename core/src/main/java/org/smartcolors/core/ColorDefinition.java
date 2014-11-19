package org.smartcolors.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.smartcolors.marshal.BytesSerializer;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.FileSerializer;
import org.smartcolors.marshal.HashableSerializable;
import org.smartcolors.marshal.MemoizedDeserializer;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;

import java.io.InputStream;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * The low-level definition of a color
 *
 * <p>Commits to all valid genesis points for this color in a merkle tree. This
 * lets even very large color definitions be used efficiently by SPV clients.
 */
// TODO serialization
public class ColorDefinition extends HashableSerializable {
	public static final TypeReference<ColorDefinition> TYPE_REFERENCE = new TypeReference<ColorDefinition>() {};
	private final NetworkParameters params;
	public static final int MAX_COLOR_OUTPUTS = 32;
	public static final int VERSION = 1;

	public static final String METADATA_NAME = "name";
	public static final String METADATA_EXTRAHASH = "extrahash";
	public static final String METADATA_ISSUER = "issuer";
	public static final String METADATA_UNIT = "unit";
	public static final String METADATA_TOTAL_UNITS = "total_unit";
	public static final String METADATA_ATOMIC_SIZE = "atomic_size";
	public static final String METADATA_SIGNATURE = "signature";
	public static final String METADATA_GENESIS_TXID = "genesis_txid";
	public static final String METADATA_COLORDEF_URL = "url";

	public static ColorDefinition makeUnknown(NetworkParameters params) {
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put(METADATA_NAME, "UNKNOWN");
		metadata.put(METADATA_EXTRAHASH, "UNKNOWN");
		return new ColorDefinition(params, new GenesisOutPointsMerbinnerTree(params), new GenesisScriptPubkeysMerbinnerTree(), metadata);
	}

	public static ColorDefinition makeBitcoin(NetworkParameters params) {
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put(METADATA_NAME, "Bitcoin");
		metadata.put(METADATA_EXTRAHASH, "Bitcoin");
		// FIXME protect the extra hash field from being input by untrusted parties
		return new ColorDefinition(params, new GenesisOutPointsMerbinnerTree(params), new GenesisScriptPubkeysMerbinnerTree(), metadata);
	}

	private final GenesisOutPointsMerbinnerTree outPointGenesisPoints;
	private final GenesisScriptPubkeysMerbinnerTree scriptGenesisPoints;
	private final Map<String, String> metadata;
	private final long blockheight;
	private long creationTime;
	private byte[] stegkey;

	/*
	// For JSON deserialization
	ColorDefinition(@JsonProperty("definition")String defHex) {
		this.params = NetworkParameters.fromID(NetworkParameters.ID_UNITTESTNET);
		this.creationTime = SmartColors.getSmartwalletEpoch();
		this.metadata = Maps.newHashMap();
		this.outPointGenesisPoints = def.outPointGenesisPoints;
		this.scriptGenesisPoints = def.scriptGenesisPoints;
		this.blockheight = def.blockheight;
	}
	*/

	public ColorDefinition(NetworkParameters params, GenesisOutPointsMerbinnerTree outPointGenesisPoints, GenesisScriptPubkeysMerbinnerTree scriptGenesisPoints, Map<String, String> metadata) {
		this(params, outPointGenesisPoints, scriptGenesisPoints, metadata, 0, new byte[16]);
	}

	public ColorDefinition(NetworkParameters params, GenesisOutPointsMerbinnerTree outPointGenesisPoints, GenesisScriptPubkeysMerbinnerTree scriptGenesisPoints, Map<String, String> metadata, long blockheight, byte[] stegkey) {
		this.params = params;
		this.outPointGenesisPoints = outPointGenesisPoints;
		this.scriptGenesisPoints = scriptGenesisPoints;
		this.creationTime = SmartColors.getSmartwalletEpoch();
		this.metadata = metadata;
		this.blockheight = blockheight;
		this.stegkey = stegkey;
		// TODO creationTime
	}

	public ColorDefinition(NetworkParameters params, GenesisOutPointsMerbinnerTree outPointGenesisPoints, GenesisScriptPubkeysMerbinnerTree scriptGenesisPoints) {
		this(params, outPointGenesisPoints, scriptGenesisPoints, Maps.<String, String>newHashMap());
	}


	@Override
	public void serialize(Serializer ser) throws SerializationException {
		ser.write(VERSION);
		ser.write(blockheight);
		ser.write(stegkey);
		ser.write(outPointGenesisPoints);
		ser.write(scriptGenesisPoints);
		if (metadata.containsKey(METADATA_EXTRAHASH)) {
			ser.write(metadata.get(METADATA_EXTRAHASH).getBytes());
		}
	}

	public static ColorDefinition deserialize(NetworkParameters params, Deserializer des) throws SerializationException {
		long version = des.readVaruint();
		if (version != VERSION)
			throw new SerializationException("unknown version " + version);
		long blockheight = des.readVaruint();
		byte[] stegkey = des.readBytes(16);
		final GenesisOutPointsMerbinnerTree outTree = new GenesisOutPointsMerbinnerTree(params);
		final GenesisScriptPubkeysMerbinnerTree scriptTree = new GenesisScriptPubkeysMerbinnerTree();
		des.readObject(new Deserializer.ObjectReader<GenesisOutPointsMerbinnerTree>() {
			@Override
			public GenesisOutPointsMerbinnerTree readObject(Deserializer des) throws SerializationException {
				outTree.deserialize(des);
				return outTree;
			}
		});
		des.readObject(new Deserializer.ObjectReader<GenesisScriptPubkeysMerbinnerTree>() {
			@Override
			public GenesisScriptPubkeysMerbinnerTree readObject(Deserializer des) throws SerializationException {
				scriptTree.deserialize(des);
				return scriptTree;
			}
		});
		return new ColorDefinition(params, outTree, scriptTree, Maps.<String, String>newHashMap(), blockheight, stegkey);
	}

	@JsonIgnore
	public String getName() {
		return metadata.get(METADATA_NAME);
	}

	public boolean contains(TransactionOutPoint point) {
		return outPointGenesisPoints.constainsKey(point);
	}

	public boolean contains(Script script) {
		return scriptGenesisPoints.constainsKey(script);
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
	public GenesisOutPointsMerbinnerTree getOutPointGenesisPoints() {
		return outPointGenesisPoints;
	}

	@JsonIgnore
	public GenesisScriptPubkeysMerbinnerTree getScriptGenesisPoints() {
		return scriptGenesisPoints;
	}

	@Override
	public String toString() {
		return getName();
	}

	public String toStringFull() {
		StringBuilder builder = new StringBuilder();
		builder.append("[ColorDefinition:\n");
		builder.append("  stegkey: ");
		builder.append(Utils.HEX.encode(stegkey));
		builder.append("\n  blockheight: " + blockheight);
		builder.append("\n");
		for (TransactionOutPoint point: outPointGenesisPoints.keySet()) {
			builder.append("  ");
			builder.append(point.toString());
			builder.append(" : ");
			builder.append(outPointGenesisPoints.get(point));
			builder.append("\n");
		}
		for (Script script: scriptGenesisPoints.keySet()) {
			builder.append("  ");
			builder.append(script.getToAddress(params));
			builder.append("\n");
		}
		builder.append("]");
		return builder.toString();
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("1d8801c1323b4cc5d1b48b289d35aad0");
	}

	@JsonIgnore
	public Map<String, String> getMetadata() {
		return ImmutableMap.copyOf(metadata);
	}

	@JsonAnyGetter
	Map<String, String> anyGetter() {
		BytesSerializer ser = new BytesSerializer();
		try {
			serialize(ser);
		} catch (SerializationException e) {
			Throwables.propagate(e);
		}
		byte[] bytes = ser.getBytes();
		return ImmutableMap.<String, String>builder().putAll(metadata).put("definition", Utils.HEX.encode(bytes)).build();
	}

	@JsonAnySetter
	void anySetter(String key, String value) {
		metadata.put(key, value);
	}

	public long getBlockheight() {
		return blockheight;
	}

	//public static final byte[] FILE_MAGIC = Utils.HEX.decode("00536d617274636f6c6f727300f8acdc00436f6c6f7270726f6f6600cb93f2c5");
	public static final byte[] FILE_MAGIC = Utils.HEX.decode("00536d617274636f6c6f727300fcbe8800436f6c6f7264656600a8edddf21401");

	public static ColorDefinition deserializeFromFile(final NetworkParameters params, InputStream is) throws SerializationException {
		MemoizedDeserializer des = new MemoizedDeserializer(is);

		FileSerializer fser = new FileSerializer() {
			@Override
			protected byte[] getMagic() {
				return FILE_MAGIC;
			}
		};
		fser.readHeader(des);
		ColorDefinition me = des.readObject(new Deserializer.ObjectReader<ColorDefinition>() {
			@Override
			public ColorDefinition readObject(Deserializer des) throws SerializationException {
				return deserialize(params, des);
			}
		});
		fser.verifyHash(des, me);
		return me;
	}

	public byte[] getStegkey() {
		return stegkey;
	}
}

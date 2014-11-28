package org.smartcolors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.hash.HashCode;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.protos.Protos;

import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by devrandom on 2014-Oct-17.
 */
public class SmartwalletExtension implements WalletExtension {
	private static final Logger log = LoggerFactory.getLogger(SmartwalletExtension.class);
	public static final String IDENTIFIER = "org.smartcolors";
	private final ObjectMapper mapper;

	protected ColorScanner scanner;
	protected ColorKeyChain colorKeyChain;

	public SmartwalletExtension(NetworkParameters params) {
		mapper = new ObjectMapper();
		Map<String, Object> values = Maps.newHashMap();
		values.put(ColorDefinition.NETWORK_ID_INJECTABLE, params.getId());
		mapper.setInjectableValues(new InjectableValues.Std(values));
	}

	@Override
	public String getWalletExtensionID() {
		return IDENTIFIER;
	}

	@Override
	public boolean isWalletExtensionMandatory() {
		return false;
	}

	@Override
	public byte[] serializeWalletExtension() {
		Protos.ColorScanner scannerProto = serializeScanner(scanner);
		return scannerProto.toByteArray();
	}

	Protos.ColorScanner serializeScanner(ColorScanner scanner) {
		if (scanner instanceof SPVColorScanner) {
			return serializeSPV((SPVColorScanner) scanner);
		} else {
			return serializeClient((ClientColorScanner) scanner);
		}
	}

	private Protos.ColorScanner serializeClient(ClientColorScanner scanner) {
		Protos.ColorScanner.Builder scannerBuilder = Protos.ColorScanner.newBuilder();
		for (ColorTrack proof : scanner.getColorTracks()) {
			scannerBuilder.addTracks(serializeTrack((ClientColorTrack) proof));
		}
		for (Transaction transaction : scanner.getPending().values()) {
			scannerBuilder.addPending(ByteString.copyFrom(transaction.bitcoinSerialize()));
		}
		return scannerBuilder.build();
	}

	private Protos.ColorScanner serializeSPV(SPVColorScanner scanner) {
		Protos.ColorScanner.Builder scannerBuilder = Protos.ColorScanner.newBuilder();
		for (ColorTrack proof : scanner.getColorTracks()) {
			scannerBuilder.addTracks(serializeTrack((SPVColorTrack) proof));
		}
		for (Map.Entry<Sha256Hash, SortedTransaction> entry : scanner.getMapBlockTx().entries()) {
			scannerBuilder.addBlockToTransaction(Protos.BlockToSortedTransaction.newBuilder()
					.setBlockHash(getHash(entry.getKey()))
					.setTransaction(Protos.SortedTransaction.newBuilder()
							.setTransaction(ByteString.copyFrom(entry.getValue().tx.bitcoinSerialize()))
							.setIndex(entry.getValue().index)));
		}
		for (Transaction transaction : scanner.getPending().values()) {
			scannerBuilder.addPending(ByteString.copyFrom(transaction.bitcoinSerialize()));
		}
		return scannerBuilder.build();
	}

	Protos.ColorTrack serializeTrack(ClientColorTrack track) {
		Protos.ColorTrack.Builder trackBuilder = Protos.ColorTrack.newBuilder();
		serializeTrack(track, trackBuilder);
		return trackBuilder.build();
	}

	Protos.ColorTrack serializeTrack(SPVColorTrack track) {
		Protos.ColorTrack.Builder trackBuilder = Protos.ColorTrack.newBuilder();
		serializeTrack(track, trackBuilder);
		for (Map.Entry<TransactionOutPoint, Long> entry : track.getUnspentOutputs().entrySet()) {
			trackBuilder.addUnspentOutputs(Protos.OutPointValue.newBuilder()
					.setHash(getHash(entry.getKey().getHash()))
					.setIndex(entry.getKey().getIndex())
					.setValue(entry.getValue()));
		}
		for (SortedTransaction tx : track.getTxs()) {
			trackBuilder.addTxs(Protos.SortedTransaction.newBuilder()
					.setIndex(tx.index)
					.setTransaction(ByteString.copyFrom(tx.tx.bitcoinSerialize())));
		}
		return trackBuilder.build();
	}

	private void serializeTrack(ColorTrack track, Protos.ColorTrack.Builder trackBuilder) {
		for (Map.Entry<TransactionOutPoint, Long> entry : track.getOutputs().entrySet()) {
			trackBuilder.addOutputs(Protos.OutPointValue.newBuilder()
					.setHash(getHash(entry.getKey().getHash()))
					.setIndex(entry.getKey().getIndex())
					.setValue(entry.getValue()));
		}
		try {
			trackBuilder.setColorDefinition(Protos.ColorDefinition.newBuilder()
							.setHash(getHash(track.getDefinition().getHash()))
							.setJson(mapper.writeValueAsString(track.getDefinition()))
			);
		} catch (JsonProcessingException e) {
			Throwables.propagate(e);
		}
	}

	private static ByteString getHash(HashCode hash) {
		return ByteString.copyFrom(hash.asBytes());
	}

	private static ByteString getHash(Sha256Hash hash) {
		return ByteString.copyFrom(hash.getBytes());
	}

	@Override
	public void deserializeWalletExtension(Wallet wallet, byte[] data) throws Exception {
		Protos.ColorScanner proto = Protos.ColorScanner.parseFrom(data);
		deserializeScanner(wallet.getParams(), proto, scanner);
	}

	private void deserializeScanner(NetworkParameters params, Protos.ColorScanner proto, ColorScanner scanner) {
		if (scanner instanceof SPVColorScanner) {
			deserializeScannerSPV(params, proto, (SPVColorScanner) scanner);
		} else if (scanner instanceof ClientColorScanner) {
			deserializeScannerClient(params, proto, (ClientColorScanner) scanner);
		} else {
			throw new UnsupportedOperationException("unknown scanner type");
		}
	}

	void deserializeScannerSPV(NetworkParameters params, Protos.ColorScanner proto, SPVColorScanner scanner) {
		SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx = TreeMultimap.create();
		for (Protos.BlockToSortedTransaction bstxp : proto.getBlockToTransactionList()) {
			Transaction transaction = new Transaction(params, bstxp.getTransaction().getTransaction().toByteArray());
			SortedTransaction stx =
					new SortedTransaction(transaction, bstxp.getTransaction().getIndex());
			mapBlockTx.put(getSha256Hash(bstxp.getBlockHash()), stx);
		}
		scanner.setMapBlockTx(mapBlockTx);

		Map<Sha256Hash,Transaction> pending = Maps.newHashMap();
		for (ByteString bytes : proto.getPendingList()) {
			Transaction tx = new Transaction(params, bytes.toByteArray());
			pending.put(tx.getHash(), tx);
		}
		scanner.setPending(pending);

		for (Protos.ColorTrack trackp : proto.getTracksList()) {
			HashCode hash = getHash(trackp.getColorDefinition().getHash());
			ColorTrack proof = scanner.getColorTrackByHash(hash);
			if (proof == null) {
				String json = trackp.getColorDefinition().getJson();
				if (json != null) {
					ColorDefinition def;
					try {
						def = mapper.readValue(json, ColorDefinition.TYPE_REFERENCE);
					} catch (IOException e) {
						throw Throwables.propagate(e);
					}
					try {
						scanner.addDefinition(def);
					} catch (AbstractColorScanner.ColorDefinitionException e) {
						Throwables.propagate(e);
					}
					proof = scanner.getColorTrackByDefinition(def);
				} else {
					log.warn("Could not find color track {} for deserializing", hash);
					continue;
				}
			}
			deserializeProofSPV(params, trackp, (SPVColorTrack)proof);
		}
	}

	void deserializeScannerClient(NetworkParameters params, Protos.ColorScanner proto, ClientColorScanner scanner) {
		Map<Sha256Hash,Transaction> pending = Maps.newHashMap();
		for (ByteString bytes : proto.getPendingList()) {
			Transaction tx = new Transaction(params, bytes.toByteArray());
			pending.put(tx.getHash(), tx);
		}
		scanner.setPending(pending);

		for (Protos.ColorTrack proofp : proto.getTracksList()) {
			HashCode hash = getHash(proofp.getColorDefinition().getHash());
			ColorTrack proof = scanner.getColorTrackByHash(hash);
			if (proof == null) {
				String json = proofp.getColorDefinition().getJson();
				if (json != null) {
					ColorDefinition def;
					try {
						def = mapper.readValue(json, ColorDefinition.TYPE_REFERENCE);
					} catch (IOException e) {
						throw Throwables.propagate(e);
					}
					try {
						scanner.addDefinition(def);
					} catch (AbstractColorScanner.ColorDefinitionException e) {
						Throwables.propagate(e);
					}
					proof = scanner.getColorTrackByDefinition(def);
				} else {
					log.warn("Could not find color track {} for deserializing", hash);
					continue;
				}
			}
			deserializeTrackClient(params, proofp, (ClientColorTrack) proof);
		}
	}

	static void deserializeProofSPV(NetworkParameters params, Protos.ColorTrack proofp, SPVColorTrack proof) {
		deserializeTrack(params, proofp, proof);
		Map<TransactionOutPoint, Long> unspentOutputs = Maps.newHashMap();
		for (Protos.OutPointValue outp : proofp.getUnspentOutputsList()) {
			TransactionOutPoint out = new TransactionOutPoint(params, outp.getIndex(), getSha256Hash(outp.getHash()));
			unspentOutputs.put(out, outp.getValue());
		}
		proof.setUnspentOutputs(unspentOutputs);
		TreeSet<SortedTransaction> txs = Sets.newTreeSet();
		for (Protos.SortedTransaction stxp : proofp.getTxsList()) {
			Transaction transaction = new Transaction(params, stxp.getTransaction().toByteArray());
			SortedTransaction tx = new SortedTransaction(transaction, stxp.getIndex());
			txs.add(tx);
		}
		proof.setTxs(txs);
	}

	private static void deserializeTrack(NetworkParameters params, Protos.ColorTrack proofp, ColorTrack proof) {
		Map<TransactionOutPoint, Long> outputs = Maps.newHashMap();
		for (Protos.OutPointValue outp : proofp.getOutputsList()) {
			TransactionOutPoint out = new TransactionOutPoint(params, outp.getIndex(), getSha256Hash(outp.getHash()));
			outputs.put(out, outp.getValue());
		}
		proof.setOutputs(outputs);
	}

	static void deserializeTrackClient(NetworkParameters params, Protos.ColorTrack proofp, ClientColorTrack proof) {
		deserializeTrack(params, proofp, proof);
	}

	static private Sha256Hash getSha256Hash(ByteString hash) {
		return new Sha256Hash(hash.toByteArray());
	}

	static private HashCode getHash(ByteString hash) {
		return HashCode.fromBytes(hash.toByteArray());
	}

	public void setScanner(ColorScanner scanner) {
		checkNotNull(scanner);
		this.scanner = scanner;
	}

	public ColorScanner getScanner() {
		checkNotNull(scanner);
		return scanner;
	}

	public void setColorKeyChain(ColorKeyChain colorKeyChain) {
		this.colorKeyChain = colorKeyChain;
	}

	public ColorKeyChain getColorKeyChain() {
		return colorKeyChain;
	}
}

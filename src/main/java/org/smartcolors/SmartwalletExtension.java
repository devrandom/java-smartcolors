package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartcolors.protos.Protos;

import java.util.Map;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by devrandom on 2014-Oct-17.
 */
public class SmartwalletExtension implements WalletExtension {
	private static final Logger log = LoggerFactory.getLogger(SmartwalletExtension.class);

	protected ColorScanner scanner;

	@Override
	public String getWalletExtensionID() {
		return "org.smartcolors";
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

	static Protos.ColorScanner serializeScanner(ColorScanner scanner) {
		Protos.ColorScanner.Builder scannerBuilder = Protos.ColorScanner.newBuilder();
		for (ColorProof proof : scanner.getColorProofs()) {
			scannerBuilder.addProofs(serializeProof(proof));
		}
		for (Map.Entry<Sha256Hash, SortedTransaction> entry : scanner.getMapBlockTx().entries()) {
			scannerBuilder.addBlockToTransaction(Protos.BlockToSortedTransaction.newBuilder()
					.setBlockHash(getHash(entry.getKey()))
					.setTransaction(Protos.SortedTransaction.newBuilder()
							.setTransactionHash(getHash(entry.getValue().tx.getHash()))
							.setIndex(entry.getValue().index)));
		}
		return scannerBuilder.build();
	}

	static Protos.ColorProof serializeProof(ColorProof proof) {
		Protos.ColorProof.Builder proofBuilder = Protos.ColorProof.newBuilder();
		for (Map.Entry<TransactionOutPoint, Long> entry : proof.getOutputs().entrySet()) {
			proofBuilder.addOutputs(Protos.OutPointValue.newBuilder()
					.setHash(getHash(entry.getKey().getHash()))
					.setIndex(entry.getKey().getIndex())
					.setValue(entry.getValue()));
		}
		for (Map.Entry<TransactionOutPoint, Long> entry : proof.getUnspentOutputs().entrySet()) {
			proofBuilder.addUnspentOutputs(Protos.OutPointValue.newBuilder()
					.setHash(getHash(entry.getKey().getHash()))
					.setIndex(entry.getKey().getIndex())
					.setValue(entry.getValue()));
		}
		for (SortedTransaction tx : proof.getTxs()) {
			proofBuilder.addTxs(Protos.SortedTransaction.newBuilder()
					.setIndex(tx.index)
					.setTransactionHash(getHash(tx.tx.getHash())));
		}
		proofBuilder.setColorDefinition(Protos.ColorDefinition.newBuilder()
				.setHash(getHash(proof.getDefinition().getHash())));
		return proofBuilder.build();
	}

	private static ByteString getHash(Sha256Hash hash) {
		return ByteString.copyFrom(hash.getBytes());
	}

	@Override
	public void deserializeWalletExtension(Wallet wallet, byte[] data) throws Exception {
		Protos.ColorScanner proto = Protos.ColorScanner.parseFrom(data);
		deserializeScanner(wallet, proto, scanner);
	}

	static void deserializeScanner(Wallet wallet, Protos.ColorScanner proto, ColorScanner scanner) {
		SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx = TreeMultimap.create();
		for (Protos.BlockToSortedTransaction stxp : proto.getBlockToTransactionList()) {
			SortedTransaction tx =
					new SortedTransaction(checkNotNull(wallet.getTransaction(getHash(stxp.getTransaction().getTransactionHash()))),
							stxp.getTransaction().getIndex());
			mapBlockTx.put(getHash(stxp.getBlockHash()), tx);
		}
		for (Protos.ColorProof proofp : proto.getProofsList()) {
			Sha256Hash hash = getHash(proofp.getColorDefinition().getHash());
			ColorProof proof = scanner.getColorProofByHash(hash);
			if (proof == null) {
				log.warn("Could not find color proof {} for deserializing", hash);
				continue;
			}
			deserializeProof(wallet, proofp, proof);
		}

		scanner.setMapBlockTx(mapBlockTx);
	}

	static void deserializeProof(Wallet wallet, Protos.ColorProof proofp, ColorProof proof) {
		Map<TransactionOutPoint, Long> outputs = Maps.newHashMap();
		Map<TransactionOutPoint, Long> unspentOutputs = Maps.newHashMap();
		TreeSet<SortedTransaction> txs = Sets.newTreeSet();
		for (Protos.OutPointValue outp : proofp.getOutputsList()) {
			TransactionOutPoint out = new TransactionOutPoint(wallet.getParams(), outp.getIndex(), getHash(outp.getHash()));
			outputs.put(out, outp.getValue());
		}
		for (Protos.OutPointValue outp : proofp.getUnspentOutputsList()) {
			TransactionOutPoint out = new TransactionOutPoint(wallet.getParams(), outp.getIndex(), getHash(outp.getHash()));
			unspentOutputs.put(out, outp.getValue());
		}
		for (Protos.SortedTransaction txp : proofp.getTxsList()) {
			SortedTransaction tx =
					new SortedTransaction(checkNotNull(wallet.getTransaction(getHash(txp.getTransactionHash()))),
							txp.getIndex());
			txs.add(tx);
		}
		proof.setOutputs(outputs);
		proof.setUnspentOutputs(unspentOutputs);
		proof.setTxs(txs);
	}

	static private Sha256Hash getHash(ByteString hash) {
		return new Sha256Hash(hash.toByteArray());
	}

	public void setScanner(ColorScanner scanner) {
		checkNotNull(scanner);
		this.scanner = scanner;
	}

	public ColorScanner getScanner() {
		checkNotNull(scanner);
		return scanner;
	}
}

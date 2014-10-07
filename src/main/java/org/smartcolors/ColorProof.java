package org.smartcolors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;

import java.util.List;
import java.util.Map;

/**
 * Proof that one or more outpoints are a certain color
 *
 * <p>Contains all transactions required to prove all relevant color moves back
 * to the genesis points. Also manages updates to the proof as
 * blocks/transactions are added/removed.
 */
public class ColorProof {
	private final ColorDefinition definition;
	private Map<TransactionOutPoint, Long> outputs;
	private Map<TransactionOutPoint, Long> unspentOutputs;
	private List<Transaction> txs;

	public ColorProof(ColorDefinition definition) {
		this.definition = definition;
		outputs = Maps.newHashMap();
		unspentOutputs = Maps.newHashMap();
		txs = Lists.newArrayList();
	}

	/**
	 * Add a new transaction to the proof.  outputs and unspentOutputs will be updated.
	 *
	 * <p>Assumes that double spends have already been checked for</p>
	 */
	public void add(Transaction tx) {
		int numOutputs = tx.getOutputs().size();
		for (int i = 0; i < numOutputs; i++) {
			TxOutGenesisPoint genesis = new TxOutGenesisPoint(tx.getParams(), tx.getOutput(i).getOutPointFor());
			if (definition.contains(genesis)) {
				long qty = SmartColors.removeMsbdropValuePadding(tx.getOutput(i).getValue().value);
				outputs.put(genesis.getOutPoint(), qty);
				unspentOutputs.put(genesis.getOutPoint(), qty);
			}
		}

		Long colorIn[] = new Long[tx.getInputs().size()];
		for (int i = 0; i < colorIn.length; i++) {
			TransactionOutPoint prev = tx.getInput(i).getOutpoint();
			if (unspentOutputs.containsKey(prev))
				colorIn[i] = unspentOutputs.get(prev);
		}
		Long colorOut[] = definition.applyKernel(tx, colorIn);
		for (int i = 0; i < colorOut.length; i++) {
			if (colorOut[i] != null) {
				TransactionOutPoint outPoint = new TransactionOutPoint(tx.getParams(), i, tx);
				outputs.put(outPoint, colorOut[i]);
				unspentOutputs.put(outPoint, colorOut[i]);
			}
		}
		for (TransactionInput input : tx.getInputs()) {
			unspentOutputs.remove(input.getOutpoint());
		}
		txs.add(tx);
	}

	public Map<TransactionOutPoint, Long> getOutputs() {
		return outputs;
	}

	public Map<TransactionOutPoint, Long> getUnspentOutputs() {
		return unspentOutputs;
	}
}

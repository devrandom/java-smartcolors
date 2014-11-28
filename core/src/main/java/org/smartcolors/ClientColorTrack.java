package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;

import org.bitcoinj.core.Sha256Hash;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-26.
 */
public class ClientColorTrack extends ColorTrack {
	Map<HashCode, ColorProof> proofs;
	public ClientColorTrack(ColorDefinition definition) {
		super(definition);
		proofs = Maps.newHashMap();
	}

	@Override
	public Sha256Hash getStateHash() {
		return null;
	}

	public void add(ColorProof proof) throws ColorProof.ValidationException {
		proof.validate();
		if (!proof.getDefinition().equals(definition))
			throw new ColorProof.ValidationException("proof is not for our definition - got " + proof.getDefinition() + ", expected " + definition);
		proofs.put(proof.getHash(), proof);
		outputs.put(proof.getOutPoint(), proof.getQuantity());
	}

	public Map<HashCode, ColorProof> getProofs() {
		return proofs;
	}
}

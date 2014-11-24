package org.smartcolors;

import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.Threading;
import org.smartcolors.core.ColorDefinition;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public abstract class AbstractColorScanner implements ColorScanner {
	protected final NetworkParameters params;
	protected Set<ColorTrack> tracks = Sets.newHashSet();
	protected final ColorDefinition unknownDefinition;
	protected final ColorDefinition bitcoinDefinition;
	// General lock.  Wallet lock is internally obtained first for any wallet related work.
	protected final ReentrantLock lock = Threading.lock("colorScanner");

	public AbstractColorScanner(NetworkParameters params) {
		this.bitcoinDefinition = ColorDefinition.makeBitcoin(params);
		this.unknownDefinition = ColorDefinition.makeUnknown(params);
		this.params = params;
	}

	public ColorDefinition getBitcoinDefinition() {
		return bitcoinDefinition;
	}

	public ColorDefinition getUnknownDefinition() {
		return unknownDefinition;
	}

	/** Add a color to the set of tracked colors */
	@Override
	public void addDefinition(ColorDefinition definition) throws ColorDefinitionExists, ColorDefinitionOutdated {

		boolean exists = false;
		if (exists) {
			throw new ColorDefinitionExists();
		}

		boolean outdated = false;
		if (outdated) {
			throw new ColorDefinitionOutdated();
		}

		lock.lock();
		try {
			tracks.add(makeTrack(definition));
		} finally {
   			lock.unlock();
		}
	}

	protected abstract ColorTrack makeTrack(ColorDefinition definition);

	public ColorDefinition getColorDefinitionByHash(Sha256Hash hash) {
		for (ColorDefinition def: getDefinitions()) {
			if (def.getHash().equals(hash))
				return def;
		}
		return null;
	}

	public ColorTrack getColorTrackByHash(HashCode hash) {
		for (ColorTrack track: tracks) {
			if (track.getDefinition().getHash().equals(hash))
				return track;
		}
		return null;
	}

	public ColorTrack getColorTrackByDefinition(ColorDefinition def) {
		for (ColorTrack track: tracks) {
			if (track.getDefinition().equals(def))
				return track;
		}
		return null;
	}

	@Override
	public boolean removeDefinition(ColorDefinition def) throws Exception {
		HashCode hash = def.getHash();
		ColorTrack track = getColorTrackByHash(hash);
		return tracks.remove(track);
	}

	public class ColorDefinitionException extends Exception {
		public ColorDefinitionException(String s) {
			super(s);
		}
	}

	public class ColorDefinitionOutdated extends ColorDefinitionException {
		public ColorDefinitionOutdated() {
			super("Trying to replace an existing definition with an older one.");
		}
	}

	public class ColorDefinitionExists extends ColorDefinitionException {
		public ColorDefinitionExists() {
			super("Trying to replace an existing definition.");
		}
	}
}

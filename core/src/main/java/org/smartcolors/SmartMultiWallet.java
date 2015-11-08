package org.smartcolors;

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

import java.util.List;

/**
 * Created by devrandom on 2015-Oct-19.
 */
abstract public class SmartMultiWallet implements MultiWallet {
    protected final SmartWallet wallet;

    public SmartMultiWallet(SmartWallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return wallet.isPubKeyHashMine(pubkeyHash);
    }

    @Override
    public boolean isWatchedScript(Script script) {
        return wallet.isWatchedScript(script);
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return wallet.isPubKeyHashMine(pubkey);
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        return wallet.isPayToScriptHashMine(payToScriptHash);
    }

    @Override
    public void lock() {
        wallet.lock();
    }

    @Override
    public void unlock() {
        wallet.unlock();
    }

    @Override
    public List<TransactionOutput> getWalletOutputs(Transaction tx) {
        return tx.getWalletOutputs(wallet);
    }

    @Override
    public Transaction getTransaction(Sha256Hash hash) {
        return wallet.getTransaction(hash);
    }

    @Override
    public void saveLater() {
        wallet.doSaveLater();
    }

    @Override
    public Context getContext() {
        return wallet.getContext();
    }

    @Override
    public SmartWallet getWallet() {
        return wallet;
    }

    /**
     * Returns true if this output is to a key, or an address we have the keys for, in the wallet.
     */
    @Override
    public boolean isMine(TransactionOutput output) {
        try {
            Script script = output.getScriptPubKey();
            if (script.isSentToRawPubKey()) {
                byte[] pubkey = script.getPubKey();
                return isPubKeyMine(pubkey);
            } if (script.isPayToScriptHash()) {
                return isPayToScriptHashMine(script.getPubKeyHash());
            } else {
                byte[] pubkeyHash = script.getPubKeyHash();
                return isPubKeyHashMine(pubkeyHash);
            }
        } catch (ScriptException e) {
            // Just means we didn't understand the output of this transaction: ignore it.
            return false;
        }
    }
}

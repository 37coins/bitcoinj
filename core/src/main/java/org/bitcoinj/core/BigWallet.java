package org.bitcoinj.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.bitcoinj.script.Script;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.KeyChainEventListener;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

public class BigWallet{


    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    private Map<Sha256Hash, Transaction> transactions;
    private Set<ByteString> addresses;

    private Set<TransactionOutPoint> spentOutpoints;

    private NetworkParameters params;
    
    private final ReentrantLock lock = Threading.lock("bigWallet");
    
    public BigWallet(NetworkParameters params) {
        this.params = params;
        transactions = new HashMap<Sha256Hash, Transaction>();
        addresses = new HashSet<ByteString>();
        spentOutpoints = new HashSet<TransactionOutPoint>();
    }

    public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
        lock.lock();
        try {
            for (TransactionInput input : tx.getInputs()) {
                // Check tx is spending our coins
                TransactionOutput connected = input.getConnectedOutput(transactions);
                if (connected != null && isMine(connected)) return true;
                
                // Check this tx is a double spend against a tx we track
                TransactionOutPoint outpoint = input.getOutpoint();
                if (spentOutpoints.contains(outpoint)) return true;
                
            }
            // Check tx is sending coins to us
            for (TransactionOutput o : tx.getOutputs()) {
                // Check tx is spending out coins
                if (isMine(o)) return true;
            }
            return false;
            
        } finally {
            lock.unlock();
        }
    }
    
    private boolean isMine(TransactionOutput output) {
        try {
            Script script = output.getScriptPubKey();
            if (script.isPayToScriptHash()) {
                return addresses.contains(ByteString.copyFrom(script.getPubKeyHash()));
            } else {
                return false;
            }
        } catch (ScriptException e) {
            // Just means we didn't understand the output of this transaction: ignore it.
            log.debug("Could not parse tx output script: {}", e.toString());
            return false;
        }
    }


    public void addTransaction(Sha256Hash hash, Transaction transaction) {
        lock.lock();
        try {
            if (transactions.put(hash, transaction) == null) {
                //transaction was not already included in the map
                for (TransactionInput input : transaction.getInputs()) {
                    spentOutpoints.add(input.getOutpoint());
                }

            }
            log.debug("transaction" + hash + " added");
        } finally {
            lock.unlock();
        }
    }

    public void addAddress(ByteString address) {
        lock.lock();
        try {
            addresses.add(address);
        } finally {
            lock.unlock();
        }
    }
    
    
}

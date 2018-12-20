package cy.agorise.graphenej.api.calls;

import java.io.Serializable;
import java.util.ArrayList;

import cy.agorise.graphenej.RPC;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.models.ApiCall;

public class BroadcastTransaction implements ApiCallable {
    public static final int REQUIRED_API = ApiAccess.API_NETWORK_BROADCAST;

    private Transaction mTransaction;

    public BroadcastTransaction(Transaction transaction){
        if(!transaction.hasPrivateKey()) throw new IllegalStateException("The Transaction instance has to be provided with a private key in order to be broadcasted");
        mTransaction = transaction;
    }

    @Override
    public ApiCall toApiCall(int apiId, long sequenceId) {
        ArrayList<Serializable> transactions = new ArrayList<>();
        transactions.add(mTransaction);
        return new ApiCall(apiId, RPC.CALL_BROADCAST_TRANSACTION, transactions, RPC.VERSION, sequenceId);
    }
}

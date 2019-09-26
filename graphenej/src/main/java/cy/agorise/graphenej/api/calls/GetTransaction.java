package cy.agorise.graphenej.api.calls;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cy.agorise.graphenej.RPC;
import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.models.ApiCall;

public class GetTransaction implements ApiCallable {
    public static final int REQUIRED_API = ApiAccess.API_NONE;

    private long blockNumber;
    private long txIndex;

    public GetTransaction(long blockNumber, long txIndex){
        this.blockNumber = blockNumber;
        this.txIndex = txIndex;
    }

    @Override
    public ApiCall toApiCall(int apiId, long sequenceId) {
        List<Serializable> params = new ArrayList<>();
        params.add(blockNumber);
        params.add(txIndex);
        return new ApiCall(apiId, RPC.CALL_GET_TRANSACTION, params, RPC.VERSION, sequenceId);
    }
}

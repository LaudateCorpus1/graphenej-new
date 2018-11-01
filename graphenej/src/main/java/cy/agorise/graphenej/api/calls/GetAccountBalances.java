package cy.agorise.graphenej.api.calls;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.RPC;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.models.ApiCall;

/**
 * Wrapper around the 'get_account_balances' API call.
 *
 * @see <a href="https://goo.gl/faFdey">get_account_balances API doc</a>
 */
public class GetAccountBalances implements ApiCallable {
    public static final int REQUIRED_API = ApiAccess.API_NONE;

    private UserAccount mUserAccount;
    private List<Asset> mAssetList;

    public GetAccountBalances(UserAccount userAccount, List<Asset> assets){
        mUserAccount = userAccount;
        mAssetList = assets;
    }

    @Override
    public ApiCall toApiCall(int apiId, long sequenceId) {
        ArrayList<Serializable> params = new ArrayList<>();
        ArrayList<Serializable> assetList = new ArrayList<>();
        if(mAssetList != null){
            for(Asset asset : mAssetList){
                assetList.add(asset.getObjectId());
            }
        }
        params.add(mUserAccount.getObjectId());
        params.add(assetList);
        return new ApiCall(apiId, RPC.CALL_GET_ACCOUNT_BALANCES, params, RPC.VERSION, sequenceId);
    }
}

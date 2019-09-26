package cy.agorise.graphenej.api.calls;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.RPC;
import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.models.ApiCall;

public class GetAssets implements ApiCallable {
    public static final int REQUIRED_API = ApiAccess.API_NONE;

    private List<Asset> assetList = new ArrayList<>();

    /**
     * Constructor that will receive a List of Asset instances.
     *
     * @param assets List of Asset instances.
     */
    public GetAssets(List<Asset> assets){
        assetList.addAll(assets);
    }

    /**
     * Constructor that will accept a string containing the asset id.
     *
     * @param id String containing the asset id of the desired asset.
     */
    public GetAssets(String id){
        assetList.add(new Asset(id));
    }

    /**
     * Constructor that accepts an {@link Asset} object instance.
     *
     * @param asset Asset class instance.
     */
    public GetAssets(Asset asset){
        assetList.add(asset);
    }

    @Override
    public ApiCall toApiCall(int apiId, long sequenceId) {
        ArrayList<Serializable> params = new ArrayList<>();
        ArrayList<Serializable> assetIds = new ArrayList<>();
        for(Asset asset : assetList){
            assetIds.add(asset.getObjectId());
        }
        params.add(assetIds);
        return new ApiCall(apiId, RPC.CALL_GET_ASSETS, params, RPC.VERSION, sequenceId);
    }
}

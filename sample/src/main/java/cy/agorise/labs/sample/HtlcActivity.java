package cy.agorise.labs.sample;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.common.primitives.UnsignedLong;

import org.bitcoinj.core.ECKey;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;

import cy.agorise.graphenej.Address;
import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.BlockData;
import cy.agorise.graphenej.BrainKey;
import cy.agorise.graphenej.HtlcHash;
import cy.agorise.graphenej.HtlcHashType;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.Util;
import cy.agorise.graphenej.api.ConnectionStatusUpdate;
import cy.agorise.graphenej.api.android.RxBus;
import cy.agorise.graphenej.api.calls.BroadcastTransaction;
import cy.agorise.graphenej.api.calls.GetDynamicGlobalProperties;
import cy.agorise.graphenej.models.DynamicGlobalProperties;
import cy.agorise.graphenej.models.JsonRpcResponse;
import cy.agorise.graphenej.operations.CreateHtlcOperation;
import cy.agorise.labs.sample.fragments.CreateHtlcFragment;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class HtlcActivity extends ConnectedActivity implements CreateHtlcFragment.HtlcListener {
    private String TAG = this.getClass().getName();
    private final short PREIMAGE_LENGTH = 32;

    private CreateHtlcOperation createHtlcOperation;
    private Disposable mDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_htlc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDisposable = RxBus.getBusInstance()
            .asFlowable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<Object>() {

                @Override
                public void accept(Object message) throws Exception {
                    if(message instanceof ConnectionStatusUpdate){
                        // TODO: Update UI ?
                    }else if(message instanceof JsonRpcResponse){
                        handleJsonRpcResponse((JsonRpcResponse) message);
                    }
                }
            });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(!mDisposable.isDisposed())
            mDisposable.dispose();

    }

    @Override
    public void onHtlcProposal(String from, String to, Double amount, Long timelock) {
        Log.d(TAG,"onHtlcProposal");
        UserAccount sourceAccount = new UserAccount(from);
        UserAccount destinationAccount = new UserAccount(to);
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf("86726"), new Asset("1.3.0"));
        AssetAmount operationAmount = new AssetAmount(UnsignedLong.valueOf(Double.valueOf(amount * 100000).longValue()), new Asset("1.3.0"));
        SecureRandom secureRandom = new SecureRandom();
        byte[] preimage = new byte[PREIMAGE_LENGTH];
        secureRandom.nextBytes(preimage);
        try {
            byte[] hash = Util.htlcHash(preimage, HtlcHashType.RIPEMD160);
            HtlcHash htlcHash = new HtlcHash(HtlcHashType.RIPEMD160, hash);
            // Creating a HTLC operation, used later.
            createHtlcOperation = new CreateHtlcOperation(fee, sourceAccount, destinationAccount, operationAmount, htlcHash, PREIMAGE_LENGTH, timelock.intValue());
            // Requesting dynamic network parameters
            long id = mNetworkService.sendMessage(new GetDynamicGlobalProperties(), GetDynamicGlobalProperties.REQUIRED_API);
            Log.d(TAG,"sendMessage returned: " + id);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,"NoSuchAlgorithmException while trying to create HTLC operation. Msg: " + e.getMessage());
        }
    }

    private void handleJsonRpcResponse(JsonRpcResponse jsonRpcResponse){
        Log.d(TAG,"handleJsonRpcResponse");
        if(jsonRpcResponse.error == null && jsonRpcResponse.result instanceof DynamicGlobalProperties){
            DynamicGlobalProperties dynamicGlobalProperties = (DynamicGlobalProperties) jsonRpcResponse.result;
            Transaction tx = buildHltcTransaction(dynamicGlobalProperties);
            long id = mNetworkService.sendMessage(new BroadcastTransaction(tx), BroadcastTransaction.REQUIRED_API);
            Log.d(TAG,"sendMessage returned: " + id);
        }
    }

    private Transaction buildHltcTransaction(DynamicGlobalProperties dynamicProperties){
        // Deriving private key
        BrainKey brainKey = new BrainKey(">> Enter your own test Brainkey here <<", 0);
        ECKey privKey = brainKey.getPrivateKey();
        Address address = new Address(ECKey.fromPublicOnly(privKey.getPubKey()));

        // Use the valid BlockData just obtained from the blockchain
        long expirationTime = (dynamicProperties.time.getTime() / 1000) + Transaction.DEFAULT_EXPIRATION_TIME;
        String headBlockId = dynamicProperties.head_block_id;
        long headBlockNumber = dynamicProperties.head_block_number;
        BlockData blockData = new BlockData(headBlockNumber, headBlockId, expirationTime);

        // Using the HTLC create operaton just obtained before
        ArrayList<BaseOperation> operations = new ArrayList<>();
        operations.add(this.createHtlcOperation);

        // Return a newly built transaction
        return new Transaction(privKey, blockData, operations);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) { }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }
}

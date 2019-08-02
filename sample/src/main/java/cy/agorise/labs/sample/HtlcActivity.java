package cy.agorise.labs.sample;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.common.primitives.UnsignedLong;

import org.bitcoinj.core.ECKey;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.BlockData;
import cy.agorise.graphenej.BrainKey;
import cy.agorise.graphenej.Htlc;
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
import cy.agorise.graphenej.operations.RedeemHtlcOperation;
import cy.agorise.labs.sample.fragments.CreateHtlcFragment;
import cy.agorise.labs.sample.fragments.RedeemHtlcFragment;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class HtlcActivity extends ConnectedActivity implements
        CreateHtlcFragment.CreateHtlcListener,
        RedeemHtlcFragment.RedeemHtlcListener {
    private String TAG = this.getClass().getName();
    private final short PREIMAGE_LENGTH = 32;
    private byte[] mPreimage = new byte[PREIMAGE_LENGTH];

    private CreateHtlcOperation createHtlcOperation;
    private RedeemHtlcOperation redeemHtlcOperation;
    private Disposable mDisposable;
    private String mHtlcMode;

    private Fragment mActiveBottomFragment;
    private CreateHtlcFragment mCreateHtlcFragment;
    private RedeemHtlcFragment mRedeemHtlcFragment;

    private HashMap<Long, String> mResponseMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_htlc);

        mHtlcMode = getIntent().getStringExtra(Constants.KEY_SELECTED_CALL);

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        if(mHtlcMode.equals(CallsActivity.CREATE_HTLC)){
            mCreateHtlcFragment = new CreateHtlcFragment();
            mActiveBottomFragment = mCreateHtlcFragment;
        }else{
            mRedeemHtlcFragment = new RedeemHtlcFragment();
            mActiveBottomFragment = mRedeemHtlcFragment;
        }
        fragmentTransaction.add(R.id.fragment_root, mActiveBottomFragment, "active-fragment").commit();

        Toolbar toolbar = findViewById(R.id.toolbar);
        if(toolbar != null && mHtlcMode != null){
            toolbar.setTitle(mHtlcMode.replace("_", " ").toUpperCase());
            setSupportActionBar(toolbar);
        }

        // While for the real world is best to use a random pre-image, for testing purposes it is more
        // convenient to make use of a fixed one.
//        SecureRandom secureRandom = new SecureRandom();
//        secureRandom.nextBytes(mPreimage);
        mPreimage = Util.hexToBytes("efb79df9b0fd0d27b405e4decf9a2534efc1531f9e133915981fe27cd031ba32");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.htlc_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        this.switchHtlcMode();
        return true;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if(fragment instanceof CreateHtlcFragment){
            mCreateHtlcFragment = (CreateHtlcFragment) fragment;
        }
    }

    private void switchHtlcMode(){
        if(mHtlcMode.equals(CallsActivity.CREATE_HTLC)){
            mHtlcMode = CallsActivity.REDEEM_HTLC;
            if(mRedeemHtlcFragment == null)
                mRedeemHtlcFragment = new RedeemHtlcFragment();
            mActiveBottomFragment = mRedeemHtlcFragment;
        }else{
            mHtlcMode = CallsActivity.CREATE_HTLC;
            if(mCreateHtlcFragment == null)
                mCreateHtlcFragment = new CreateHtlcFragment();
            mActiveBottomFragment = mCreateHtlcFragment;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_root, mActiveBottomFragment, "active-fragment")
                .commit();

        Toolbar toolbar = findViewById(R.id.toolbar);
        if(toolbar != null)
            toolbar.setTitle(mHtlcMode.replace("_", " ").toUpperCase());
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
        UserAccount sourceAccount = new UserAccount(from);
        UserAccount destinationAccount = new UserAccount(to);
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf("86726"), new Asset("1.3.0"));
        AssetAmount operationAmount = new AssetAmount(UnsignedLong.valueOf(Double.valueOf(amount * 100000).longValue()), new Asset("1.3.0"));
        try {
            byte[] hash = Util.htlcHash(mPreimage, HtlcHashType.RIPEMD160);
            HtlcHash htlcHash = new HtlcHash(HtlcHashType.RIPEMD160, hash);
            // Creating a HTLC operation, used later.
            createHtlcOperation = new CreateHtlcOperation(fee, sourceAccount, destinationAccount, operationAmount, htlcHash, PREIMAGE_LENGTH, timelock.intValue());
            // Requesting dynamic network parameters
            long id = mNetworkService.sendMessage(new GetDynamicGlobalProperties(), GetDynamicGlobalProperties.REQUIRED_API);
            mResponseMap.put(id, CallsActivity.CREATE_HTLC);
            Log.d(TAG,"sendMessage returned: " + id);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,"NoSuchAlgorithmException while trying to create HTLC operation. Msg: " + e.getMessage());
        }
    }

    @Override
    public void onRedeemProposal(String userId, String htlcId) {
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf("255128"), new Asset("1.3.0"));
        UserAccount redeemer = new UserAccount(userId);
        Htlc htlc = new Htlc(htlcId);
        redeemHtlcOperation = new RedeemHtlcOperation(fee, redeemer, htlc, mPreimage);
        long id = mNetworkService.sendMessage(new GetDynamicGlobalProperties(), GetDynamicGlobalProperties.REQUIRED_API);
        mResponseMap.put(id, CallsActivity.REDEEM_HTLC);
        Log.d(TAG,"sendMessage returned: " + id);
    }

    private void handleJsonRpcResponse(JsonRpcResponse jsonRpcResponse){
        Log.d(TAG,"handleJsonRpcResponse");
        if(jsonRpcResponse.error == null && jsonRpcResponse.result instanceof DynamicGlobalProperties){
            DynamicGlobalProperties dynamicGlobalProperties = (DynamicGlobalProperties) jsonRpcResponse.result;
            Transaction tx = buildHltcTransaction(dynamicGlobalProperties, jsonRpcResponse.id);
            long id = mNetworkService.sendMessage(new BroadcastTransaction(tx), BroadcastTransaction.REQUIRED_API);
            Log.d(TAG,"sendMessage returned: " + id);
        }
    }

    /**
     * Private method used to build a transaction containing a specific HTLC operation.
     *
     * @param dynamicProperties The current dynamic properties.
     * @param responseId The response id, used to decide whether to build a CREATE_HTLC or REDEEM_HTLC operation.
     * @return A transaction that contains an HTLC operation.
     */
    private Transaction buildHltcTransaction(DynamicGlobalProperties dynamicProperties, long responseId){
        // Private key, to be obtained differently below depending on which operation we'll be performing.
        ECKey privKey = null;

        // Use the valid BlockData just obtained from the blockchain
        long expirationTime = (dynamicProperties.time.getTime() / 1000) + Transaction.DEFAULT_EXPIRATION_TIME;
        String headBlockId = dynamicProperties.head_block_id;
        long headBlockNumber = dynamicProperties.head_block_number;
        BlockData blockData = new BlockData(headBlockNumber, headBlockId, expirationTime);

        // Using the HTLC create operaton just obtained before
        ArrayList<BaseOperation> operations = new ArrayList<>();

        if(mResponseMap.get(responseId).equals(CallsActivity.CREATE_HTLC)){
            // Deriving private key
            BrainKey brainKey = new BrainKey(">> Place brainkey of HTLC creator here <<", 0);
            privKey = brainKey.getPrivateKey();

            operations.add(this.createHtlcOperation);
        }else if(mResponseMap.get(responseId).equals(CallsActivity.REDEEM_HTLC)){
            // Deriving private key
            BrainKey brainKey = new BrainKey(">> Place brainkey of redeemer here <<", 0);
            privKey = brainKey.getPrivateKey();

            operations.add(this.redeemHtlcOperation);
        }

        // Return a newly built transaction
        return new Transaction(privKey, blockData, operations);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) { }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }
}

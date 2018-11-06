package cy.agorise.graphenej.api.android;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.TimeUnit;

import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.LimitOrder;
import cy.agorise.graphenej.Memo;
import cy.agorise.graphenej.RPC;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.api.ConnectionStatusUpdate;
import cy.agorise.graphenej.api.calls.ApiCallable;
import cy.agorise.graphenej.api.calls.GetAccountBalances;
import cy.agorise.graphenej.api.calls.GetAccounts;
import cy.agorise.graphenej.api.calls.GetFullAccounts;
import cy.agorise.graphenej.api.calls.GetKeyReferences;
import cy.agorise.graphenej.api.calls.GetLimitOrders;
import cy.agorise.graphenej.api.calls.GetMarketHistory;
import cy.agorise.graphenej.api.calls.GetObjects;
import cy.agorise.graphenej.api.calls.GetRelativeAccountHistory;
import cy.agorise.graphenej.api.calls.GetRequiredFees;
import cy.agorise.graphenej.api.calls.ListAssets;
import cy.agorise.graphenej.models.AccountProperties;
import cy.agorise.graphenej.models.ApiCall;
import cy.agorise.graphenej.models.Block;
import cy.agorise.graphenej.models.BlockHeader;
import cy.agorise.graphenej.models.BucketObject;
import cy.agorise.graphenej.models.DynamicGlobalProperties;
import cy.agorise.graphenej.models.FullAccountDetails;
import cy.agorise.graphenej.models.HistoryOperationDetail;
import cy.agorise.graphenej.models.JsonRpcNotification;
import cy.agorise.graphenej.models.JsonRpcResponse;
import cy.agorise.graphenej.models.OperationHistory;
import cy.agorise.graphenej.network.FullNode;
import cy.agorise.graphenej.network.LatencyNodeProvider;
import cy.agorise.graphenej.network.NodeLatencyVerifier;
import cy.agorise.graphenej.network.NodeProvider;
import cy.agorise.graphenej.operations.CustomOperation;
import cy.agorise.graphenej.operations.LimitOrderCreateOperation;
import cy.agorise.graphenej.operations.TransferOperation;
import cy.agorise.graphenej.stats.ExponentialMovingAverage;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Service in charge of maintaining a connection to the full node.
 */

public class NetworkService extends Service {
    private final String TAG = this.getClass().getName();

    public static final int NORMAL_CLOSURE_STATUS = 1000;

    // Time to wait before retrying a connection attempt
    private static final int DEFAULT_RETRY_DELAY = 500;

    // Default connection delay when using the node latency verification strategy. This initial
    // delay is required in order ot make sure we have a fair selection of node latencies from
    // which we can choose from.
    private final int DEFAULT_INITIAL_DELAY = 2000;

    /**
     * Constant to be used as a key in order to pass the user name information, in case the
     * provided API nodes might require this information.
     */
    public static final String KEY_USERNAME = "key_username";

    /**
     * Constant to be used as a key in order to pass the password information, in case the
     * provided API nodes might require this information.
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_PASSWORD = "key_password";

    /**
     * Constant used as a key in order to specify which APIs the application will be requiring.
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_REQUESTED_APIS = "key_requested_apis";

    /**
     * Constant used as a key in order to let the NetworkService know whether or not it should
     * start a recurring node latency verification task.
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_ENABLE_LATENCY_VERIFIER = "key_enable_latency_verifier";

    /**
     * Constant used as a key in order to specify the alpha (or smoothing) factor to be used in
     * the exponential moving average calculated from the different latency samples. This only
     * makes sense if the latency verification feature is enabled of course.
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_NODE_LATENCY_SMOOTHING_FACTOR = "key_node_latency_smoothing_factor";

    /**
     * Key used to pass via intent a boolean extra to specify whether the connection should
     * be automatically established.
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_AUTO_CONNECT = "key_auto_connect";

    /**
     * Key used to pass via intent a list of node URLs. The value passed should be a String
     * containing a simple comma separated list of URLs.
     * <p>
     * For example:
     *
     *      wss://domain1.com/ws,wss://domain2.com/ws,wss://domain3.com/ws
     * <p>
     * This information should be passed as an intent extra when calling the bindService
     * or startService methods.
     */
    public static final String KEY_NODE_URLS = "key_node_urls";

    private final IBinder mBinder = new LocalBinder();

    private WebSocket mWebSocket;

    // Username and password used to connect to a specific node
    private String mUsername;
    private String mPassword;

    private boolean isLoggedIn = false;

    private String mLastCall;
    private long mCurrentId = 0;

    private boolean mAutoConnect;

    // Requested APIs passed to this service
    private int mRequestedApis;

    // Variable used to keep track of the currently obtained API accesses
    private HashMap<Integer, Integer> mApiIds = new HashMap<Integer, Integer>();

    // Variable used as a source of node information
    private NodeProvider nodeProvider = new LatencyNodeProvider();

    // Class used to obtain frequent node latency updates
    private NodeLatencyVerifier nodeLatencyVerifier;

    // PublishSubject used to announce full node latencies updates
    private PublishSubject<FullNode> fullNodePublishSubject;

    // Counter used to trigger the connection only after we've received enough node latency updates
    private long latencyUpdateCounter;

    // Property used to keep track of the currently active node
    private FullNode mSelectedNode;

    private Handler mHandler = new Handler();

    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(Transaction.class, new Transaction.TransactionDeserializer())
            .registerTypeAdapter(TransferOperation.class, new TransferOperation.TransferDeserializer())
            .registerTypeAdapter(LimitOrderCreateOperation.class, new LimitOrderCreateOperation.LimitOrderCreateDeserializer())
            .registerTypeAdapter(CustomOperation.class, new CustomOperation.CustomOperationDeserializer())
            .registerTypeAdapter(AssetAmount.class, new AssetAmount.AssetAmountDeserializer())
            .registerTypeAdapter(UserAccount.class, new UserAccount.UserAccountSimpleDeserializer())
            .registerTypeAdapter(DynamicGlobalProperties.class, new DynamicGlobalProperties.DynamicGlobalPropertiesDeserializer())
            .registerTypeAdapter(Memo.class, new Memo.MemoDeserializer())
            .registerTypeAdapter(BaseOperation.class, new BaseOperation.OperationDeserializer())
            .registerTypeAdapter(OperationHistory.class, new OperationHistory.OperationHistoryDeserializer())
            .registerTypeAdapter(JsonRpcNotification.class, new JsonRpcNotification.JsonRpcNotificationDeserializer())
            .create();

    // Map used to keep track of outgoing request ids and its request types. This is just
    // one of two required mappings. The second one is implemented by the DeserializationMap
    // class.
    private HashMap<Long, Class> mRequestClassMap = new HashMap<>();

    // This class is used to keep track of the mapping between request classes and response
    // payload classes. It also provides a handy method that returns a Gson deserializer instance
    // suited for every response type.
    private DeserializationMap mDeserializationMap = new DeserializationMap();

    /**
     * Actually establishes a connection from this Service to one of the full nodes.
     */
    public void connect(){
        OkHttpClient client = new OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .build();
        mSelectedNode = nodeProvider.getBestNode();
        Log.v(TAG,"connect.url: "+ mSelectedNode.getUrl()+", latency: "+mSelectedNode.getLatencyValue());
        Request request = new Request.Builder().url(mSelectedNode.getUrl()).build();
        mWebSocket = client.newWebSocket(request, mWebSocketListener);
    }

    public long sendMessage(String message){
        if(mWebSocket != null){
            if(mWebSocket.send(message)){
                Log.v(TAG,"-> " + message);
                return mCurrentId;
            }
        }else{
            throw new RuntimeException("Websocket connection has not yet been established");
        }
        return -1;
    }

    /**
     * Method that will send a message to the full node, and takes as an argument one of the
     * API call wrapper classes. This is the preferred method of sending blockchain API calls.
     *
     * @param apiCallable   The object that will get serialized into a request
     * @param requiredApi   The required APIs for this specific request. Should be one of the
     *                      constants specified in the ApiAccess class.
     * @return              The id of the message that was just sent, or -1 if no message was sent.
     */
    public long sendMessage(ApiCallable apiCallable, int requiredApi){
        if(requiredApi != -1 && mApiIds.containsKey(requiredApi) || requiredApi == ApiAccess.API_NONE){
            int apiId = 0;
            if(requiredApi != ApiAccess.API_NONE)
                apiId = mApiIds.get(requiredApi);
            ApiCall call = apiCallable.toApiCall(apiId, ++mCurrentId);
            mRequestClassMap.put(mCurrentId, apiCallable.getClass());
            if(mWebSocket != null && mWebSocket.send(call.toJsonString())){
                Log.v(TAG,"-> "+call.toJsonString());
                return mCurrentId;
            }
        }
        return -1;
    }

    /**
     * Method used to inform any external party a clue about the current connectivity status
     * @return  True if the service is currently connected and logged in, false otherwise.
     */
    public boolean isConnected(){
        return mWebSocket != null && isLoggedIn;
    }

    @Override
    public void onDestroy() {
        if(mWebSocket != null)
            mWebSocket.close(NORMAL_CLOSURE_STATUS, null);

        if(nodeLatencyVerifier != null)
            nodeLatencyVerifier.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Initialize information and try to connect to a node accordingly. This methods were moved
     * from onBind to avoid crashes due to components other than {@link NetworkServiceManager}
     * binding to the service without submitting the proper information.
     *
     * @param extras    Bundle that contains all required information for a proper initialization
     */
    public void bootstrapService(Bundle extras) {
        // Retrieving credentials and requested API data from the shared preferences
        mUsername = extras.getString(NetworkService.KEY_USERNAME, "");
        mPassword = extras.getString(NetworkService.KEY_PASSWORD, "");
        mRequestedApis = extras.getInt(NetworkService.KEY_REQUESTED_APIS, 0);
        mAutoConnect = extras.getBoolean(NetworkService.KEY_AUTO_CONNECT, true);
        boolean verifyNodeLatency = extras.getBoolean(NetworkService.KEY_ENABLE_LATENCY_VERIFIER, false);

        // If the user of the library desires, a custom list of node URLs can
        // be passed using the KEY_NODE_URLS constant
        String nodeURLStr = extras.getString(NetworkService.KEY_NODE_URLS, "");
        if(nodeURLStr.equals("")){
            throw new MissingResourceException("A comma-separated list of node URLs must be provided as an intent extra", String.class.getName(), NetworkService.KEY_NODE_URLS);
        }

        // Adding user-provided list of node URLs
        String[] urls = nodeURLStr.split(",");

        // Feeding all node information to the NodeProvider instance
        for(String nodeUrl : urls){
            nodeProvider.addNode(new FullNode(nodeUrl));
        }

        // We only connect automatically if the auto-connect flag is true AND
        // we are not going to care about node latency ordering.
        if(mAutoConnect && !verifyNodeLatency) {
            connect();
        }else{
            // In case we care about node latency ordering, we must first obtain
            // a first round of measurements in order to be sure to select the
            // best node.
            if(verifyNodeLatency){
                double alpha = extras.getDouble(KEY_NODE_LATENCY_SMOOTHING_FACTOR, ExponentialMovingAverage.DEFAULT_ALPHA);
                ArrayList<FullNode> fullNodes = new ArrayList<>();
                for(String url : urls){
                    fullNodes.add(new FullNode(url, alpha));
                }
                nodeLatencyVerifier = new NodeLatencyVerifier(fullNodes);
                fullNodePublishSubject = nodeLatencyVerifier.start();
                fullNodePublishSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(nodeLatencyObserver);
                mHandler.postDelayed(mConnectAttempt, DEFAULT_INITIAL_DELAY);
            }
        }
    }

    /**
     * Runnable that will perform a connection attempt with the best node after DEFAULT_INITIAL_DELAY
     * milliseconds. This is used only if the node latency verification is activated.
     *
     * The reason to delay the initial connection is that we want to ideally connect to the best node,
     * meaning the one that offers the lowest latency value. But we have to give some time for the
     * first node latency measurement round to finish in order to have at least a partial result set
     * that could be used.
     */
    private Runnable mConnectAttempt = new Runnable() {
        @Override
        public void run() {
            FullNode fullNode = nodeProvider.getBestNode();
            if(fullNode != null){
                Log.i(TAG, String.format("Connected with %d latency results", latencyUpdateCounter));
                connect();
            }else{
                mHandler.postDelayed(this, DEFAULT_INITIAL_DELAY);
            }
        }
    };

    /**
     * Observer used to be notified about node latency measurement updates.
     */
    private Observer<FullNode> nodeLatencyObserver = new Observer<FullNode>() {
        @Override
        public void onSubscribe(Disposable d) { }

        @Override
        public void onNext(FullNode fullNode) {
            latencyUpdateCounter++;
            // Updating the node with the new latency measurement
            nodeProvider.updateNode(fullNode);
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG,"nodeLatencyObserver.onError.Msg: "+e.getMessage());
        }

        @Override
        public void onComplete() { }
    };

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public NetworkService getService() {
            // Return this instance of LocalService so clients can call public methods
            return NetworkService.this;
        }
    }

    private WebSocketListener mWebSocketListener = new WebSocketListener() {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);

            // Marking the selected node as connected
            mSelectedNode.setConnected(true);

            // Updating the selected node's 'connected' status on the NodeLatencyVerifier instance
            if(nodeLatencyVerifier != null)
                nodeLatencyVerifier.updateActiveNodeInformation(mSelectedNode);

            // Notifying all listeners about the new connection status
            RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.CONNECTED, ApiAccess.API_NONE));

            // If we're not yet logged in, we should do it now
            if(!isLoggedIn){
                ArrayList<Serializable> loginParams = new ArrayList<>();
                loginParams.add(mUsername);
                loginParams.add(mPassword);
                ApiCall loginCall = new ApiCall(1, RPC.CALL_LOGIN, loginParams, RPC.VERSION, ++mCurrentId);
                mLastCall = RPC.CALL_LOGIN;
                sendMessage(loginCall.toJsonString());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            Log.v(TAG,"<- "+text);
            JsonRpcNotification notification = gson.fromJson(text, JsonRpcNotification.class);

            if(notification.method != null){
                // If we are dealing with a notification
                handleJsonRpcNotification(notification);
            }else{
                // If we are dealing with a response
                JsonRpcResponse<?> response = gson.fromJson(text, JsonRpcResponse.class);
                if(response.result != null){
                    // Handling initial handshake with the full node (authentication and API access checks)
                    if(response.result instanceof Double || response.result instanceof Boolean){
                        switch (mLastCall) {
                            case RPC.CALL_LOGIN:
                                isLoggedIn = true;

                                // Broadcasting result
                                RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.AUTHENTICATED, ApiAccess.API_NONE));

                                checkNextRequestedApiAccess();
                                break;
                            case RPC.CALL_DATABASE: {
                                // Deserializing integer response
                                Type IntegerJsonResponse = new TypeToken<JsonRpcResponse<Integer>>() {}.getType();
                                JsonRpcResponse<Integer> apiIdResponse = gson.fromJson(text, IntegerJsonResponse);

                                // Storing the "database" api id
                                mApiIds.put(ApiAccess.API_DATABASE, apiIdResponse.result);

                                // Broadcasting result
                                RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.API_UPDATE, ApiAccess.API_DATABASE));

                                checkNextRequestedApiAccess();
                                break;
                            }
                            case RPC.CALL_HISTORY: {
                                // Deserializing integer response
                                Type IntegerJsonResponse = new TypeToken<JsonRpcResponse<Integer>>() {}.getType();
                                JsonRpcResponse<Integer> apiIdResponse = gson.fromJson(text, IntegerJsonResponse);

                                // Broadcasting result
                                RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.API_UPDATE, ApiAccess.API_HISTORY));

                                // Storing the "history" api id
                                mApiIds.put(ApiAccess.API_HISTORY, apiIdResponse.result);

                                checkNextRequestedApiAccess();
                                break;
                            }
                            case RPC.CALL_NETWORK_BROADCAST:
                                // Deserializing integer response
                                Type IntegerJsonResponse = new TypeToken<JsonRpcResponse<Integer>>() {}.getType();
                                JsonRpcResponse<Integer> apiIdResponse = gson.fromJson(text, IntegerJsonResponse);

                                // Broadcasting result
                                RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.API_UPDATE, ApiAccess.API_NETWORK_BROADCAST));

                                // Storing the "network_broadcast" api access
                                mApiIds.put(ApiAccess.API_NETWORK_BROADCAST, apiIdResponse.result);

                                // All calls have been handled at this point
                                mLastCall = "";
                                break;
                        }
                    }
                }
                if(response.error != null && response.error.message != null){
                    // We could not make sense of this incoming message, just log a warning
                    Log.w(TAG,"Error.Msg: "+response.error.message);
                }
                // Properly de-serialize all other fields and broadcasts to the event bus
                handleJsonRpcResponse(response, text);
            }
        }

        /**
         * Private method that will de-serialize all fields of every kind of JSON-RPC response
         * and broadcast it to the event bus.
         *
         * @param response  De-serialized response
         * @param text      Raw text, as received
         */
        private void handleJsonRpcResponse(JsonRpcResponse response, String text){
            JsonRpcResponse parsedResponse = null;

            Class requestClass = mRequestClassMap.get(response.id);
            if(requestClass != null){
                // Removing the class entry in the map
                mRequestClassMap.remove(response.id);

                // Obtaining the response payload class
                Class responsePayloadClass = mDeserializationMap.getReceivedClass(requestClass);
                Gson gson = mDeserializationMap.getGson(requestClass);
                if(responsePayloadClass == Block.class){
                    // If the response payload is a Block instance, we proceed to de-serialize it
                    Type GetBlockResponse = new TypeToken<JsonRpcResponse<Block>>() {}.getType();
                    parsedResponse = gson.fromJson(text, GetBlockResponse);
                }else if(responsePayloadClass == BlockHeader.class){
                    // If the response payload is a BlockHeader instance, we proceed to de-serialize it
                    Type GetBlockHeaderResponse = new TypeToken<JsonRpcResponse<BlockHeader>>(){}.getType();
                    parsedResponse = gson.fromJson(text, GetBlockHeaderResponse);
                } else if(responsePayloadClass == AccountProperties.class){
                    Type GetAccountByNameResponse = new TypeToken<JsonRpcResponse<AccountProperties>>(){}.getType();
                    parsedResponse = gson.fromJson(text, GetAccountByNameResponse);
                } else if(responsePayloadClass == HistoryOperationDetail.class){
                    Type GetAccountHistoryByOperationsResponse = new TypeToken<JsonRpcResponse<HistoryOperationDetail>>(){}.getType();
                    parsedResponse = gson.fromJson(text, GetAccountHistoryByOperationsResponse);
                }else if(responsePayloadClass == DynamicGlobalProperties.class){
                    Type GetDynamicGlobalPropertiesResponse = new TypeToken<JsonRpcResponse<DynamicGlobalProperties>>(){}.getType();
                    parsedResponse = gson.fromJson(text, GetDynamicGlobalPropertiesResponse);
                }else if(responsePayloadClass == List.class){
                    // If the response payload is a List, further inquiry is required in order to
                    // determine a list of what is expected here
                    if(requestClass == GetAccounts.class){
                        // If the request call was the wrapper to the get_accounts API call, we know
                        // the response should be in the form of a JsonRpcResponse<List<AccountProperties>>
                        // so we proceed with that
                        Type GetAccountsResponse = new TypeToken<JsonRpcResponse<List<AccountProperties>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetAccountsResponse);
                    }else if(requestClass == GetRequiredFees.class){
                        Type GetRequiredFeesResponse = new TypeToken<JsonRpcResponse<List<AssetAmount>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetRequiredFeesResponse);
                    }else if(requestClass == GetRelativeAccountHistory.class){
                        Type RelativeAccountHistoryResponse = new TypeToken<JsonRpcResponse<List<OperationHistory>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, RelativeAccountHistoryResponse);
                    }else if(requestClass == GetMarketHistory.class){
                        Type GetMarketHistoryResponse = new TypeToken<JsonRpcResponse<List<BucketObject>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetMarketHistoryResponse);
                    }else if(requestClass == GetObjects.class){
                        parsedResponse = handleGetObject(text);
                    }else if(requestClass == ListAssets.class){
                        Type LisAssetsResponse = new TypeToken<JsonRpcResponse<List<Asset>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, LisAssetsResponse);
                    }else if(requestClass == GetLimitOrders.class){
                        Type GetLimitOrdersResponse = new TypeToken<JsonRpcResponse<List<LimitOrder>>>() {}.getType();
                        parsedResponse = gson.fromJson(text, GetLimitOrdersResponse);
                    } else if (requestClass == GetFullAccounts.class) {
                        Type GetFullAccountsResponse = new TypeToken<JsonRpcResponse<List<FullAccountDetails>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetFullAccountsResponse);
                    } else if(requestClass == GetKeyReferences.class){
                        Type GetKeyReferencesResponse = new TypeToken<JsonRpcResponse<List<List<UserAccount>>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetKeyReferencesResponse);
                    } else if(requestClass == GetAccountBalances.class){
                        Type GetAccountBalancesResponse = new TypeToken<JsonRpcResponse<List<AssetAmount>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetAccountBalancesResponse);
                    }else {
                        Log.w(TAG,"Unknown request class");
                    }
                }else{
                    Log.w(TAG,"Unhandled situation");
                }
            }

            // In case the parsedResponse instance is null, we fall back to the raw response
            if(parsedResponse == null){
                parsedResponse = response;
            }
            // Broadcasting the parsed response to all interested listeners
            RxBus.getBusInstance().send(parsedResponse);
        }

        /**
         * Private method that will just broadcast a de-serialized notification to all interested parties
         * @param notification  De-serialized notification
         */
        private void handleJsonRpcNotification(JsonRpcNotification notification){
            // Broadcasting the parsed notification to all interested listeners
            RxBus.getBusInstance().send(notification);
        }

        /**
         * Method used to try to deserialize a 'get_objects' API call. Since this request can be used
         * for several types of objects, the de-serialization procedure can be a bit more complex.
         *
         * @param response  Response to a 'get_objects' API call
         */
        private JsonRpcResponse handleGetObject(String response){
            //TODO: Implement a proper de-serialization logic
            return null;
        }

        /**
         * Method used to check all possible API accesses.
         *
         * The service will try to obtain sequentially API access ids for the following APIs:
         *
         * - Database
         * - History
         * - Network broadcast
         */
        private void checkNextRequestedApiAccess(){
            if( (mRequestedApis & ApiAccess.API_DATABASE) == ApiAccess.API_DATABASE &&
                    mApiIds.get(ApiAccess.API_DATABASE) == null){
                // If we need the "database" api access and we don't yet have it

                ApiCall apiCall = new ApiCall(1, RPC.CALL_DATABASE, null, RPC.VERSION, ++mCurrentId);
                mLastCall = RPC.CALL_DATABASE;
                sendMessage(apiCall.toJsonString());
            } else if( (mRequestedApis & ApiAccess.API_HISTORY) == ApiAccess.API_HISTORY &&
                    mApiIds.get(ApiAccess.API_HISTORY) == null){
                // If we need the "history" api access and we don't yet have it

                ApiCall apiCall = new ApiCall(1, RPC.CALL_HISTORY, null, RPC.VERSION, ++mCurrentId);
                mLastCall = RPC.CALL_HISTORY;
                sendMessage(apiCall.toJsonString());
            }else if( (mRequestedApis & ApiAccess.API_NETWORK_BROADCAST) == ApiAccess.API_NETWORK_BROADCAST &&
                    mApiIds.get(ApiAccess.API_NETWORK_BROADCAST) == null){
                // If we need the "network_broadcast" api access and we don't yet have it

                ApiCall apiCall = new ApiCall(1, RPC.CALL_NETWORK_BROADCAST, null, RPC.VERSION, ++mCurrentId);
                mLastCall = RPC.CALL_NETWORK_BROADCAST;
                sendMessage(apiCall.toJsonString());
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            Log.d(TAG,"onClosed");
            RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.DISCONNECTED, ApiAccess.API_NONE));

            isLoggedIn = false;

            // Marking the selected node as not connected
            mSelectedNode.setConnected(false);

            // Updating the selected node's 'connected' status on the NodeLatencyVerifier instance
            if(nodeLatencyVerifier != null)
                nodeLatencyVerifier.updateActiveNodeInformation(mSelectedNode);


            // We have currently no selected node
            mSelectedNode = null;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            Log.e(TAG,"onFailure. Exception: "+t.getClass().getName()+", Msg: "+t.getMessage());
            // Logging error stack trace
            for(StackTraceElement element : t.getStackTrace()){
                Log.v(TAG,String.format("%s#%s:%s", element.getClassName(), element.getMethodName(), element.getLineNumber()));
            }
            // Registering current status
            isLoggedIn = false;
            mCurrentId = 0;
            mApiIds.clear();

            // If there is a response, we print it
            if(response != null){
                Log.e(TAG,"Response: "+response.message());
            }

            // Adding a very high latency value to this node in order to prevent
            // us from getting it again
            mSelectedNode.addLatencyValue(Long.MAX_VALUE);
            nodeProvider.updateNode(mSelectedNode);

            RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.DISCONNECTED, ApiAccess.API_NONE));

            if(nodeProvider.getBestNode() == null){
                Log.e(TAG,"Giving up on connections");
                stopSelf();
            }else{
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG,"Retrying");
                        connect();
                    }
                }, DEFAULT_RETRY_DELAY);
            }
            // Marking the selected node as not connected
            mSelectedNode.setConnected(false);

            // Updating the selected node's 'connected' status on the NodeLatencyVerifier instance
            if(nodeLatencyVerifier != null)
                nodeLatencyVerifier.updateActiveNodeInformation(mSelectedNode);

            // We have currently no selected node
            mSelectedNode = null;
        }
    };

    /**
     * Method used to check whether or not the network service is connected to a node that
     * offers a specific API.
     *
     * @param whichApi  The API we want to use.
     * @return          True if the node has got that API enabled, false otherwise
     */
    public boolean hasApiId(int whichApi){
        return mApiIds.get(whichApi) != null;
    }

    /**
     * Updates the full node details
     * @param fullNode  Updated {@link FullNode} instance
     */
    public void updateNode(FullNode fullNode){
        nodeProvider.updateNode(fullNode);
    }

    /**
     * Returns a list of {@link FullNode} instances
     * @return  List of full nodes
     */
    public List<FullNode> getNodes(){
        return nodeProvider.getSortedNodes();
    }

    /**
     * Returns an observable that will notify its observers about node latency updates.
     * @return  Observer of {@link FullNode} instances.
     */
    public PublishSubject<FullNode> getNodeLatencyObservable(){
        return fullNodePublishSubject;
    }
}

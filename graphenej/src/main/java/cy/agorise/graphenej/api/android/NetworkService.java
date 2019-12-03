package cy.agorise.graphenej.api.android;

import android.util.SparseArray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import cy.agorise.graphenej.api.ApiCallback;
import cy.agorise.graphenej.api.ConnectionStatusUpdate;
import cy.agorise.graphenej.api.calls.ApiCallable;
import cy.agorise.graphenej.api.calls.GetAccountBalances;
import cy.agorise.graphenej.api.calls.GetAccounts;
import cy.agorise.graphenej.api.calls.GetAssets;
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
import cy.agorise.graphenej.models.BitAssetData;
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
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Class in charge of maintaining a connection to the full node.
 */

public class NetworkService {
    public static final int NORMAL_CLOSURE_STATUS = 1000;
    private static final int GOING_AWAY_STATUS = 1001;

    // Time to wait before retrying a connection attempt
    private static final int DEFAULT_RETRY_DELAY = 500;

    // Default connection delay when using the node latency verification strategy. This initial
    // delay is required in order ot make sure we have a fair selection of node latencies from
    // which we can choose from.
    private final int DEFAULT_INITIAL_DELAY = 500;

    private WebSocket mWebSocket;

    // Username and password used to connect to a specific node
    private String mUsername;
    private String mPassword;

    private boolean isLoggedIn = false;

    private String mLastCall;
    private long mCurrentId = 0;

    // Requested APIs passed to this service
    private int mRequestedApis = ApiAccess.API_DATABASE | ApiAccess.API_HISTORY | ApiAccess.API_NETWORK_BROADCAST;

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

    private CompositeDisposable mCompositeDisposable;

    private HashMap<Long, ApiCallback> mCallbackMap = new HashMap<Long, ApiCallback>();

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
     * Singleton reference
     */
    private static NetworkService instance;

    /**
     * Private constructor
     */
    private NetworkService(){}

    /**
     * Thread-safe singleton getter.
     * @return  A NetworkService instance.
     */
    public static NetworkService getInstance(){
        if(instance == null) {
            synchronized (NetworkService.class) {
                if(instance == null) {
                    instance = new NetworkService();
                }
            }
        }
        return instance;
    }

    /**
     * Actually establishes a connection from this Service to one of the full nodes.
     */
    public void connect(){
        OkHttpClient client = new OkHttpClient
                .Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        synchronized (mWebSocketListener){
            mSelectedNode = nodeProvider.getBestNode();
            if(mSelectedNode != null){
                System.out.println("Trying to connect to: "+ mSelectedNode.getUrl());
                Request request = new Request.Builder().url(mSelectedNode.getUrl()).build();
                mWebSocket = client.newWebSocket(request, mWebSocketListener);
            }else{
                System.out.println("Could not find best node, reescheduling");
                // If no node could be found yet, schedule a new attempt in DEFAULT_INITIAL_DELAY ms
                Disposable d = Observable.timer(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS).subscribe(mConnectAttempt);
                mCompositeDisposable.add(d);
            }
        }
    }

    public long sendMessage(String message){
        if(mWebSocket != null){
            if(mWebSocket.send(message)){
                System.out.println("-> " + message);
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
    public synchronized long sendMessage(ApiCallable apiCallable, int requiredApi){
        if(requiredApi != -1 && mApiIds.containsKey(requiredApi) || requiredApi == ApiAccess.API_NONE){
            int apiId = 0;
            if(requiredApi != ApiAccess.API_NONE)
                apiId = mApiIds.get(requiredApi);
            ApiCall call = apiCallable.toApiCall(apiId, ++mCurrentId);
            mRequestClassMap.put(mCurrentId, apiCallable.getClass());
            if(mWebSocket != null && mWebSocket.send(call.toJsonString())){
                System.out.println("-> "+call.toJsonString());
                return mCurrentId;
            }
        }
        return -1;
    }

    public synchronized long sendMessage(ApiCallable apiCallable, int requiredApi, ApiCallback callback){
        long id = this.sendMessage(apiCallable, requiredApi);
        if(callback != null){
            if(id != -1){
                mCallbackMap.put(id, callback);
            }else{
                callback.onFailure(new Exception("Message could not be sent"), null);
            }
        }
        return id;
    }

    /**
     * Method used to inform any external party a clue about the current connectivity status
     * @return  True if the service is currently connected and logged in, false otherwise.
     */
    public boolean isConnected(){
        return mWebSocket != null && isLoggedIn;
    }

    /**
     * Stops the service by closing the connection and stopping the latency verifier.
     */
    public void stop() {
        if(mWebSocket != null)
            mWebSocket.close(NORMAL_CLOSURE_STATUS, null);

        if(nodeLatencyVerifier != null)
            nodeLatencyVerifier.stop();

        mCompositeDisposable.dispose();
        mCallbackMap.clear();
    }

    /**
     * Starts the connection
     */
    public void start(String[] urls, double alpha) {
        mCompositeDisposable = new CompositeDisposable();

        // Retrieving credentials and requested API data from the shared preferences
        mUsername = "";
        mPassword = "";

        if(urls == null || urls.length == 0){
            throw new MissingResourceException("Expecting at least a node URL to be provided", String.class.getName(), "urls");
        }

        // Feeding all node information to the NodeProvider instance
        for(String nodeUrl : urls){
            nodeProvider.addNode(new FullNode(nodeUrl));
        }

        ArrayList<FullNode> fullNodes = new ArrayList<>();
        for(String url : urls){
            fullNodes.add(new FullNode(url, alpha));
        }
        nodeLatencyVerifier = new NodeLatencyVerifier(fullNodes);
        fullNodePublishSubject = nodeLatencyVerifier.start();
        fullNodePublishSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(nodeLatencyObserver);

        Disposable d = Observable.timer(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS).subscribe(mConnectAttempt);
        mCompositeDisposable.add(d);
    }

    /**
     * Used to close the current connection and cause the service to attempt a reconnection.
     */
    public void reconnectNode() {
        mWebSocket.close(GOING_AWAY_STATUS, null);
    }

    /**
     * Consumer that will perform a connection attempt with the best node after DEFAULT_INITIAL_DELAY
     * milliseconds. This is used only if the node latency verification is activated.
     *
     * The reason to delay the initial connection is that we want to ideally connect to the best node,
     * meaning the one that offers the lowest latency value. But we have to give some time for the
     * first node latency measurement round to finish in order to have at least a partial result set
     * that could be used.
     */
    private Consumer mConnectAttempt = new Consumer() {

        @Override
        public void accept(Object o) throws Exception {
            FullNode fullNode = nodeProvider.getBestNode();
            if(fullNode != null){
                System.out.println(String.format(Locale.ROOT, "Connected with %d latency results", latencyUpdateCounter));
                mApiIds.clear();
                connect();
            }else{
                Disposable d = Observable.timer(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS).subscribe(this);
                mCompositeDisposable.add(d);
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
            System.out.println("nodeLatencyObserver.onError.Msg: "+e.getMessage());
        }

        @Override
        public void onComplete() { }
    };

    /**
     * Method used to execute every callback failure method and remove them from the SparseArray.
     *
     * @param throwable
     * @param response
     */
    private void resetCallbacks(Throwable throwable, Response response){
        for(ApiCallback callback : mCallbackMap.values()) {
            if(callback != null) {
                callback.onFailure(throwable, response);
                mCallbackMap.remove(callback);
            }
        }
    }

    private WebSocketListener mWebSocketListener = new WebSocketListener() {

        @Override
        public synchronized void onOpen(WebSocket webSocket, Response response) {
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
        public synchronized void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            System.out.println("<- "+text);
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
                    System.out.println("Error.Msg: "+response.error.message);
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
                }else if(responsePayloadClass == Transaction.class){
                    Type GetTransactionClass = new TypeToken<JsonRpcResponse<Transaction>>(){}.getType();
                    parsedResponse = gson.fromJson(text, GetTransactionClass);
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
                    } else if(requestClass == GetAssets.class){
                        Type GetAssetsResponse = new TypeToken<JsonRpcResponse<List<Asset>>>(){}.getType();
                        parsedResponse = gson.fromJson(text, GetAssetsResponse);
                    }else {
                        System.out.println("Unknown request class");
                    }
                }else{
                    System.out.println("Unhandled situation");
                }
            }

            // In case the parsedResponse instance is null, we fall back to the raw response
            if(parsedResponse == null){
                parsedResponse = response;
            }

            // Executing callback, if present with the parsed response
            if(mCallbackMap.containsKey(response.id)){
                ApiCallback callback = mCallbackMap.get(response.id);
                if(response.error == null)
                    callback.onResponse(parsedResponse, text);
                else
                    callback.onFailure(new Exception("Exception while trying to parse node response. Message: " + response.error.message), null);
                mCallbackMap.remove(response.id);
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
            //TODO: Add support for other types of 'get_objects' request types
            Gson gson = mDeserializationMap.getGson(GetObjects.class);
            Type GetBitAssetResponse = new TypeToken<JsonRpcResponse<List<BitAssetData>>>(){}.getType();
            return gson.fromJson(response, GetBitAssetResponse);
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
            resetCallbacks(new Exception("Websocket closed. Reason: " + reason), null);
            if(code == GOING_AWAY_STATUS)
                handleWebSocketDisconnection(true, false);
            else
                handleWebSocketDisconnection(false, false);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            resetCallbacks(t, response);
            System.out.println("onFailure. Exception: "+t.getClass().getName()+", Msg: "+t.getMessage());
            // Logging error stack trace
            for(StackTraceElement element : t.getStackTrace()){
                System.out.println(String.format("%s#%s:%s", element.getClassName(), element.getMethodName(), element.getLineNumber()));
            }

            // If there is a response, we print it
            if(response != null){
                System.out.println("Response: "+response.message());
            }

            handleWebSocketDisconnection(true, true);
        }

        /**
         * Method that encapsulates the behavior of handling a disconnection to the current node, and
         * potentially tries to reconnect to another one.
         *
         * @param tryReconnection       States if a reconnection to other node should be tried.
         * @param penalizeNode          Whether or not to penalize the current node with a very high latency reading.
         */
        private synchronized void handleWebSocketDisconnection(boolean tryReconnection, boolean penalizeNode) {
            System.out.println("handleWebSocketDisconnection. try reconnection: " + tryReconnection + ", penalizeNode: " + penalizeNode);
            RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.DISCONNECTED, ApiAccess.API_NONE));
            isLoggedIn = false;

            // Clearing previous request id to class mappings
            mRequestClassMap.clear();

            if(mSelectedNode != null){
                // Marking the selected node as not connected
                mSelectedNode.setConnected(false);

                // Updating the selected node's 'connected' status on the NodeLatencyVerifier instance
                if(nodeLatencyVerifier != null)
                    nodeLatencyVerifier.updateActiveNodeInformation(mSelectedNode);

                if (penalizeNode){
                    // Adding a very high latency value to this node in order to prevent
                    // us from getting it again
                    mSelectedNode.addLatencyValue(Long.MAX_VALUE);
                    nodeProvider.updateNode(mSelectedNode);
                }
            }

            if(tryReconnection) {
                // Registering current status
                mCurrentId = 0;
                mApiIds.clear();

                RxBus.getBusInstance().send(new ConnectionStatusUpdate(ConnectionStatusUpdate.DISCONNECTED, ApiAccess.API_NONE));

                if (nodeProvider.getBestNode() == null) {
                    System.out.println( "Giving up on connections");
                } else {
                    Disposable d = Observable.timer(DEFAULT_RETRY_DELAY, TimeUnit.MILLISECONDS).subscribe(new Consumer<Long>() {
                        @Override
                        public void accept(Long aLong) throws Exception {
                            connect();
                        }
                    });
                    mCompositeDisposable.add(d);
                }
            }

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
     * Returns the currently selected node
     */
    public FullNode getSelectedNode() { return mSelectedNode; }

    /**
     * Returns an observable that will notify its observers about node latency updates.
     * @return  Observer of {@link FullNode} instances.
     */
    public PublishSubject<FullNode> getNodeLatencyObservable(){
        return fullNodePublishSubject;
    }

    public NodeLatencyVerifier getNodeLatencyVerifier(){ return nodeLatencyVerifier; }
}

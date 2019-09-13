package cy.agorise.labs.sample.network;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import cy.agorise.graphenej.api.android.NetworkService;
import cy.agorise.graphenej.stats.ExponentialMovingAverage;

public class NetworkServiceManager implements Application.ActivityLifecycleCallbacks {
    private final String TAG = this.getClass().getName();

    /**
     * Constant used to specify how long will the app wait for another activity to go through its starting life
     * cycle events before running the teardownConnectionTask task.
     *
     * This is used as a means to detect whether or not the user has left the app.
     */
    private static final int DISCONNECT_DELAY = 1500;

    /**
     * Handler instance used to schedule tasks back to the main thread
     */
    private Handler mHandler = new Handler();

    private NetworkService mNetworkService;

    private String[] mNodeUrls;

    public NetworkServiceManager(String[] nodes){
        this.mNodeUrls = nodes;
    }

    /**
     * Runnable used to schedule a service disconnection once the app is not visible to the user for
     * more than DISCONNECT_DELAY milliseconds.
     */
    private final Runnable mDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            mNetworkService.stop();
            mNetworkService = null;
        }
    };

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) { }

    @Override
    public void onActivityStarted(Activity activity) { }

    @Override
    public void onActivityResumed(Activity activity) {
        mHandler.removeCallbacks(mDisconnectRunnable);
        if(mNetworkService == null) {
            mNetworkService = NetworkService.getInstance();
            mNetworkService.start(this.mNodeUrls, ExponentialMovingAverage.DEFAULT_ALPHA);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mHandler.postDelayed(mDisconnectRunnable, DISCONNECT_DELAY);
    }

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}

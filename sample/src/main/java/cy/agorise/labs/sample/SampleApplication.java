package cy.agorise.labs.sample;

import android.app.Application;

import cy.agorise.labs.sample.network.NetworkServiceManager;

/**
 * Sample application class
 */

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        String[] nodeURLs = new String[]{
                "wss://bitshares.openledger.info/ws",
                "wss://us.nodes.bitshares.ws",
                "wss://eu.nodes.bitshares.ws",
                "wss://citadel.li/node",
                "wss://api.bts.mobi/ws"
        };

        NetworkServiceManager networkManager = new NetworkServiceManager(nodeURLs);

        // Registering this class as a listener to all activity's callback cycle events, in order to
        // better estimate when the user has left the app and it is safe to disconnect the websocket connection
        registerActivityLifecycleCallbacks(networkManager);
    }
}

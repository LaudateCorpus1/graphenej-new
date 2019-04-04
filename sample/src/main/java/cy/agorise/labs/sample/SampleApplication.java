package cy.agorise.labs.sample;

import android.app.Application;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import cy.agorise.graphenej.api.ApiAccess;
import cy.agorise.graphenej.api.android.NetworkServiceManager;

/**
 * Sample application class
 */

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Specifying some important information regarding the connection, such as the
        // credentials and the requested API accesses
        int requestedApis = ApiAccess.API_DATABASE | ApiAccess.API_HISTORY | ApiAccess.API_NETWORK_BROADCAST;

        String[] nodeURLs = new String[]{
                "wss://bitshares.openledger.info/ws",
                "wss://us.nodes.bitshares.ws",
                "wss://eu.nodes.bitshares.ws"
        };
        List<String> nodeList = Arrays.asList(nodeURLs);
        String nodes = join(nodeList, ",");

        NetworkServiceManager networkManager = new NetworkServiceManager.Builder()
                .setUserName("username")
                .setPassword("secret")
                .setRequestedApis(requestedApis)
                .setCustomNodeUrls(nodes)
                .setAutoConnect(true)
                .setNodeLatencyVerification(true)
                .setLatencyAverageAlpha(0.1f)
                .build(this);

        // Registering this class as a listener to all activity's callback cycle events, in order to
        // better estimate when the user has left the app and it is safe to disconnect the websocket connection
        registerActivityLifecycleCallbacks(networkManager);
    }

    /**
     * Private method used to join a sequence of Strings given a iterable representation
     * and a delimiter.
     *
     *
     * @param s         Any collection of CharSequence that implements the Iterable interface.
     * @param delimiter The delimiter which will be used to join the different strings together.
     * @return          A single string combining all the iterable pieces with the delimiter.
     */
    private String join(Iterable<? extends CharSequence> s, String delimiter) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) return "";
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) buffer.append(delimiter).append(iter.next());
        return buffer.toString();
    }
}

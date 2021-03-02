package cy.agorise.graphenej;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import cy.agorise.graphenej.network.FullNode;
import cy.agorise.graphenej.network.LatencyNodeProvider;
import cy.agorise.graphenej.network.NodeLatencyVerifier;
import cy.agorise.graphenej.network.NodeProvider;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

@RunWith(AndroidJUnit4.class)
public class NodeLatencyVerifierTest {
    private final String TAG = this.getClass().getName();

    String[] nodeURLs = new String[]{
            "wss://bitshares.openledger.info/ws",
            "wss://us.nodes.bitshares.ws",
            "wss://eu.nodes.bitshares.ws",
            "wss://citadel.li/node",
            "wss://api.bts.mobi/ws"
    };

    @Test
    public void testNodeLatencyTest() throws Exception {
        ArrayList<FullNode> nodeList = new ArrayList<>();
        nodeList.add(new FullNode(nodeURLs[0]));
        nodeList.add(new FullNode(nodeURLs[1]));
        nodeList.add(new FullNode(nodeURLs[2]));
        final NodeLatencyVerifier nodeLatencyVerifier = new NodeLatencyVerifier(nodeList);
        PublishSubject subject = nodeLatencyVerifier.start();
        final NodeProvider nodeProvider = new LatencyNodeProvider();
        subject.subscribe(new Observer<FullNode>() {
            int counter = 0;

            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(FullNode fullNode) {
                Log.i(TAG,String.format("Avg latency: %.2f, url: %s", fullNode.getLatencyValue(), fullNode.getUrl()));

                // Updating node provider
                nodeProvider.updateNode(fullNode);
                List<FullNode> sortedNodes = nodeProvider.getSortedNodes();
                for(FullNode node : sortedNodes){
                    Log.d(TAG,String.format("> %.2f, url: %s", node.getLatencyValue(), node.getUrl()));
                }

                // Finish test after certain amount of rounds
                if(counter > 3){
                    synchronized (NodeLatencyVerifierTest.this){
                        nodeLatencyVerifier.stop();
                        NodeLatencyVerifierTest.this.notifyAll();
                    }
                }

                counter++;
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG,"onError.Msg: "+e.getMessage());
                synchronized (NodeLatencyVerifierTest.this){
                    NodeLatencyVerifierTest.this.notifyAll();
                }
            }

            @Override
            public void onComplete() {
                Log.d(TAG,"onComplete");
            }
        });
        try {
            synchronized(this) {
                wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

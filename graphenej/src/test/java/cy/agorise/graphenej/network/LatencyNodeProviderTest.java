package cy.agorise.graphenej.network;

import junit.framework.Assert;

import org.junit.Test;

import java.util.List;

public class LatencyNodeProviderTest {
    private FullNode nodeA, nodeB, nodeC;
    private LatencyNodeProvider latencyNodeProvider;

    private void setupTestNodes(){
        // Creating 3 nodes with different latencies
        nodeA = new FullNode("wss://nodeA");
        nodeB = new FullNode("wss://nodeB");
        nodeC = new FullNode("wss://nodeC");

        // Adding latencies measurements
        nodeA.addLatencyValue(100);
        nodeB.addLatencyValue(50);
        nodeC.addLatencyValue(20);

        // Creating a node provider and adding the nodes created previously
        latencyNodeProvider = new LatencyNodeProvider();
        latencyNodeProvider.addNode(nodeC);
        latencyNodeProvider.addNode(nodeA);
        latencyNodeProvider.addNode(nodeB);
    }

    @Test
    public void testSortedList(){
        setupTestNodes();

        // Confirming that the getSortedNodes gives us a sorted list of nodes in increasing latency order
        List<FullNode> fullNodeList = latencyNodeProvider.getSortedNodes();
        Assert.assertSame(nodeC, fullNodeList.get(0));
        Assert.assertSame(nodeB, fullNodeList.get(1));
        Assert.assertSame(nodeA, fullNodeList.get(2));

        // Adding more nodes with different latencies measurements
        FullNode nodeD = new FullNode("wss://nodeD");
        FullNode nodeE = new FullNode("wss://nodeE");
        FullNode nodeF = new FullNode("wss://nodef");

        // Adding latencies measurements
        nodeD.addLatencyValue(900);
        nodeE.addLatencyValue(1);
        nodeF.addLatencyValue(1500);

        // Updating the LatencyNodeProvider
        latencyNodeProvider.updateNode(nodeD);
        latencyNodeProvider.updateNode(nodeE);
        latencyNodeProvider.updateNode(nodeF);

        FullNode bestNode = latencyNodeProvider.getBestNode();
        // Checking for best node
        Assert.assertSame("Verifying that the nodeE is the best now", nodeE, bestNode);
        fullNodeList = latencyNodeProvider.getSortedNodes();
        FullNode worstNode = fullNodeList.get(fullNodeList.size() - 1);
        // Checking for worst node
        Assert.assertSame("Verifying that the nodeF is the worst now", nodeF, worstNode);
    }

    @Test
    public void testScoreUpdate(){
        setupTestNodes();

        // Confirming that the best node is nodeC
        FullNode bestNode = latencyNodeProvider.getBestNode();
        Assert.assertSame("Check that the best node is nodeC", nodeC, bestNode);

        // Improving nodeA score by feeding it with new better latency measurements
        latencyNodeProvider.updateNode(nodeA, 10);
        latencyNodeProvider.updateNode(nodeA, 10);
        latencyNodeProvider.updateNode(nodeA, 10);
        latencyNodeProvider.updateNode(nodeA, 10);

        // Updating the nodeA position in the provider
        latencyNodeProvider.updateNode(nodeA);
        bestNode = latencyNodeProvider.getBestNode();
        System.out.println("Best node latency after update: "+bestNode.getLatencyValue());
        Assert.assertSame("Check that the best node now is the nodeA", nodeA, bestNode);
    }

    @Test
    public void testLargeNumbers(){
        setupTestNodes();
        nodeA.addLatencyValue(Long.MAX_VALUE);
        latencyNodeProvider.updateNode(nodeA);
        FullNode best = latencyNodeProvider.getBestNode();
        Assert.assertSame(nodeC, best);
    }
}

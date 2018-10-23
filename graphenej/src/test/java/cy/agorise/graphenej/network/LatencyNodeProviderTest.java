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

    @Test
    public void realisticSituationTest(){
        FullNode node1 = new FullNode("wss://fi.bts.dcn.cx/ws");
        FullNode node2 = new FullNode("wss://mx.palmpay.io/ws");
        FullNode node3 = new FullNode("wss://miami.bitshares.apasia.tech/ws");
        FullNode node4 = new FullNode("wss://valley.bitshares.apasia.tech/ws");
        FullNode node5 = new FullNode("wss://atlanta.bitshares.apasia.tech/ws");
        FullNode node6 = new FullNode("wss://dallas.bitshares.apasia.tech/ws");
        FullNode node7 = new FullNode("wss://eu-west-2.bts.crypto-bridge.org");
        FullNode node8 = new FullNode("wss://england.bitshares.apasia.tech/ws");
        FullNode node9 = new FullNode("wss://eu-west-1.bts.crypto-bridge.org");
        FullNode node11 = new FullNode("wss://netherlands.bitshares.apasia.tech/ws");
        FullNode node12 = new FullNode("wss://api.bts.blckchnd.com");
        FullNode node13 = new FullNode("wss://bitshares.nu/ws");
        FullNode node14 = new FullNode("wss://bitshares.openledger.info/ws");
        FullNode node15 = new FullNode("wss://citadel.li/node");
        FullNode node16 = new FullNode("wss://api-ru.bts.blckchnd.com");
        FullNode node17 = new FullNode("wss://dex.rnglab.org");
        FullNode node18 = new FullNode("wss://nl.palmpay.io/ws");
        FullNode node19 = new FullNode("wss://bitshares.crypto.fans/ws");
        FullNode node20 = new FullNode("wss://bit.btsabc.org/ws");

        LatencyNodeProvider provider = new LatencyNodeProvider();
        provider.addNode(node1);
        provider.addNode(node2);
        provider.addNode(node3);
        provider.addNode(node4);
        provider.addNode(node5);
        provider.addNode(node6);
        provider.addNode(node7);
        provider.addNode(node8);
        provider.addNode(node9);
        provider.addNode(node11);
        provider.addNode(node12);
        provider.addNode(node13);
        provider.addNode(node14);
        provider.addNode(node15);
        provider.addNode(node16);
        provider.addNode(node17);
        provider.addNode(node18);
        provider.addNode(node19);
        provider.addNode(node20);

        node3.addLatencyValue(458.41);
        node4.addLatencyValue(458.40);
        node5.addLatencyValue(620.12);
        node6.addLatencyValue(682.64);
        node7.addLatencyValue(842.88);
        node8.addLatencyValue(842.05);
        node9.addLatencyValue(911.38);
        node11.addLatencyValue(930.58);
        node12.addLatencyValue(1002.27);
        node13.addLatencyValue(1069.96);
        node14.addLatencyValue(1060.20);
        node15.addLatencyValue(1025.14);
        node16.addLatencyValue(1060.55);
        node17.addLatencyValue(1001.44);
        node18.addLatencyValue(1036.69);
        node19.addLatencyValue(1047.19);
        node20.addLatencyValue(1286.89);

        provider.updateNode(node1);
        provider.updateNode(node2);
        provider.updateNode(node3);
        provider.updateNode(node4);
        provider.updateNode(node5);
        provider.updateNode(node6);
        provider.updateNode(node7);
        provider.updateNode(node8);
        provider.updateNode(node9);
        provider.updateNode(node11);
        provider.updateNode(node12);
        provider.updateNode(node13);
        provider.updateNode(node14);
        provider.updateNode(node15);
        provider.updateNode(node16);
        provider.updateNode(node17);
        provider.updateNode(node18);
        provider.updateNode(node19);
        provider.updateNode(node20);

        FullNode best = provider.getBestNode();
        Assert.assertSame("Expects node4 to be the best", node4, best);
    }
}

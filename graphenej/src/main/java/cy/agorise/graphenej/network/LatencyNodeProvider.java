package cy.agorise.graphenej.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

public class LatencyNodeProvider implements NodeProvider {

    private PriorityBlockingQueue<FullNode> mFullNodeHeap;

    public LatencyNodeProvider(){
        mFullNodeHeap = new PriorityBlockingQueue<>();
    }

    @Override
    public FullNode getBestNode() {
        return mFullNodeHeap.peek();
    }

    @Override
    public void addNode(FullNode fullNode) {
        mFullNodeHeap.add(fullNode);
    }

    @Override
    public boolean updateNode(FullNode fullNode) {
        boolean existed = mFullNodeHeap.remove(fullNode);
        if(existed){
            return mFullNodeHeap.offer(fullNode);
        }
        return false;
    }

    /**
     * Updates an existing node with the new latency value.
     *
     * @param fullNode  Existing full node instance
     * @param latency   New latency measurement
     * @return          True if the node priority was updated successfully
     */
    public boolean updateNode(FullNode fullNode, int latency){
        boolean existed = mFullNodeHeap.remove(fullNode);
        if(existed){
            fullNode.addLatencyValue(latency);
            return mFullNodeHeap.add(fullNode);
        }
        return false;
    }

    @Override
    public void removeNode(FullNode fullNode) {
        mFullNodeHeap.remove(fullNode);
    }

    @Override
    public List<FullNode> getSortedNodes() {
        FullNode[] nodeArray = mFullNodeHeap.toArray(new FullNode[mFullNodeHeap.size()]);
        ArrayList<FullNode> nodeList = new ArrayList<>();
        for(FullNode fullNode : nodeArray){
            if(fullNode != null){
                nodeList.add(fullNode);
            }
        }
        Collections.sort(nodeList);
        return nodeList;
    }
}

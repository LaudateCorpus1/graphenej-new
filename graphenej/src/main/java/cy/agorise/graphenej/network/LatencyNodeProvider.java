package cy.agorise.graphenej.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class LatencyNodeProvider implements NodeProvider {
    private HashSet<String> mRemovedNodeURLs;

    private PriorityQueue<FullNode> mFullNodeHeap;

    public LatencyNodeProvider(){
        mFullNodeHeap = new PriorityQueue<>();
        mRemovedNodeURLs = new HashSet<>();
    }

    @Override
    public FullNode getBestNode() {
        return mFullNodeHeap.peek();
    }

    @Override
    public void addNode(FullNode fullNode) {
        mFullNodeHeap.add(fullNode);
        mRemovedNodeURLs.remove(fullNode.getUrl());
    }

    @Override
    public boolean updateNode(FullNode fullNode) {
        if (!mRemovedNodeURLs.contains(fullNode.getUrl())) {
            mFullNodeHeap.remove(fullNode);
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
        if(!mRemovedNodeURLs.contains(fullNode.getUrl()) && mFullNodeHeap.remove(fullNode)){
            fullNode.addLatencyValue(latency);
            return mFullNodeHeap.add(fullNode);
        }else{
            return false;
        }
    }

    @Override
    public void removeNode(FullNode fullNode) {
        if (mFullNodeHeap.remove(fullNode)) {
            mRemovedNodeURLs.add(fullNode.getUrl());
        }
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

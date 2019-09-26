package cy.agorise.labs.sample;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cy.agorise.graphenej.api.android.NetworkService;
import cy.agorise.graphenej.network.FullNode;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

public class RemoveNodeActivity extends ConnectedActivity {

    private final String TAG = this.getClass().getName();

    @BindView(R.id.rvNodes)
    RecyclerView rvNodes;

    FullNodesAdapter nodesAdapter;

    // Comparator used to sort the nodes in ascending order
    private final Comparator<FullNode> LATENCY_COMPARATOR = (a, b) ->
            Double.compare(a.getLatencyValue(), b.getLatencyValue());

    /* Network service connection */
    private NetworkService mNetworkService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remove_node);

        ButterKnife.bind(this);

        rvNodes.setLayoutManager(new LinearLayoutManager(this));
        nodesAdapter = new FullNodesAdapter(this, LATENCY_COMPARATOR);
        rvNodes.setAdapter(nodesAdapter);

        if(mNetworkService != null){
            // PublishSubject used to announce full node latencies updates
            PublishSubject<FullNode> fullNodePublishSubject = mNetworkService.getNodeLatencyObservable();
            if(fullNodePublishSubject != null)
                fullNodePublishSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(nodeLatencyObserver);

            List<FullNode> fullNodes = mNetworkService.getNodes();
            nodesAdapter.add(fullNodes);
        }
    }

    @OnClick(R.id.btnReconnectNode)
    public void removeCurrentNode() {
        mNetworkService.reconnectNode();
    }

    /**
     * Observer used to be notified about node latency measurement updates.
     */
    private Observer<FullNode> nodeLatencyObserver = new Observer<FullNode>() {
        @Override
        public void onSubscribe(Disposable d) { }

        @Override
        public void onNext(FullNode fullNode) {
            if (!fullNode.isRemoved())
                nodesAdapter.add(fullNode);
            else
                nodesAdapter.remove(fullNode);
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG,"nodeLatencyObserver.onError.Msg: "+e.getMessage());
        }

        @Override
        public void onComplete() { }
    };

    class FullNodesAdapter extends RecyclerView.Adapter<FullNodesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView   ivNodeStatus;
            TextView    tvNodeName;

            ViewHolder(View itemView) {
                super(itemView);

                ivNodeStatus    = itemView.findViewById(R.id.ivNodeStatus);
                tvNodeName      = itemView.findViewById(R.id.tvNodeName);
            }
        }

        private final SortedList<FullNode> mSortedList = new SortedList<>(FullNode.class, new SortedList.Callback<FullNode>() {
            @Override
            public void onInserted(int position, int count) {
                notifyItemRangeInserted(position, count);
            }

            @Override
            public void onRemoved(int position, int count) {
                notifyItemRangeRemoved(position, count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                notifyItemMoved(fromPosition, toPosition);
            }

            @Override
            public void onChanged(int position, int count) {
                notifyItemRangeChanged(position, count);
            }

            @Override
            public int compare(FullNode a, FullNode b) {
                return mComparator.compare(a, b);
            }

            @Override
            public boolean areContentsTheSame(FullNode oldItem, FullNode newItem) {
                return oldItem.getLatencyValue() == newItem.getLatencyValue();
            }

            @Override
            public boolean areItemsTheSame(FullNode item1, FullNode item2) {
                return item1.getUrl().equals(item2.getUrl());
            }
        });

        private final Comparator<FullNode> mComparator;

        private Context mContext;

        FullNodesAdapter(Context context, Comparator<FullNode> comparator) {
            mContext = context;
            mComparator = comparator;
        }

        private Context getContext() {
            return mContext;
        }

        @NonNull
        @Override
        public FullNodesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            View transactionView = inflater.inflate(R.layout.item_node, parent, false);

            return new ViewHolder(transactionView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            final FullNode fullNode = mSortedList.get(position);

            // Show the green check mark before the node name if that node is the one being used
            if (fullNode.isConnected())
                viewHolder.ivNodeStatus.setImageResource(R.drawable.ic_connected);
            else
                viewHolder.ivNodeStatus.setImageDrawable(null);

            double latency = fullNode.getLatencyValue();

            // Select correct color span according to the latency value
            ForegroundColorSpan colorSpan;

            if (latency < 400)
                colorSpan = new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.colorPrimary));
            else if (latency < 800)
                colorSpan = new ForegroundColorSpan(Color.rgb(255,136,0)); // Holo orange
            else
                colorSpan = new ForegroundColorSpan(Color.rgb(204,0,0)); // Holo red

            // Create a string with the latency number colored according to their amount
            SpannableStringBuilder ssb = new SpannableStringBuilder();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ssb.append(fullNode.getUrl().replace("wss://", ""), new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append(" (");

            // 2000 ms is the timeout of the websocket used to calculate the latency, therefore if the
            // received latency is greater than such value we can assume the node was not reachable.
            String ms = latency < 2000 ? String.format(Locale.US, "%.0f ms", latency) : "??";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ssb.append(ms, colorSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append(")");

            viewHolder.tvNodeName.setText(ssb);
        }

        /**
         * Functions that adds/updates a FullNode to the SortedList
         */
        public void add(FullNode fullNode) {
            // Remove the old instance of the FullNode before adding a new one. My understanding is that
            // the sorted list should be able to automatically find repeated elements and update them
            // instead of adding duplicates but it wasn't working so I opted for manually removing old
            // instances of FullNodes before adding the updated ones.
            int removed = 0;
            for (int i=0;  i<mSortedList.size(); i++)
            if (mSortedList.get(i - removed).getUrl().equals(fullNode.getUrl()))
                mSortedList.removeItemAt(i-removed++);

            mSortedList.add(fullNode);
        }

        /**
         * Function that adds a whole list of nodes to the SortedList. It should only be used at the
         * moment of populating the SortedList for the first time.
         */
        public void add(List<FullNode> fullNodes) {
            mSortedList.addAll(fullNodes);
        }

        public void remove(FullNode fullNode) {
            mSortedList.remove(fullNode);
        }

        @Override
        public int getItemCount() {
            return mSortedList.size();
        }
    }
}

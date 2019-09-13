package cy.agorise.labs.sample;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cy.agorise.graphenej.api.android.RxBus;
import cy.agorise.graphenej.api.calls.CancelAllSubscriptions;
import cy.agorise.graphenej.api.calls.SetSubscribeCallback;
import cy.agorise.graphenej.models.JsonRpcNotification;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class SubscriptionActivity extends ConnectedActivity {

    private final String TAG = this.getClass().getName();

    @BindView(R.id.text_field)
    TextView mTextField;

    private Disposable mDisposable;

    // Notification counter
    private int counter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        ButterKnife.bind(this);

        mDisposable = RxBus.getBusInstance()
                .asFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {

                    @Override
                    public void accept(Object message) throws Exception {
                        if(message instanceof String){
                            Log.d(TAG,"Got text message: "+(message));
                            mTextField.setText(mTextField.getText() + ((String) message) + "\n");
                        }else if(message instanceof JsonRpcNotification){
                            counter++;
                            mTextField.setText(String.format("Got %d notifications so far", counter));
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisposable.dispose();
    }

    @OnClick(R.id.subscribe)
    public void onTransferFeeUsdClicked(View v){
        mNetworkService.sendMessage(new SetSubscribeCallback(true), SetSubscribeCallback.REQUIRED_API);
    }

    @OnClick(R.id.unsubscribe)
    public void onTransferFeeBtsClicked(View v){
        mNetworkService.sendMessage(new CancelAllSubscriptions(), CancelAllSubscriptions.REQUIRED_API);
    }
}

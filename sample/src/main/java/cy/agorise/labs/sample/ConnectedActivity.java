package cy.agorise.labs.sample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import cy.agorise.graphenej.api.android.NetworkService;

public abstract class ConnectedActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getName();

    /* Network service connection */
    protected NetworkService mNetworkService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNetworkService = NetworkService.getInstance();
    }
}

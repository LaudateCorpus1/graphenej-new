package cy.agorise.labs.sample;

import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cy.agorise.graphenej.BrainKey;

public class BrainkeyActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getName();
    @BindView(R.id.brainkey)
    TextInputEditText mBrainkeyView;

    @BindView(R.id.pubkey)
    TextInputEditText mDesiredPubKey;

    @BindView(R.id.pubkey_display)
    TextView mPubkeyDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brainkey);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.button_generate)
    public void onGenerateClicked(View v){
        String target = mDesiredPubKey.getText().toString();
        String brainkeyText = mBrainkeyView.getText().toString();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < 10; i++){
            BrainKey brainKey = new BrainKey(brainkeyText, i);
            builder.append(String.format(Locale.ROOT, "%d -> ", i))
                .append(brainKey.getPublicAddress("BTS").toString())
                .append("\n");
        }
        String derivationResult = builder.toString();
        mPubkeyDisplay.setText(derivationResult);
        if(!target.isEmpty() && derivationResult.contains(target)){
            int start = derivationResult.indexOf(target);
            SpannableStringBuilder sBuilder = new SpannableStringBuilder(derivationResult);
            sBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, (start + 53), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mPubkeyDisplay.setText(sBuilder);
        }
    }
}

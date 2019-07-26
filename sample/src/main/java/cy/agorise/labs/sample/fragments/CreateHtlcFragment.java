package cy.agorise.labs.sample.fragments;


import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cy.agorise.labs.sample.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class CreateHtlcFragment extends Fragment {
    private final String TAG = this.getClass().getName();

    @BindView(R.id.from)
    TextInputEditText fromField;

    @BindView(R.id.to)
    TextInputEditText toField;

    @BindView(R.id.amount)
    TextInputEditText amountField;

    @BindView(R.id.timelock)
    TextInputEditText timelockField;

    // Parent activity, which must implement the HtlcListener interface.
    private HtlcListener mListener;

    public CreateHtlcFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_htlc, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @OnClick(R.id.button_create)
    public void onSendClicked(View v){
        Log.d(TAG,"onSendClicked");
        String from = fromField.getText().toString();
        String to = toField.getText().toString();
        Double amount = null;
        Long timeLock = null;
        try{
            amount = Double.parseDouble(amountField.getText().toString());
        }catch(NumberFormatException e){
            amountField.setError("Invalid amount");
        }
        try{
            timeLock = Long.parseLong(timelockField.getText().toString());
        }catch(NumberFormatException e){
            timelockField.setError("Invalid value");
        }
        Log.d(TAG,"amount: " + amount + ", timelock: " + timeLock);
        if(amount != null && timeLock != null){
            Toast.makeText(getContext(), "Should be sending message up", Toast.LENGTH_SHORT).show();
            mListener.onHtlcProposal(from, to, amount, timeLock);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof HtlcListener){
            mListener = (HtlcListener) context;
        }else{
            throw new ClassCastException(context.toString() + " must implement the HtlcListener interface!");
        }
    }

    /**
     * Interface to be implemented by the parent activity.
     */
    public interface HtlcListener {
        /**
         * Method used to notify the parent activity of the request to create an HTLC with the following parameters.
         *
         * @param from Source account id.
         * @param to Destination account id.
         * @param amount The amount of BTS to propose the HTLC.
         * @param timelock The timelock in seconds.
         */
        void onHtlcProposal(String from, String to, Double amount, Long timelock);
    }
}

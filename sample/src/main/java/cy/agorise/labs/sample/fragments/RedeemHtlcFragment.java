package cy.agorise.labs.sample.fragments;


import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
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
public class RedeemHtlcFragment extends Fragment {

    @BindView(R.id.redeemer)
    TextInputEditText mRedeemer;

    @BindView(R.id.htlc_id)
    TextInputEditText mHtlcId;

    // Parent activity, which must implement the RedeemHtlcListener interface.
    private RedeemHtlcListener mListener;


    public RedeemHtlcFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_redeem_htlc, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof RedeemHtlcListener){
            mListener = (RedeemHtlcListener) context;
        }else{
            throw new ClassCastException(context.toString() + " must implement the RedeemHtlcListener interface!");
        }
    }

    @OnClick(R.id.button_create)
    public void onSendClicked(View v){
        String redeemerId = mRedeemer.getText().toString();
        String htlcId = mHtlcId.getText().toString();
        Toast.makeText(getContext(), "Should be sending message up", Toast.LENGTH_SHORT).show();
        mListener.onRedeemProposal(redeemerId, htlcId);
    }

    /**
     * Interface to be implemented by the parent activity.
     */
    public interface RedeemHtlcListener {
        /**
         * Method used to notify the parent activity about the creation of an HTLC redeem operation.
         *
         * @param userId The id of the user that wishes to redeem an HTLC.
         * @param htlcId The HTLC id.
         */
        void onRedeemProposal(String userId, String htlcId);
    }
}

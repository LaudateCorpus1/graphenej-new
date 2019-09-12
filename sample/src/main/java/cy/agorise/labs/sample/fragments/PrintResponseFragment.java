package cy.agorise.labs.sample.fragments;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import cy.agorise.labs.sample.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class PrintResponseFragment extends Fragment {


    public PrintResponseFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_print_response, container, false);
    }

}

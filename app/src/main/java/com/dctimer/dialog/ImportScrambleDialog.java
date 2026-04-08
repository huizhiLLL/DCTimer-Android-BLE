package com.dctimer.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.dctimer.R;
import com.dctimer.activity.MainActivity;

import static com.dctimer.APP.importType;

public class ImportScrambleDialog extends DialogFragment {
    public static ImportScrambleDialog newInstance() {
        return new ImportScrambleDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder buidler = new AlertDialog.Builder(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_scramble, null);
        Spinner spinner = view.findViewById(R.id.sp_type);
        spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                importType = position;
            }
            public void onNothingSelected(AdapterView<?> arg0) {}
        });
        buidler.setView(view).setTitle(R.string.action_import_scramble)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int i) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).requestScrambleImport();
                        }
                    }
                }).setNegativeButton(R.string.btn_cancel, null);
        return buidler.create();
    }
}

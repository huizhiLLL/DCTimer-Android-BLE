package com.dctimer.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.dctimer.R;
import com.dctimer.activity.MainActivity;

public class ExportScrambleDialog extends DialogFragment {
    private EditText editNumber;
    private EditText editName;
    private String scramble;

    public static ExportScrambleDialog newInstance(String scramble) {
        ExportScrambleDialog dialog = new ExportScrambleDialog();
        Bundle bundle = new Bundle();
        bundle.putString("scramble", scramble);
        dialog.setArguments(bundle);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        scramble = getArguments().getString("scramble", "");
        AlertDialog.Builder buidler = new AlertDialog.Builder(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_scramble, null);
        editNumber = view.findViewById(R.id.edit_scrnum);
        editNumber.setText("5");
        editNumber.setSelection(1);
        editName = view.findViewById(R.id.edit_scrfile);
        buidler.setView(view).setTitle(getString(R.string.action_export_scramble) + "(" + scramble + ")")
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int i) {
                        String str = editNumber.getText().toString();
                        if (TextUtils.isEmpty(str)) return;
                        int n = Integer.parseInt(str);
                        if (n > 500) n = 500;
                        else if (n < 1) n = 5;
                        String fileName = editName.getText().toString();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).requestScrambleExport(n, fileName);
                        }
                    }
                }).setNegativeButton(R.string.btn_cancel, null);
        return buidler.create();
    }
}

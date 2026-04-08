package com.dctimer.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.RadioGroup;

import com.dctimer.R;
import com.dctimer.activity.MainActivity;

public class ImportExportDialog extends DialogFragment {
    private RadioGroup radioGroup;

    public static ImportExportDialog newInstance() {
        return new ImportExportDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder buidler = new AlertDialog.Builder(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_export, null);
        radioGroup = view.findViewById(R.id.rgroup);
        buidler.setView(view).setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int i) {
                int rgid = radioGroup.getCheckedRadioButtonId();
                if (rgid == R.id.rbt_in) {  //导入数据库
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).requestDatabaseImport();
                } else if (rgid == R.id.rbt_out) {  //导出数据库
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).requestDatabaseExport();
                }
            }
        }).setNegativeButton(R.string.btn_cancel, null);
        return buidler.create();
    }
}

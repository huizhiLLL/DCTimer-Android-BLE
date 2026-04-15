package com.dctimer.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.dctimer.R;

import java.util.ArrayList;

public class BattleRoundDetailDialog extends DialogFragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_RULE = "rule";
    private static final String ARG_NAMES = "names";
    private static final String ARG_RESULTS = "results";
    private static final String ARG_COLORS = "colors";

    public static BattleRoundDetailDialog newInstance(String title, String rule,
                                                      ArrayList<String> names,
                                                      ArrayList<String> results,
                                                      ArrayList<Integer> colors) {
        BattleRoundDetailDialog dialog = new BattleRoundDetailDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_RULE, rule);
        args.putStringArrayList(ARG_NAMES, names);
        args.putStringArrayList(ARG_RESULTS, results);
        args.putIntegerArrayList(ARG_COLORS, colors);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.DialogTheme);
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_battle_round_detail, null);
        TextView tvTitle = view.findViewById(R.id.tv_detail_round_title);
        TextView tvRule = view.findViewById(R.id.tv_detail_round_rule);
        LinearLayout playerList = view.findViewById(R.id.ll_detail_player_list);

        Bundle args = getArguments();
        ArrayList<String> names = args == null ? new ArrayList<String>() : args.getStringArrayList(ARG_NAMES);
        ArrayList<String> results = args == null ? new ArrayList<String>() : args.getStringArrayList(ARG_RESULTS);
        ArrayList<Integer> colors = args == null ? new ArrayList<Integer>() : args.getIntegerArrayList(ARG_COLORS);

        tvTitle.setText(args == null ? "" : args.getString(ARG_TITLE, ""));
        tvRule.setText(args == null ? "" : args.getString(ARG_RULE, ""));

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        if (names != null && results != null) {
            for (int i = 0; i < Math.min(names.size(), results.size()); i++) {
                View item = inflater.inflate(R.layout.item_battle_round_player, playerList, false);
                TextView tvName = item.findViewById(R.id.tv_detail_player_name);
                TextView tvResult = item.findViewById(R.id.tv_detail_player_result);
                tvName.setText(names.get(i));
                tvResult.setText(results.get(i));
                if (colors != null && colors.size() > i) {
                    tvResult.setTextColor(colors.get(i));
                }
                playerList.addView(item);
                if (i < names.size() - 1) {
                    View divider = new View(getActivity());
                    divider.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xffdddddd);
                    playerList.addView(divider);
                }
            }
        }

        builder.setView(view);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }
}

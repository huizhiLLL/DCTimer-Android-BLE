package com.dctimer.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatTextView;

import com.dctimer.APP;
import com.dctimer.R;
import com.dctimer.dialog.BattleRoundDetailDialog;
import com.dctimer.model.BattleRepository;
import com.dctimer.model.BattleRoomUiModel;
import com.dctimer.model.MockBattleRepository;
import com.dctimer.util.Utils;
import com.dctimer.widget.CustomToolbar;

import java.util.ArrayList;
import java.util.List;

import static com.dctimer.APP.screenOri;
import static com.dctimer.APP.SCREEN_ORIENTATION;
import static com.dctimer.APP.timerFont;
import static com.dctimer.APP.timerSize;

public class BattleRoomActivity extends AppCompatActivity {
    public static final String EXTRA_ROOM_ID = "battle_room_id";

    private TextView tvRoomName;
    private TextView tvRoomMode;
    private TextView tvRoomRound;
    private AppCompatTextView tvRoomScramble;
    private TextView tvRoomTimer;
    private TextView tvRoomStatus;
    private TextView tvRoomSummaryTimes;
    private LinearLayout llRoomSummary;
    private final BattleRepository battleRepository = MockBattleRepository.getInstance();
    private BattleRoomUiModel roomUiModel;
    private int uiMode;

    public static Intent newIntent(Context context, String roomId) {
        Intent intent = new Intent(context, BattleRoomActivity.class);
        intent.putExtra(EXTRA_ROOM_ID, roomId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        setContentView(R.layout.activity_battle_room);
        uiMode = getResources().getConfiguration().uiMode;

        LinearLayout layout = findViewById(R.id.layout_battle_room);
        layout.setBackgroundColor(APP.getBackgroundColor());
        int gray = Utils.grayScale(APP.getBackgroundColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int visibility = gray > 200 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && gray > 200) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(visibility);
            window.setStatusBarColor(APP.getBackgroundColor());
            window.setNavigationBarColor(APP.getBackgroundColor());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (gray > 200) {
                window.setStatusBarColor(0x44000000);
            } else {
                window.setStatusBarColor(APP.getBackgroundColor());
            }
            window.setNavigationBarColor(APP.getBackgroundColor());
        }

        CustomToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.action_battle);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(APP.getBackgroundColor());
        toolbar.setItemColor(APP.getTextColor());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        tvRoomName = findViewById(R.id.tv_room_name);
        tvRoomMode = findViewById(R.id.tv_room_mode);
        tvRoomRound = findViewById(R.id.tv_room_round);
        tvRoomScramble = findViewById(R.id.tv_room_scramble);
        tvRoomTimer = findViewById(R.id.tv_room_timer);
        tvRoomStatus = findViewById(R.id.tv_room_status);
        tvRoomSummaryTimes = findViewById(R.id.tv_room_summary_times);
        llRoomSummary = findViewById(R.id.ll_room_summary);

        int textColor = APP.getTextColor();
        tvRoomName.setTextColor(textColor);
        tvRoomScramble.setTextColor(textColor);
        tvRoomTimer.setTextColor(textColor);

        tvRoomTimer.setTextSize(timerSize);
        applyTimerTypeface();
        
        // 应用全局打乱文字配置
        tvRoomScramble.setTextSize(APP.scrambleSize);
        if (APP.monoFont) {
            tvRoomScramble.setTypeface(Typeface.MONOSPACE);
        }
        
        bindRoomUiModel();

        llRoomSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showRoundDetail();
            }
        });

        setRequestedOrientation(SCREEN_ORIENTATION[screenOri]);
    }

    private void bindRoomUiModel() {
        String roomId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_ROOM_ID);
        roomUiModel = battleRepository.getRoomUiModel(roomId);
        tvRoomName.setText(roomUiModel.getRoomName());
        tvRoomMode.setText(roomUiModel.getRoomModeText());
        tvRoomRound.setText(roomUiModel.getRoomRoundText());
        tvRoomScramble.setText(roomUiModel.getScrambleText());
        tvRoomTimer.setText(roomUiModel.getTimerText());
        tvRoomStatus.setText(roomUiModel.getStatusText());
        tvRoomSummaryTimes.setText(roomUiModel.getSummaryText());
    }

    private void showRoundDetail() {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> results = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        List<BattleRoomUiModel.RoundPlayerItem> players =
                roomUiModel == null ? null : roomUiModel.getRoundPlayers();
        if (players != null) {
            for (BattleRoomUiModel.RoundPlayerItem player : players) {
                names.add(player.getPlayerName());
                results.add(player.getResultText());
                colors.add(player.getResultColor());
            }
        }

        BattleRoundDetailDialog.newInstance(
                roomUiModel == null ? "" : roomUiModel.getDetailTitle(),
                roomUiModel == null ? "" : roomUiModel.getDetailRule(),
                names,
                results,
                colors
        ).show(getSupportFragmentManager(), "BattleRoundDetail");
    }

    private void applyTimerTypeface() {
        Typeface timerTypeface = null;
        switch (timerFont) {
            case 0:
                timerTypeface = Typeface.create("monospace", Typeface.NORMAL);
                break;
            case 1:
                timerTypeface = Typeface.create("serif", Typeface.NORMAL);
                break;
            case 2:
                timerTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
                break;
            case 3:
                timerTypeface = Typeface.createFromAsset(getAssets(), "Ds.ttf");
                break;
            case 4:
                timerTypeface = Typeface.createFromAsset(getAssets(), "Df.ttf");
                break;
            case 5:
                timerTypeface = Typeface.createFromAsset(getAssets(), "lcd.ttf");
                break;
        }
        if (timerTypeface != null) {
            tvRoomTimer.setTypeface(timerTypeface);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode;
            if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            recreate();
        }
    }
}

package com.dctimer.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.*;
import android.widget.*;

import com.dctimer.APP;
import com.dctimer.R;
import com.dctimer.adapter.StatAdapter;
import com.dctimer.util.Utils;
import com.dctimer.widget.CustomToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static com.dctimer.APP.*;

public class DetailActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT_STAT = 1001;

    private EditText et2;
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    //private ProgressBar progress;
    private RecyclerView rvStat;
    private StatAdapter stAdapter;
    private int uiMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {    //5.0
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        setContentView(R.layout.activity_detail);

        LinearLayout layout = findViewById(R.id.layout);
        layout.setBackgroundColor(APP.getBackgroundColor());
        int gray = Utils.grayScale(APP.getBackgroundColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   //6.0
            int visibility = gray > 200 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && gray > 200) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(visibility);
            window.setStatusBarColor(APP.getBackgroundColor());
            window.setNavigationBarColor(APP.getBackgroundColor());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { //5.0
            if (gray > 200) {
                window.setStatusBarColor(0x44000000);
            } else {
                window.setStatusBarColor(APP.getBackgroundColor());
            }
            window.setNavigationBarColor(APP.getBackgroundColor());
        }

        CustomToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(APP.getBackgroundColor());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        toolbar.setItemColor(APP.getTextColor());

        rvStat = findViewById(R.id.rv_detail);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setOrientation(LinearLayoutManager.VERTICAL);
        rvStat.setLayoutManager(lm);
        //rvStat.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        //屏幕方向
        setRequestedOrientation(SCREEN_ORIENTATION[screenOri]);

        //tvDetail.setText(MainActivity.statDetail);
        Intent i = getIntent();
        int avg = i.getIntExtra("avg", 0);
        int pos = i.getIntExtra("pos", 0);
        int len = i.getIntExtra("len", 0);
        ArrayList<Integer> trimIdx = i.getIntegerArrayListExtra("trim");
        String[] stat = i.getStringArrayExtra("detail");
        stAdapter = new StatAdapter(avg, pos, len, stat, trimIdx);
        rvStat.setAdapter(stAdapter);
        uiMode = getResources().getConfiguration().uiMode;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.w("dct", "configure change " + newConfig.uiMode);
        super.onConfigurationChanged(newConfig);
        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode;
            if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                Log.w("dct", "深色模式");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                recreate();
            } else {
                Log.w("dct", "浅色模式");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                recreate();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_copy) {   //复制成绩
            // if (Build.VERSION.SDK_INT >= 11) {
                android.content.ClipboardManager clip = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clip.setPrimaryClip(ClipData.newPlainText("text", APP.statDetail));
            //} else {
//                android.text.ClipboardManager clip = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//                clip.setText(tvDetail.getText().toString());
//            }
            Toast.makeText(DetailActivity.this, getString(R.string.copy_success), Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_save) {
            return saveStat();
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean saveStat() {
        final LayoutInflater factory = LayoutInflater.from(DetailActivity.this);
        int layoutId = R.layout.dialog_save_stat;
        View view = factory.inflate(layoutId, null);
        et2 = view.findViewById(R.id.edit_scrfile);
        et2.requestFocus();
        et2.setText(String.format(getString(R.string.default_stats_name), formatter.format(new Date())));
        et2.setSelection(et2.getText().length());
        new AlertDialog.Builder(DetailActivity.this).setView(view).setTitle(R.string.stat_save)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int i) {
                        requestStatExport(et2.getText().toString());
                        Utils.hideKeyboard(et2);
                    }
                }).setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                Utils.hideKeyboard(et2);
            }
        }).show();
        return true;
    }

    private void requestStatExport(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, ensureFileName(fileName, ".txt"));
        startActivityForResult(intent, REQUEST_EXPORT_STAT);
    }

    private String ensureFileName(String fileName, String extension) {
        if (fileName == null || fileName.trim().length() == 0) {
            fileName = String.format(getString(R.string.default_stats_name), formatter.format(new Date()));
        }
        String trimName = fileName.trim();
        return trimName.endsWith(extension) ? trimName : trimName + extension;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_STAT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Utils.saveStat(this, uri, APP.statDetail);
            }
        }
    }
}

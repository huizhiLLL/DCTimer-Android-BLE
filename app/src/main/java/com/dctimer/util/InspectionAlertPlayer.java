package com.dctimer.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.StringRes;

import com.dctimer.R;

import java.io.IOException;

public class InspectionAlertPlayer {
    private static final String TAG = "InspectionAlert";
    private static final String ASSET_EIGHT_SEC = "inspection/inspection_8s.wav";
    private static final String ASSET_TWELVE_SEC = "inspection/inspection_12s.wav";

    private final Context context;
    private MediaPlayer eightSecPlayer;
    private MediaPlayer twelveSecPlayer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsInitStarted;

    public InspectionAlertPlayer(Context context) {
        this.context = context.getApplicationContext();
        AssetManager assetManager = this.context.getAssets();
        eightSecPlayer = createPlayer(assetManager, ASSET_EIGHT_SEC);
        twelveSecPlayer = createPlayer(assetManager, ASSET_TWELVE_SEC);
        if (eightSecPlayer == null || twelveSecPlayer == null) {
            ensureTts();
        }
    }

    public void play(@StringRes int textResId) {
        MediaPlayer player = getPlayer(textResId);
        if (player != null) {
            if (restart(player)) return;
        }
        speakFallback(textResId);
    }

    public void release() {
        releasePlayer(eightSecPlayer);
        eightSecPlayer = null;
        releasePlayer(twelveSecPlayer);
        twelveSecPlayer = null;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
        ttsInitStarted = false;
    }

    private MediaPlayer getPlayer(@StringRes int textResId) {
        if (textResId == R.string.eight_sec) return eightSecPlayer;
        if (textResId == R.string.twelve_sec) return twelveSecPlayer;
        return null;
    }

    private MediaPlayer createPlayer(AssetManager assetManager, String assetPath) {
        AssetFileDescriptor afd = null;
        try {
            afd = assetManager.openFd(assetPath);
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.seekTo(0);
                }
            });
            player.prepare();
            return player;
        } catch (IOException e) {
            Log.i(TAG, "未找到观察提醒音频，回退 TTS: " + assetPath);
            return null;
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private boolean restart(MediaPlayer player) {
        try {
            if (player.isPlaying()) {
                player.pause();
            }
            player.seekTo(0);
            player.start();
            return true;
        } catch (IllegalStateException e) {
            Log.w(TAG, "观察提醒音频播放失败，回退 TTS", e);
            return false;
        }
    }

    private void speakFallback(@StringRes final int textResId) {
        ensureTts();
        if (tts == null) return;
        if (!ttsReady) {
            Log.w(TAG, "TTS 尚未就绪，跳过本次观察提醒");
            return;
        }
        tts.speak(context.getString(textResId), TextToSpeech.QUEUE_FLUSH, null);
    }

    private void ensureTts() {
        if (tts != null || ttsInitStarted) return;
        ttsInitStarted = true;
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (!ttsReady) {
                    Log.e(TAG, "TTS 初始化失败");
                }
            }
        });
    }

    private void releasePlayer(MediaPlayer player) {
        if (player == null) return;
        try {
            player.stop();
        } catch (IllegalStateException ignore) {
        }
        player.release();
    }
}

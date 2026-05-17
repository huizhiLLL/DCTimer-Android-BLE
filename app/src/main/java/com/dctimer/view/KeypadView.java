package com.dctimer.view;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import com.dctimer.R;

public class KeypadView extends RelativeLayout {
    private Activity context;
    private EditText editText;
    private ImageView imgClose;
    private ImageButton btnBackspace;
    private Button btnClear;
    private Button btnDone;
    private Button[] btnKey = new Button[12];
    private RadioGroup radioGroup;
    private OnClickListener mOnClickListener;
    private View mLayout;

    public KeypadView(Context context) {
        super(context);
        this.context = (Activity) context;
        initView();
    }

    public KeypadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = (Activity) context;
        initView();
    }

    public KeypadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = (Activity) context;
        initView();
    }

    private void initView() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        mLayout = LayoutInflater.from(context).inflate(R.layout.layout_keypad, null);
        imgClose  = mLayout.findViewById(R.id.iv_close);//关闭
        editText = mLayout.findViewById(R.id.edit_text);
        radioGroup = mLayout.findViewById(R.id.rg_penalty);
        btnDone = mLayout.findViewById(R.id.bt_done);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishInput();
            }
        });
        btnClear = mLayout.findViewById(R.id.bt_clear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText.setText("");
            }
        });
        btnBackspace = mLayout.findViewById(R.id.bt_bs);
        btnBackspace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteLastChar();
            }
        });
        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeInput();
            }
        });
        int[] keyIds = {R.id.bt_0, R.id.bt_1, R.id.bt_2, R.id.bt_3, R.id.bt_4, R.id.bt_5, R.id.bt_6, R.id.bt_7, R.id.bt_8, R.id.bt_9, R.id.bt_colon, R.id.bt_dot};
        for (int i = 0; i < keyIds.length; i++) {
            btnKey[i] = mLayout.findViewById(keyIds[i]);
            btnKey[i].setTag(i);
            btnKey[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int button = (int) view.getTag();
                    if (button < 10)
                        appendChar((char) ('0' + button));
                    else if (button == 10)
                        appendChar(':');
                    else if (button == 11)
                        appendChar('.');
                }
            });
        }
        this.addView(mLayout);
        post(new Runnable() {
            @Override
            public void run() {
                requestFocus();
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean handleKeyEvent(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN && handleKeyDown(event);
    }

    public static boolean isManualTimeInputKey(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int unicodeChar = event.getUnicodeChar();
        if (unicodeChar >= '0' && unicodeChar <= '9') return true;
        if (unicodeChar == '.' || unicodeChar == ':') return true;

        int keyCode = event.getKeyCode();
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) return true;
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) return true;
        return keyCode == KeyEvent.KEYCODE_PERIOD
                || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT
                || keyCode == KeyEvent.KEYCODE_SEMICOLON;
    }

    private boolean handleKeyDown(KeyEvent event) {
        int unicodeChar = event.getUnicodeChar();
        if (unicodeChar >= '0' && unicodeChar <= '9') {
            appendChar((char) unicodeChar);
            return true;
        }
        if (unicodeChar == '.' || unicodeChar == ':') {
            appendChar((char) unicodeChar);
            return true;
        }

        int keyCode = event.getKeyCode();
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendChar((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            appendChar((char) ('0' + keyCode - KeyEvent.KEYCODE_NUMPAD_0));
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_PERIOD:
            case KeyEvent.KEYCODE_NUMPAD_DOT:
                appendChar('.');
                return true;
            case KeyEvent.KEYCODE_SEMICOLON:
                appendChar(':');
                return true;
            case KeyEvent.KEYCODE_DEL:
                deleteLastChar();
                return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                editText.setText("");
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                finishInput();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                closeInput();
                return true;
            default:
                return false;
        }
    }

    private void appendChar(char c) {
        editText.getText().append(c);
    }

    private void deleteLastChar() {
        int len = editText.getText().length();
        if (len > 0)
            editText.getText().delete(len - 1, len);
    }

    private void finishInput() {
        if (mOnClickListener == null) return;
        int rgid = radioGroup.getCheckedRadioButtonId();
        int penalty = 0;
        switch (rgid) {
            case R.id.rb_no_penalty: penalty = 0; break;
            case R.id.rb_plus2: penalty = 1; break;
            case R.id.rb_dnf: penalty = 2; break;
        }
        mOnClickListener.onFinish(editText.getText().toString(), penalty);
    }

    private void closeInput() {
        if (mOnClickListener != null) {
            mOnClickListener.onClose();
        }
    }

    public interface OnClickListener {
        void onFinish(String time, int penalty);
        void onClose();
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.mOnClickListener = onClickListener;
    }
}

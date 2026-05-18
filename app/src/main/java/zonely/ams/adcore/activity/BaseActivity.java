package zonely.ams.adcore.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import zonely.ams.adcore.logging.AdcoreLogger;

public class BaseActivity extends Activity implements
        View.OnClickListener,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener,
        TextView.OnEditorActionListener,
        View.OnFocusChangeListener,
        View.OnTouchListener,
        View.OnKeyListener,
        SeekBar.OnSeekBarChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterFullscreen();
        AdcoreLogger.i(getClass().getSimpleName(), "Activity created.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    @Override
    public void onClick(View view) {
        handleOnClick(view);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        handleOnItemSelected(parent, view, position, id);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        handleOnNothingSelected(parent);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        handleOnCheckedChanged(buttonView, isChecked);
    }

    @Override
    public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
        return handleOnEditorAction(view, actionId, event);
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        handleOnFocusChange(view, hasFocus);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        return handleOnTouch(view, event);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        return handleOnKey(view, keyCode, event);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        handleOnProgressChanged(seekBar, progress, fromUser);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        handleOnStartTrackingTouch(seekBar);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        handleOnStopTrackingTouch(seekBar);
    }

    protected void handleOnClick(View view) {
    }

    protected void handleOnItemSelected(AdapterView<?> parent, View view, int position, long id) {
    }

    protected void handleOnNothingSelected(AdapterView<?> parent) {
    }

    protected void handleOnCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    }

    protected boolean handleOnEditorAction(TextView view, int actionId, KeyEvent event) {
        return false;
    }

    protected void handleOnFocusChange(View view, boolean hasFocus) {
    }

    protected boolean handleOnTouch(View view, MotionEvent event) {
        return false;
    }

    protected boolean handleOnKey(View view, int keyCode, KeyEvent event) {
        return false;
    }

    protected void handleOnProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    protected void handleOnStartTrackingTouch(SeekBar seekBar) {
    }

    protected void handleOnStopTrackingTouch(SeekBar seekBar) {
    }

    protected void enterFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    protected TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        return view;
    }

    protected void goTo(Class<?> activityClass, boolean finishCurrent) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
        if (finishCurrent) {
            finish();
        }
    }

    protected int backgroundColor() {
        return Color.rgb(6, 9, 14);
    }
}

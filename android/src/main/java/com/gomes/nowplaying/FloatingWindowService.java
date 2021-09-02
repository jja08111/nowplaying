package com.gomes.nowplaying;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.gomes.NowPlaying.R;

import java.util.ArrayList;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

public class FloatingWindowService extends Service implements View.OnClickListener {
    static public final String SHARED_PREFS_KEY = "com.example.p_lyric.floating.window.service";
    static public final String IS_APP_VISIBLE_KEY = "isAppVisible";
    static private final String LYRICS_KEY = "lyrics";
    static private final String TITLE_KEY = "title";
    static private final String ARTIST_KEY = "artist";

    // TODO: 백그라운드에서 곡 변경시 가사 업데이트하기
    static boolean isDisplayed = false;

    private WindowManager mWindowManager;
    private View mFloatingView;
    private View collapsedView;
    private View expandedView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //getting the widget layout from xml using layout inflater
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);

        //setting the layout parameters
        final WindowManager.LayoutParams params;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        //getting windows services and adding the floating view to it
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        //getting the collapsed and expanded view from the floating view
        collapsedView = mFloatingView.findViewById(R.id.layoutCollapsed);
        expandedView = mFloatingView.findViewById(R.id.layoutExpanded);

        //adding click listener to close button and expanded view
        mFloatingView.findViewById(R.id.buttonClose).setOnClickListener(this);
        expandedView.setOnClickListener(this);

        SharedPreferences prefs = this.getSharedPreferences(FloatingWindowService.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        String lyrics = prefs.getString(FloatingWindowService.LYRICS_KEY, "");
        TextView textView = mFloatingView.findViewById(R.id.expanded_textView);
        if (!lyrics.isEmpty()) textView.setText(lyrics);

        isDisplayed = true;

        //adding an touchlistener to make drag movement of the floating widget
        mFloatingView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.performClick();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        //when the drag is ended switching the state of the widget
                        collapsedView.setVisibility(View.GONE);
                        expandedView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //this code is helping the widget to move around the screen with fingers
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isDisplayed = false;
        if (mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
            mFloatingView = null;
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.layoutExpanded) {//switching views
            collapsedView.setVisibility(View.VISIBLE);
            expandedView.setVisibility(View.GONE);
        } else if (id == R.id.buttonClose) {//closing the widget
            stopSelf();
        }
    }

    // TODO(민성): SharedPreference 에서 플로팅을 사용하기로 한 경우만 띄우도록 하기
    public static void startFloatingService(MethodChannel channel, Context context, Map<String, Object> data) {
        final String title = (String) data.get("title");
        if (title == null || title.isEmpty()) return;

        final String artist = (String) data.get("artist");
        if (artist == null || artist.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        final String currentTitle = prefs.getString(TITLE_KEY, "");
        final String currentArtist = prefs.getString(ARTIST_KEY, "");

        if (currentTitle.equals(title) && currentArtist.equals(artist))
            return;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(TITLE_KEY, title);
        editor.putString(ARTIST_KEY, artist);
        editor.apply();

        invokeMethod(channel, context);
    }

    public static void startFloatingService(BinaryMessenger messenger, Context context) {
        invokeMethod(new MethodChannel(messenger, "gomes.com.es/nowplaying"), context);
    }

    private static void invokeMethod(MethodChannel channel, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);

        final String title = prefs.getString(TITLE_KEY, "");
        final String artist = prefs.getString(ARTIST_KEY, "");
        final boolean isAppVisible = prefs.getBoolean(IS_APP_VISIBLE_KEY, true);

        if (title.isEmpty() || artist.isEmpty() || isAppVisible) return;

        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(title);
        arguments.add(artist);

        channel.invokeMethod("updateLyrics", arguments, new MethodChannel.Result() {
            @Override
            public void success(Object o) {
                SharedPreferences prefs = context.getSharedPreferences(FloatingWindowService.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
                prefs.edit().putString(FloatingWindowService.LYRICS_KEY, o.toString()).apply();

                context.startService(new Intent(context, FloatingWindowService.class));
            }

            @Override
            public void error(String s, String s1, Object o) {
            }

            @Override
            public void notImplemented() {
            }
        });
    }
}
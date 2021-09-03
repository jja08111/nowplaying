package com.gomes.nowplaying;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.gomes.NowPlaying.R;

import java.util.ArrayList;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

public class FloatingWindowService extends Service implements View.OnClickListener {
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    static public final String SHARED_PREFS_KEY = "com.example.p_lyric.floating.window.service";
    static public final String IS_APP_VISIBLE_KEY = "isAppVisible";
    static private final String LYRICS_KEY = "lyrics";
    static private final String TITLE_KEY = "title";
    static private final String ARTIST_KEY = "artist";

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
        createNotificationChannel();
        Intent notificationIntent = getAppStartingIntent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PLyric 가사 창 실행 중")
                .setContentText("탭하여 앱 실행")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mFloatingView == null) {
            createWindowView();

        } else {
            setLyricsTextView();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
            mFloatingView = null;
        }

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if (viewId == R.id.collapsed_close_button || viewId == R.id.expanded_close_button) {
            stopSelf();
        } else if (viewId == R.id.expanded_fullscreen_button) {
            startApp();
        } else if (viewId == R.id.expanded_collapse_button) {//switching views
            collapsedView.setVisibility(View.VISIBLE);
            expandedView.setVisibility(View.GONE);
        } else if (viewId == R.id.collapsed_expand_button) {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 앱을 화면의 최상단에 띄운다.
     */
    private void startApp() {
        this.startActivity(getAppStartingIntent());
    }

    private Intent getAppStartingIntent() {
        Intent startIntent = this
                .getPackageManager()
                .getLaunchIntentForPackage(this.getPackageName());

        if (startIntent != null)
            startIntent.setFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            );
        return startIntent;
    }

    private void setLyricsTextView() {
        if (mFloatingView == null) return;

        SharedPreferences prefs = this.getSharedPreferences(FloatingWindowService.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        String lyrics = prefs.getString(FloatingWindowService.LYRICS_KEY, "");
        TextView textView = mFloatingView.findViewById(R.id.expanded_textView);

        if (!lyrics.isEmpty()) textView.setText(lyrics);
    }

    private void createWindowView() {
        // getting the widget layout from xml using layout inflater
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);

        // setting the layout parameters
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

        // getting windows services and adding the floating view to it
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        setLyricsTextView();

        // getting the collapsed and expanded view from the floating view
        collapsedView = mFloatingView.findViewById(R.id.layoutCollapsed);
        expandedView = mFloatingView.findViewById(R.id.layoutExpanded);

        // 클릭 리스너 추가
        mFloatingView.findViewById(R.id.collapsed_close_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.collapsed_expand_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_close_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_fullscreen_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_collapse_button).setOnClickListener(this);

        // adding an touch listener to make drag movement of the floating widget
        collapsedView.setOnTouchListener(getWindowTouchListener(params));
        expandedView.setOnTouchListener(getWindowTouchListener(params));
    }

    private View.OnTouchListener getWindowTouchListener(WindowManager.LayoutParams params) {
        return new View.OnTouchListener() {
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
                    case MotionEvent.ACTION_MOVE:
                        //this code is helping the widget to move around the screen with fingers
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        };
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
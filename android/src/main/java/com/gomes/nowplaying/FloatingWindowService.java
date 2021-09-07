package com.gomes.nowplaying;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.gomes.NowPlaying.R;

import java.util.Map;

import io.flutter.Log;
import io.flutter.plugin.common.MethodChannel;

public class FloatingWindowService extends Service implements View.OnClickListener {
    private static final String TAG = "FloatingWindowService";
    private static final int NOTIFICATION_ID = 1;
    private static final String EXTRA_NOTIFICATION_ID = "com.example.p_lyric.NotificationAction";
    private static final String ACTION_CREATE_VIEW = "create_view";
    private static final String ACTION_REMOVE_VIEW = "remove_view";
    private static final String LYRICS_KEY = "lyrics";
    private static final String TITLE_KEY = "title";
    private static final String ARTIST_KEY = "artist";
    private static final String LAST_MODE_KEY = "last_mode";

    private enum ViewMode {
        BUBBLE,
        WINDOW
    }

    public static final String SHARED_PREFS_KEY = "com.example.p_lyric.floating.window.service";
    public static final String SEARCH_CALLBACK_KEY = "searchLyricCallback";
    public static final String IS_APP_VISIBLE_KEY = "isAppVisible";
    public static final String CHANNEL_ID = "ForegroundServiceChannel";

    /**
     * Background Dart execution context.
     */
    private static FlutterBackgroundExecutor flutterBackgroundExecutor;

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
    }

    /**
     * Starts the background isolate for the {@link FloatingWindowService}.
     */
    public static void startBackgroundIsolate(Context context, long callbackHandle) {
        if (flutterBackgroundExecutor != null) {
            Log.w(TAG, "Attempted to start a duplicate background isolate. Returning...");
            return;
        }
        flutterBackgroundExecutor = new FlutterBackgroundExecutor();
        flutterBackgroundExecutor.startBackgroundIsolate(context, callbackHandle);
    }

    public static void setCallbackDispatcher(Context context, long callbackHandle) {
        FlutterBackgroundExecutor.setCallbackDispatcher(context, callbackHandle);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );

            try {
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.createNotificationChannel(serviceChannel);
            } catch (Exception e) {
                Log.e("nowplaying", "Failed to create notification channel: " + e);
            }

        }
    }

    private void startForegroundService(boolean isActionRemoving) {
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, getAppStartingIntent(), PendingIntent.FLAG_IMMUTABLE);

        Intent createOrRemoveIntent = new Intent(this, CreateOrRemoveActionReceiver.class)
                .putExtra(EXTRA_NOTIFICATION_ID, isActionRemoving ? ACTION_REMOVE_VIEW : ACTION_CREATE_VIEW);
        PendingIntent createOrRemovePendingIntent = PendingIntent.getBroadcast(
                this, 0, createOrRemoveIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent cancelIntent = new Intent(this, CancelActionReceiver.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PLyric 플로팅 가사")
                .setContentText("탭하여 앱 실행")
                .setSmallIcon(R.drawable.ic_notification)
                .setShowWhen(false)
                .addAction(R.drawable.ic_close_fullscreen, isActionRemoving ? "창 숨기기" : "창 보이기", createOrRemovePendingIntent)
                .addAction(R.drawable.ic_close, "완전 중단", cancelPendingIntent)
                .setAutoCancel(false)
                .setContentIntent(contentPendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        if (Build.VERSION_CODES.S > Build.VERSION.SDK_INT) {
            Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            this.sendBroadcast(it);
        }
    }

    private void stopForegroundService() {
        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
            notificationManager.cancel(NOTIFICATION_ID);
    }

    public static class CancelActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.stopService(new Intent(context, FloatingWindowService.class));
        }
    }

    public static class CreateOrRemoveActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra(EXTRA_NOTIFICATION_ID);
            if (action == null) return;

            Intent createOrRemoveIntent = new Intent(context, FloatingWindowService.class);

            switch (action) {
                case ACTION_CREATE_VIEW:
                    context.startService(createOrRemoveIntent);
                    break;
                case ACTION_REMOVE_VIEW:
                    createOrRemoveIntent.putExtra(EXTRA_NOTIFICATION_ID, ACTION_REMOVE_VIEW);
                    context.startService(createOrRemoveIntent);
                    break;
                default:
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getStringExtra(EXTRA_NOTIFICATION_ID);

        if (action != null) {
            if (ACTION_REMOVE_VIEW.equals(action)) {
                removeWindowView();
            }
            return START_STICKY;
        }

        if (mFloatingView == null) {
            createWindowView();
        } else {
            setLyricsTextView();
            setCoverImage();
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
        stopForegroundService();
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if (viewId == R.id.collapsed_close_button || viewId == R.id.expanded_close_button) {
            removeWindowView();
        } else if (viewId == R.id.expanded_fullscreen_button) {
            startApp();
        } else if (viewId == R.id.expanded_collapse_button) {//switching views
            switchToBubble();
        }
    }

    private void switchToBubble() {
        saveViewModePrefs(ViewMode.BUBBLE);
        collapsedView.setVisibility(View.VISIBLE);
        expandedView.setVisibility(View.GONE);
    }

    private void switchToWindow() {
        saveViewModePrefs(ViewMode.WINDOW);
        collapsedView.setVisibility(View.GONE);
        expandedView.setVisibility(View.VISIBLE);
    }

    private void saveViewModePrefs(ViewMode viewMode) {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString(LAST_MODE_KEY, viewMode.toString()).apply();
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

        if (!lyrics.isEmpty()) {
            textView.setText(lyrics);
            ScrollView scrollView = mFloatingView.findViewById(R.id.expanded_scrollView);
            scrollView.setScrollY(0);
        }
    }

    private void setCoverImage() {
        byte[] bytes = (byte[]) NowPlayingPlugin.trackData.get("image");

        if (bytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                ImageView collapsedImageView = mFloatingView.findViewById(R.id.collapsed_icon);
                collapsedImageView.setImageBitmap(bitmap);
                collapsedImageView.setBackground(new ShapeDrawable(new OvalShape()));
                collapsedImageView.setClipToOutline(true);

                ImageView expandedImageView = mFloatingView.findViewById(R.id.expanded_icon);
                expandedImageView.setImageBitmap(bitmap);
                expandedImageView.setBackground(new ShapeDrawable(new OvalShape()));
                expandedImageView.setClipToOutline(true);
            }
        }
    }

    private void createWindowView() {
        startForegroundService(true);

        // getting the widget layout from xml using layout inflater
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);

        // setting the layout parameters
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // getting windows services and adding the floating view to it
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        setLyricsTextView();
        setCoverImage();

        // getting the collapsed and expanded view from the floating view
        collapsedView = mFloatingView.findViewById(R.id.layoutCollapsed);
        expandedView = mFloatingView.findViewById(R.id.layoutExpanded);

        // 가장 마지막에 설정한 모드 값으로 설정한다.
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        String lastMode = prefs.getString(LAST_MODE_KEY, ViewMode.WINDOW.toString());
        switch (ViewMode.valueOf(lastMode)) {
            case WINDOW:
                switchToWindow();
                break;
            case BUBBLE:
                switchToBubble();
                break;
            default:
                break;
        }

        // 클릭 리스너 추가
        mFloatingView.findViewById(R.id.collapsed_close_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_close_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_fullscreen_button).setOnClickListener(this);
        mFloatingView.findViewById(R.id.expanded_collapse_button).setOnClickListener(this);

        // adding an touch listener to make drag movement of the floating widget
        collapsedView.setOnTouchListener(getWindowTouchListener(params, true));
        expandedView.setOnTouchListener(getWindowTouchListener(params, false));
    }

    private void removeWindowView() {
        try {
            if (mWindowManager != null) {
                if (mFloatingView != null) {
                    mWindowManager.removeView(mFloatingView);
                    mFloatingView = null;
                    startForegroundService(false);
                }
            }
            mWindowManager = null;
        } catch (IllegalArgumentException e) {
            Log.e("nowplaying", "Failed to remove view. View not found");
        }
    }

    private View.OnTouchListener getWindowTouchListener(WindowManager.LayoutParams params, boolean isBubble) {
        Display display = mWindowManager.getDefaultDisplay();
        Point deviceSize = new Point();
        display.getSize(deviceSize);

        return new View.OnTouchListener() {
            private final Size minSize = new Size(-deviceSize.x / 2, -deviceSize.y / 2);
            private final Size maxSize = new Size(deviceSize.x / 2, deviceSize.y / 2);
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
                        if (isBubble) {
                            long tapDownTime = event.getDownTime();
                            long actionTime = event.getEventTime();
                            if (actionTime - tapDownTime < 300
                                    && Math.abs(initialTouchX - event.getRawX()) < 10
                                    && Math.abs(initialTouchY - event.getRawY()) < 10) {
                                switchToWindow();
                            }
                        }
                    case MotionEvent.ACTION_MOVE:
                        int viewHalfWidth = v.getWidth() / 2;
                        int viewHalfHeight = v.getHeight() / 2;

                        params.x = Math.max(minSize.getWidth() + viewHalfWidth,
                                Math.min(maxSize.getWidth() - viewHalfWidth,
                                        initialX + (int) (event.getRawX() - initialTouchX)));
                        params.y = Math.max(minSize.getHeight() + viewHalfHeight,
                                Math.min(maxSize.getHeight() - viewHalfHeight,
                                        initialY + (int) (event.getRawY() - initialTouchY)));
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        };
    }

    public static void startFloatingService(Context context) {
        startFloatingService(context, false);
    }

    // TODO(민성): SharedPreference 에서 플로팅을 사용하기로 한 경우만 띄우도록 하기
    public static void startFloatingService(Context context, boolean forceStart) {
        Map<String, Object> data = NowPlayingPlugin.extractFieldsFor(
                context,
                NowPlayingListenerService.lastToken,
                NowPlayingListenerService.lastIcon);

        if (data == null) return;

        NowPlayingPlugin.trackData = data;

        final String title = (String) NowPlayingPlugin.trackData.get("title");
        if (title == null || title.isEmpty()) return;

        final String artist = (String) NowPlayingPlugin.trackData.get("artist");
        if (artist == null || artist.isEmpty()) return;

        if (!forceStart) {
            SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
            final String currentTitle = prefs.getString(TITLE_KEY, "");
            final String currentArtist = prefs.getString(ARTIST_KEY, "");

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(TITLE_KEY, title);
            editor.putString(ARTIST_KEY, artist);
            editor.apply();

            if (currentTitle.equals(title) && currentArtist.equals(artist))
                return;
        }

        invokeMethod(context, title, artist, forceStart);
    }

    private static void invokeMethod(Context context, String title, String artist, boolean forceStart) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        final boolean isAppVisible = prefs.getBoolean(IS_APP_VISIBLE_KEY, true);
        if (!forceStart && isAppVisible) return;

        long callbackHandle = prefs.getLong(SEARCH_CALLBACK_KEY, 0);
        if (callbackHandle == 0) return;

        MethodChannel.Result result = new MethodChannel.Result() {
            @Override
            public void success(Object o) {
                if (o == null) return;

                SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
                prefs.edit().putString(LYRICS_KEY, o.toString()).apply();

                // System overlay 권한이 허용된 경우만 서비스 실행
                if (Settings.canDrawOverlays(context))
                    context.startService(new Intent(context, FloatingWindowService.class));
            }

            @Override
            public void error(String s, String s1, Object o) {
            }

            @Override
            public void notImplemented() {
            }
        };

        flutterBackgroundExecutor.executeDartCallbackInBackgroundIsolate(callbackHandle, title, artist, result);
    }
}
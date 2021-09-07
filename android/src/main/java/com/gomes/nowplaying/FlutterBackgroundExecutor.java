package com.gomes.nowplaying;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An background execution abstraction which handles initializing a background isolate running a
 * callback dispatcher, used to invoke Dart callbacks while backgrounded.
 */
public class FlutterBackgroundExecutor implements MethodCallHandler {
    private static final String TAG = "FlutterBackgroundExecutor";
    private static final String CALLBACK_HANDLE_KEY = "callback_handle";

    /**
     * The {@link MethodChannel} that connects the Android side of this plugin with the background
     * Dart isolate that was created by this plugin.
     */
    private MethodChannel backgroundChannel;

    private FlutterEngine backgroundFlutterEngine;

    private final AtomicBoolean isCallbackDispatcherReady = new AtomicBoolean(false);

    /**
     * Sets the Dart callback handle for the Dart method that is responsible for initializing the
     * background Dart isolate, preparing it to receive Dart callback tasks requests.
     */
    public static void setCallbackDispatcher(Context context, long callbackHandle) {
        SharedPreferences prefs = context.getSharedPreferences(FloatingWindowService.SHARED_PREFS_KEY, 0);
        prefs.edit().putLong(CALLBACK_HANDLE_KEY, callbackHandle).apply();
    }

    /** Returns true when the background isolate has started and is ready. */
    public boolean isRunning() {
        return isCallbackDispatcherReady.get();
    }

    private void onInitialized() {
        isCallbackDispatcherReady.set(true);
    }

    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        String method = call.method;

        try {
            if (method.equals("FloatingWindowService.initialized")) {
                // This message is sent by the background method channel as soon as the background isolate
                // is running. From this point forward, the Android side of this plugin can send
                // callback handles through the background method channel, and the Dart side will execute
                // the Dart methods corresponding to those callback handles.
                onInitialized();
                result.success(true);
            } else {
                result.notImplemented();
            }
        } catch (PluginRegistrantException e) {
            result.error("error", "FloatingService error: " + e.getMessage(), null);
        }
    }

    /**
     * Starts running a background Dart isolate within a new {@link FlutterEngine}.
     */
    public void startBackgroundIsolate(Context context, long callbackHandle) {
        if (backgroundFlutterEngine != null) {
            Log.e(TAG, "Background isolate already started");
            return;
        }

        Log.i(TAG, "Starting FloatingWindowService...");
        @SuppressWarnings("deprecation")
        String appBundlePath = FlutterMain.findAppBundlePath();
        AssetManager assets = context.getAssets();
        if (!isRunning()) {
            backgroundFlutterEngine = new FlutterEngine(context);

            // We need to create an instance of `FlutterEngine` before looking up the
            // callback. If we don't, the callback cache won't be initialized and the
            // lookup will fail.
            FlutterCallbackInformation flutterCallback =
                    FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (flutterCallback == null) {
                Log.e(TAG, "Fatal: failed to find callback");
                return;
            }

            DartExecutor executor = backgroundFlutterEngine.getDartExecutor();
            initializeMethodChannel(executor);
            DartCallback dartCallback = new DartCallback(assets, appBundlePath, flutterCallback);

            executor.executeDartCallback(dartCallback);
        }
    }

    /**
     * Executes the desired Dart callback in a background Dart isolate.
     *
     * <p>The given {@code intent} should contain a {@code long} extra called "callbackHandle", which
     * corresponds to a callback registered with the Dart VM.
     */
    public void executeDartCallbackInBackgroundIsolate(
            long callbackHandle, String title, String artist, MethodChannel.Result result) {

        // Handle the alarm event in Dart. Note that for this plugin, we don't
        // care about the method name as we simply lookup and invoke the callback
        // provided.
        backgroundChannel.invokeMethod(
                "invokeFloatingWindowCallback",
                new Object[] {callbackHandle, title, artist},
                result);
    }

    private void initializeMethodChannel(BinaryMessenger isolate) {
        // backgroundChannel is the channel responsible for receiving the following messages from
        // the background isolate that was setup by this plugin:
        // - "FloatingWindowService.initialized"
        //
        // This channel is also responsible for sending requests from Android to Dart to execute Dart
        // callbacks in the background isolate.
        backgroundChannel =
                new MethodChannel(
                        isolate,
                        "gomes.com.es/nowplaying_background",
                        JSONMethodCodec.INSTANCE);
        backgroundChannel.setMethodCallHandler(this);
    }
}
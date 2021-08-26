package com.gomes.nowplaying;

import androidx.annotation.NonNull;

import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import io.flutter.plugin.common.MethodChannel.Result;

public class NowPlayingController {
    private final Context context;

    NowPlayingController(Context context) {
        this.context = context;
    }

    private MediaController getMediaController() {
        return new MediaController(context, NowPlayingListenerService.lastToken);
    }

    public void playOrPause(@NonNull Result result) {
        try {
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            final MediaController mediaController = getMediaController();

            if (audioManager.isMusicActive())
                mediaController.getTransportControls().pause();
            else
                mediaController.getTransportControls().play();
            result.success(true);
        } catch (Exception e) {
            result.error(e.toString(), "Control error", "Failed to play or pause media");
        }
    }

    public void skipToPrevious(@NonNull Result result) {
        try {
            final MediaController mediaController = getMediaController();
            mediaController.getTransportControls().skipToPrevious();
            result.success(true);
        } catch (Exception e) {
            result.error(e.toString(), "Control error", "Failed to skip to previous media");
        }
    }

    public void skipToNext(@NonNull Result result) {
        try {
            final MediaController mediaController = getMediaController();
            mediaController.getTransportControls().skipToNext();
            result.success(true);
        } catch (Exception e) {
            result.error(e.toString(), "Control error", "Failed to skip to next media");
        }
    }
}

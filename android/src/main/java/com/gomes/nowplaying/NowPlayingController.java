package com.gomes.nowplaying;

import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;

public class NowPlayingController {
    private final Context context;

    NowPlayingController(Context context) {
        this.context = context;
    }

    private MediaController getMediaController() {
        return new MediaController(context, NowPlayingListenerService.lastToken);
    }

    public void playOrPause() {
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final MediaController mediaController = getMediaController();

        if (audioManager.isMusicActive())
            mediaController.getTransportControls().pause();
        else
            mediaController.getTransportControls().play();
    }

    public void skipToPrevious() {
        final MediaController mediaController = getMediaController();
        mediaController.getTransportControls().skipToPrevious();
    }

    public void skipToNext() {
        final MediaController mediaController = getMediaController();
        mediaController.getTransportControls().skipToNext();
    }
}

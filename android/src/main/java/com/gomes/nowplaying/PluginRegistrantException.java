package com.gomes.nowplaying;

class PluginRegistrantException extends RuntimeException {
    public PluginRegistrantException() {
        super(
                "PluginRegistrantCallback is not set. Did you forget to call "
                        + "FloatingWindowService.setPluginRegistrant? See the README for instructions.");
    }
}
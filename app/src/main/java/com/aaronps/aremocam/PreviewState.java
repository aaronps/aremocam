package com.aaronps.aremocam;

import android.hardware.Camera;

/**
 * Created by krom on 12/16/16.
 */

final class PreviewState {
    final boolean                previewing;
    final int                    width;
    final int                    height;
    final Camera.PreviewCallback previewCallback;

    public PreviewState() {
        previewing = false;
        width = 0;
        height = 0;
        previewCallback = null;
    }

    public PreviewState(boolean previewing, int width, int height, Camera.PreviewCallback previewCallback) {
        this.previewing = previewing;
        this.width = width;
        this.height = height;
        this.previewCallback = previewCallback;
    }
}

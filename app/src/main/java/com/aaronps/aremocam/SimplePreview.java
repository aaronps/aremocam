package com.aaronps.aremocam;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;

/**
 * @todo use Looper and Handler .... But this makes it AndroidOnly
 * <p>
 * Created by krom on 12/11/16.
 */

public class SimplePreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "SimplePreview";

    private final int           mCameraIndex;
    private final SurfaceHolder mSurfaceHolder;
    private final Context       mContext;

    private volatile boolean mHasSurface = false;

    private Camera mCamera;

    public SimplePreview(int cameraIndex, Context context) {
        super(context);
        mCameraIndex = cameraIndex;
        mSurfaceHolder = getHolder();
        mContext = context;

        mSurfaceHolder.addCallback(this);
    }

    public final void start() {
        if (mCamera == null)
        {
            try
            {
                mCamera = Camera.open(mCameraIndex);
            }
            catch (Exception e)
            {
                Log.d(TAG, "Unable to acquire camera", e);
            }
        }

        // no else
        if (mCamera != null)
        {
            if (mHasSurface)
            {
                updatePreview();
            }
        }
    }

    public final void stop() {
        if (mCamera != null)
        {
            final Camera cam = mCamera;
            mCamera = null;
            cam.release();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");
        mHasSurface = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        // set preview size and make any resize, rotate or
        // reformatting changes here

        PixelFormat pixelFormat = new PixelFormat();
        PixelFormat.getPixelFormatInfo(format, pixelFormat);

        Log.d(TAG, String.format("surfaceChanged format=%d(%d/%d) %dx%d",
                                 format,
                                 pixelFormat.bitsPerPixel,
                                 pixelFormat.bytesPerPixel,
                                 width,
                                 height));

        updatePreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
        mHasSurface = false;
    }

    public boolean updatePreview() {
        if (mCamera == null)
        {
            return false;
        }

//        // if options changed during this process, doesn't affect us.
//        final CaptureOptions options = mCaptureOptions;
//
//        if ( options.mShallCaptureVideo )
//        {
        if (mSurfaceHolder.getSurface() == null)
        {
            Log.d(TAG, "Called updatePreview but there is no surface");
            return false;
        }

//            if ( mCaptureState.isCapturing )
//            {
//                if ( mCaptureState.width != options.mDesiredWidth || mCaptureState.height != options.mDesiredHeight )
//                {
//                    try { mCamera.stopPreview(); } catch (Exception e) { }
//                    mCaptureState = new CaptureState();
//                }
//                else
//                {
//                    // nothing to do, it is the same, maybe notify...
//                    return true;
//                }
//            }

        try
        {
            mCamera.setPreviewDisplay(mSurfaceHolder);

            final Camera.Parameters parameters = mCamera.getParameters();
//            for (Camera.Size size : parameters.getSupportedPreviewSizes())
//            {
//                Log.d(TAG, String.format("Camera format: %dx%d", size.width, size.height));
//            }

//                final Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(768, 432);

            mCamera.setParameters(parameters);

            final WindowManager wm       = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            final int           rotation = wm.getDefaultDisplay().getRotation();

            int degrees = 0;
            switch (rotation)
            {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Camera.CameraInfo ci = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraIndex, ci);

            int result;
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                // @todo maybe review these orientation changes
                Log.d(TAG, "Is front, orientation=" + ci.orientation + " degrees=" + degrees);
                result = Math.abs((ci.orientation - (180 + degrees)) % 360);
//                result = 90;
//                result = (ci.orientation + degrees) % 360;
//                result = (360 - result) % 360;
            } else
            {
                result = (ci.orientation - degrees + 360) % 360;
            }
            Log.d(TAG, "Result orientx = " + result);
            mCamera.setDisplayOrientation(result);

//                mCamera.setPreviewCallback(this);

            mCamera.startPreview();

//                mCaptureState = new CaptureState(true, options.mDesiredWidth, options.mDesiredHeight);
            return true;
        }
        catch (IOException e)
        {
            Log.d(TAG, "Error starting preview", e);
        }
//        }
//        else
//        {
//            // shall stop video.
//            if ( mCaptureState.isCapturing )
//            {
//                try { mCamera.stopPreview(); } catch (Exception e) { }
//                mCaptureState = new CaptureState();
//            }
//        }
        return false;
    }


}

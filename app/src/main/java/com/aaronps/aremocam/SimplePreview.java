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
import java.util.List;

/**
 * @todo use Looper and Handler .... But this makes it AndroidOnly
 * <p>
 * Created by krom on 12/11/16.
 */

public final class SimplePreview
        extends SurfaceView
        implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "SimplePreview";

    private final int           mCameraIndex;
    private final SurfaceHolder mSurfaceHolder;
    private final Context       mContext;

    private volatile boolean mHasSurface = false;

    public Camera            mCamera;
    public List<Camera.Size> mPreviewSizes;

    private int               mSelectedPreviewSize = 0;
    private Camera.CameraInfo mCameraInfo          = new Camera.CameraInfo();

    private static final PreviewState PREVIEW_OFF = new PreviewState();

    private PreviewState mPreviewState        = PREVIEW_OFF;
    private PreviewState mDesiredPreviewState = PREVIEW_OFF;

    // @note @optimization This keep separated because it might change every frame and we don't want to renew it
    private volatile Camera.PreviewCallback mPreviewCallback;


    public SimplePreview(int cameraIndex, Context context) {
        super(context);
        mCameraIndex = cameraIndex;
        mSurfaceHolder = getHolder();
        mContext = context;

        mSurfaceHolder.addCallback(this);
    }

    public boolean acquire() {
        if (mCamera == null)
        {
            try
            {
                mCamera = Camera.open(mCameraIndex);

                final Camera.Parameters parameters = mCamera.getParameters();
                mPreviewSizes = parameters.getSupportedPreviewSizes();

                Camera.getCameraInfo(mCameraIndex, mCameraInfo);
            }
            catch (RuntimeException e)
            {
                Log.d(TAG, "Unable to acquire camera", e);
            }
        }

        return mCamera != null;
    }

    public void release() {
        if (mCamera != null)
        {
            mCamera.release();
            mCamera = null;
        }
    }

    public boolean start() {
        if (acquire())
        {
            if (mDesiredPreviewState.width == 0 || mDesiredPreviewState.height == 0)
            {
                Camera.Size sz = mPreviewSizes.get(0);
                mDesiredPreviewState = new PreviewState(true, sz.width, sz.height, null);
            }
            else if (!mDesiredPreviewState.previewing)
            {
                mDesiredPreviewState = new PreviewState(true, mDesiredPreviewState.width, mDesiredPreviewState.height, null);
            }

            if (mHasSurface)
            {
                return updatePreview();
            }
        }
        return false;
    }

    public boolean start(final int width, final int height) {
        if (acquire())
        {
            mDesiredPreviewState = new PreviewState(true, width, height, null);

            if (mHasSurface)
            {
                return updatePreview();
            }
        }
        return false;
    }

    public void stop() {
        if (mCamera != null)
        {
            mCamera.stopPreview();
        }

        mDesiredPreviewState = mPreviewState = PREVIEW_OFF;

        // @todo decide if release here or not.
        release();
    }

    public int getPreviewFormat() {
        if (mCamera != null)
        {
            return mCamera.getParameters().getPreviewFormat();
        }
        return -1;
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


    /**
     * @return unknown
     * @todo discover the meaning of the return value
     */
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

        if (mPreviewState == mDesiredPreviewState)
        {
            return mPreviewState.previewing; // what is the meaning of the return value?
        }

        if (mPreviewState.previewing)
        {
            mCamera.stopPreview();
            mPreviewState = PREVIEW_OFF;
        }

        if (mDesiredPreviewState.previewing)
        {
            final Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mDesiredPreviewState.width, mDesiredPreviewState.height);
            mCamera.setParameters(parameters);

            try
            {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.setPreviewCallback(this);
                mCamera.startPreview();
                mPreviewState = mDesiredPreviewState;
                return true;
            }
            catch (IOException ex)
            {
                Log.d(TAG, "Error starting thew preview", ex);
            }
        }

//            final WindowManager wm       = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
//            final int           rotation = wm.getDefaultDisplay().getRotation();
//
//            int degrees = 0;
//            switch (rotation)
//            {
//                case Surface.ROTATION_0:
//                    degrees = 0;
//                    break;
//                case Surface.ROTATION_90:
//                    degrees = 90;
//                    break;
//                case Surface.ROTATION_180:
//                    degrees = 180;
//                    break;
//                case Surface.ROTATION_270:
//                    degrees = 270;
//                    break;
//            }
//
//            Camera.CameraInfo ci = new Camera.CameraInfo();
//            Camera.getCameraInfo(mCameraIndex, ci);
//
//            int result;
//            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
//            {
//                // @todo maybe review these orientation changes
//                Log.d(TAG, "Is front, orientation=" + ci.orientation + " degrees=" + degrees);
//                result = Math.abs((ci.orientation - (180 + degrees)) % 360);
////                result = 90;
////                result = (ci.orientation + degrees) % 360;
////                result = (360 - result) % 360;
//            } else
//            {
//                result = (ci.orientation - degrees + 360) % 360;
//            }
//            Log.d(TAG, "Result orientx = " + result);
//            mCamera.setDisplayOrientation(result);

        return false;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        final Camera.PreviewCallback callback = mPreviewCallback;
        if ( callback != null )
        {
            mPreviewCallback = null;
            callback.onPreviewFrame(bytes, camera);
        }

    }

    public void setPreviewCallbackOnce(Camera.PreviewCallback previewCallback) {
        mPreviewCallback = previewCallback;
    }

}

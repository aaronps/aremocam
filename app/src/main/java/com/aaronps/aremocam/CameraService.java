package com.aaronps.aremocam;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;

public class CameraService extends Service {
    private static final String TAG = "CameraService";

    private Thread        mThread;
    private SimplePreview mSimplePreview;
    private CameraServer  mCameraServer;

    public CameraService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSimplePreview = new SimplePreview(0, this);
        mCameraServer = new CameraServer(mSimplePreview, 19999);

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        mSimplePreview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                  ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams layoutParams =
                new WindowManager.LayoutParams(1, 1,
                                               WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                                               WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                                               PixelFormat.TRANSLUCENT);

        windowManager.addView(mSimplePreview, layoutParams);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mThread == null)
        {
            Log.d(TAG, "Starting Thread");
            mThread = new Thread(mCameraServer);
            mThread.start();
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mThread != null)
        {
            Log.d(TAG, "Stopping Thread");
            try
            {
                mThread.interrupt();
                mThread.join();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                mThread = null;
            }
        }
        Log.d(TAG, "Service destroyed");
    }
}

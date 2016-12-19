package com.aaronps.aremocam;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // these two lines to show the app even when locked...
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        // this one switch the screen on, and keep it on
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(R.id.button_start_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCameraService();
            }
        });

        findViewById(R.id.button_stop_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCameraService();
            }
        });

        startCameraService();

    }

    void startCameraService() {
        Log.d(TAG, "Starting the service");
        final Intent i = new Intent(MainActivity.this, CameraService.class);
        startService(i);
    }

    void stopCameraService() {
        Log.d(TAG, "Stopping the service");
        final Intent i = new Intent(MainActivity.this, CameraService.class);
        stopService(i);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart in");
        super.onRestart();
        Log.d(TAG, "onRestart out");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy in");
        super.onDestroy();
        Log.d(TAG, "onDestroy out");
    }
}

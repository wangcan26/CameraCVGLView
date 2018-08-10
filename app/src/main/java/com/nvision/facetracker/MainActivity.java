package com.nvision.facetracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.nvision.face_tracker_android.R;

public class MainActivity extends AppCompatActivity {

    private CameraRenderView mCameraView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PermissionHelper.requestCameraPermission(this, true);


        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        mCameraView = (CameraRenderView) findViewById(R.id.camera_view);
        mCameraView.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mCameraView != null)
        {
            mCameraView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if(mCameraView != null)
        {
            mCameraView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraView != null){
            mCameraView.deinit();
        }
    }
}

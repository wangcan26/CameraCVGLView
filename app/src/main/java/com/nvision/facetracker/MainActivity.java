package com.nvision.facetracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.nvision.face_tracker_android.R;

import static com.nvision.facetracker.CameraRenderView.IMAGE_HEIGHT;
import static com.nvision.facetracker.CameraRenderView.IMAGE_WIDTH;

public class MainActivity extends AppCompatActivity {



    private CameraRenderView mCameraView;
    private ImageView        mImageView;
    private Bitmap          mTestBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PermissionHelper.requestCameraPermission(this, true);
        setContentView(R.layout.activity_main);

        //Test: A Bitmap for test mat
        //

        // Example of a call to a native method
        mCameraView = (CameraRenderView) findViewById(R.id.camera_view);
        mCameraView.init(this);

        //ImageView Test for mat
        mImageView = (ImageView) findViewById(R.id.image_view);
        new Thread(new Runnable() {
            @Override
            public void run() {

                while(true)
                {
                     mCameraView.testMat(mImageView);
                }

            }
        }).start();

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

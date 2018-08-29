package com.nvision.facetracker;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.nvision.face_tracker_android.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.nvision.facetracker.CameraRenderView.IMAGE_HEIGHT;
import static com.nvision.facetracker.CameraRenderView.IMAGE_WIDTH;

public class MainActivity extends Activity {



    private CameraRenderView mCameraView;
    private ImageView        mImageView;
    private Bitmap           mTestBitmap;
    private boolean          mIsTestImage;

    public enum LIFECYCLE_ORDER{
        CREATE,
        RESUME,
        PAUSE,
        DESTROY
    }
    public static LIFECYCLE_ORDER kOrder;
    public static boolean ACTIVITY_INIT = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(ACTIVITY_INIT) {
            finish();
            return;
        }

        PermissionHelper.requestCameraPermission(this, true);
        setContentView(R.layout.activity_main);

        //First you should copy assets to sdcard
        copyAssets();
        final ExecutorService executorService = Executors.newCachedThreadPool();

        //CameraRenderView
        mCameraView = (CameraRenderView) findViewById(R.id.camera_view);
        mCameraView.init(this);
        //ImageView Test for mat
        mImageView = (ImageView) findViewById(R.id.image_view);
        mIsTestImage = false;
        mTestBitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);

        //Call in a thread that different from ImageReader Callback
        Button capture_button = (Button)findViewById(R.id.capture);
        capture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("MainActivity", "MainActivity ");
                /*executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("MainActivity", "MainActivity Exectutor process testMat");
                        if(mTestBitmap != null &&mTestBitmap.isRecycled())
                        {
                            mTestBitmap.recycle();
                        }
                        mTestBitmap = Bitmap.createBitmap(IMAGE_HEIGHT, IMAGE_WIDTH, Bitmap.Config.ARGB_8888);
                        mCameraView.nativeTestIMage(mTestBitmap);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("MainActivity", "MainActivity onCreate mImageView set ImageBitmap");
                                mImageView.setImageBitmap(mTestBitmap);
                            }
                        });
                    }
                });*/
                mIsTestImage = true;
            }
        });
        kOrder  = LIFECYCLE_ORDER.CREATE;
        ACTIVITY_INIT = true;
        Log.i("MainActivity", "MainActivity Lifecycle onCreate");
    }

    public void testImage(final byte[] data)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i("MainActivity", "MainActivity onCreate mImageView set ImageBitmap");
                //if(mIsTestImage)
                //{
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    mTestBitmap.copyPixelsFromBuffer(buffer);

                    mImageView.setImageBitmap(mTestBitmap);
                    mIsTestImage = false;
                //}

            }
        });
    }

    public void testImage(final  Bitmap bitmap)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mImageView.setImageBitmap(bitmap);
                mIsTestImage = false;


            }
        });
    }

    @Override
    protected void onResume() {


        Log.i("MainActivity", "MainActivity Lifecycle onResume");
        super.onResume();
        if(mCameraView != null)
        {
            mCameraView.onResume();
        }
        kOrder = LIFECYCLE_ORDER.RESUME;


    }

    @Override
    protected void onPause() {
        Log.i("MainActivity", "MainActivity Lifecycle onPause");
        if(mCameraView != null)
        {
            mCameraView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if(mCameraView != null){
            mCameraView.deinit();
        }
        super.onDestroy();
        ACTIVITY_INIT = false;
        Log.i("MainActivity", "MainActivity Lifecycle onDestroy");

    }

    //https://stackoverflow.com/questions/4447477/how-to-copy-files-from-assets-folder-to-sdcard
    private void copyAssets()
    {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try{
            files = assetManager.list("");
        }catch (IOException e){
            Log.e("MainActivity", "Failed to get asset file list.", e);
        }

        if(files != null)for(String filename:files){

            InputStream in = null;
            OutputStream out = null;
            try{
                in = assetManager.open(filename);

                File outFile = new File(getExternalFilesDir(null), filename);
                Log.i("MainActivity", "MainActivity copyAssets to App DIR: " + outFile.getPath());
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            }catch (IOException e){
                Log.e("MainActivity", "Failed to copy asset file : "+ filename, e);
            }
            finally {
                if(in != null){
                    try{
                        in.close();
                    }catch (IOException e){

                    }
                }
                if(out != null){
                    try{
                        out.close();
                    }catch (IOException e){

                    }
                }
            }

        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
}

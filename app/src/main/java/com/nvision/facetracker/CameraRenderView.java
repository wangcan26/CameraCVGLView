package com.nvision.facetracker;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.VolumeShaper;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CameraRenderView extends SurfaceView implements SurfaceHolder.Callback{

    private WeakReference<Activity>     mWeakActivity;
    private CameraManager               mCamManager;
    private CameraDevice                mCamera;
    private String                      mCameraId;

    private CaptureRequest.Builder      mPreviewBuilder;
    private CameraCaptureSession        mCaptureSession;
    private CaptureRequest.Builder      mCaptureRequestBuilder;

    private ImageReader                 mImageReader;

    boolean                     mIsSurfaceAvailable;
    private SurfaceHolder       mSurfaceHolder;
    private Surface             mSurface;
    private SurfaceTexture      mSurfaceTexture;
    private volatile Handler    //Create a handler from the ui thread
            mUIHandler = new Handler();
    private Semaphore           mCameraOpenCloseLock = new Semaphore(1);
    private HandlerThread       mCamSessionThread;
    private Handler             mCamSessionHandler;

    private int                 mSensorOrientation;
    private static final int    MAX_PREVIEW_WIDTH = 1920;
    private static final int    MAX_PREVIEW_HEIGHT = 1080;

    private Size                mPreviewSize;
    private int mWidth, mHeight;

    public static int IMAGE_WIDTH = 640, IMAGE_HEIGHT= 480;
    public static final String CAMERA_FACE_BACK = "" + CameraCharacteristics.LENS_FACING_BACK;
    public static final String CAMERA_FACE_FRONT = "" + CameraCharacteristics.LENS_FACING_FRONT;

    //CameraDevice StateCallback
    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCamera = cameraDevice;
            try{
                createCameraPreviewSession();
            }catch (CameraAccessException e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    };

    // Session State Callback
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if(null == mCamera) return;


            mCaptureSession = cameraCaptureSession;
            try{
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                mPreviewBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 1600);
                //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);

                startPreview(mCaptureSession);
            }catch (CameraAccessException e){
                e.printStackTrace();
            }

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e("CameraRenderView", "onConfigureFailed");
        }
    };


    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            //Log.i("CameraRenderView","CameraCaptureSession Capture Completed");
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

        }

        private void process(CaptureRequest result)
        {
            //Nothing
        }
    };

    private long last_time = System.currentTimeMillis();
    //This is a callback object for ImageReader OnImageAvailble will be called when a still image is ready for process
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            long cur_time = System.currentTimeMillis();
            //Log.i("CameraRenderView", "CameraRenderView OnImageAvailable Since last time " + (cur_time - last_time));
            Image image = imageReader.acquireNextImage();
            last_time = cur_time;
            image.close();

        }
    };

    static {
        System.loadLibrary("nvision_core");
    }

    public CameraRenderView(Context context){
        this(context, null);
    }

    public CameraRenderView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }


    public void init(Activity activity)
    {
        mWeakActivity = new WeakReference<>(activity);
        mSurfaceHolder = this.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setKeepScreenOn(true);

        mIsSurfaceAvailable = false;

        //Create a App
        nativeCreateApp();
    }

    public void onResume()
    {
        nativeResumeApp();
    }

    public void onPause()
    {
        nativePauseApp();
    }

    public void deinit()
    {
        nativeDestroyApp();
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mIsSurfaceAvailable = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        mWidth = width;
        mHeight = height;

        //This method may block the ui thread until the gl context and surface texture id created
        nativeSetSurface(surfaceHolder.getSurface());
        //configure the output sizes for the surfaceTexture and select a id for camera
        configureCamera(width, height);
        //Only First time we open the camera and create imageReader
        if(!mIsSurfaceAvailable)
        {
            if(mCameraId != null)
            {
                startCameraSessionThread();
                openCamera();
            }

            mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCamSessionHandler);

        }
        mIsSurfaceAvailable = true;

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        nativeSetSurface(null);

        closeCamera();
        stopCameraSessionThread();
        destroyPreviewSurface();

        mIsSurfaceAvailable = false;
    }

    private void startCameraSessionThread()
    {
        mCamSessionThread = new HandlerThread("Camera2");
        mCamSessionThread.start();
        mCamSessionHandler = new Handler(mCamSessionThread.getLooper());
    }

    private void stopCameraSessionThread()
    {
        mCamSessionThread.quitSafely();
        try{
            mCamSessionThread.join();
            mCamSessionThread = null;
            mCamSessionHandler = null;
        }catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private boolean isSurfaceAvailable()
    {
        return mSurfaceHolder!= null&&mIsSurfaceAvailable;
    }

    private void createCameraPreviewSession() throws CameraAccessException
    {
        try{

            mSurface = getPreviewSurface(mPreviewSize);
            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); //This must called before createCaptureSession
            mCamera.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()), mSessionStateCallback,null);
        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private  void startPreview(CameraCaptureSession session) throws CameraAccessException{
        //Set Surface of SurfaceView as the target of the builder
        mPreviewBuilder.addTarget(mSurface);
        mPreviewBuilder.addTarget(mImageReader.getSurface());
        session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mCamSessionHandler);
    }

    private void configureCamera(int width, int height)
    {
        //Assume it is a face back camera
        mCameraId = CAMERA_FACE_BACK;
        ///Configure camera output surfaces
        setupCameraOutputs(mWidth, mHeight);
    }

    private void openCamera()
    {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestCameraPermission(getActivity(), true);
            return;
        }

        ///Prepare for camera
        mCamManager = (CameraManager)getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw  new RuntimeException("Time out waiting to lock camera opening.");
            }
        }catch (InterruptedException e){
            throw  new RuntimeException("Interupted while trying to lock camera opening.", e);
        }

        try{

            mCamManager.openCamera(mCameraId, mCameraDeviceCallback, mCamSessionHandler);
        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }


    private void closeCamera()
    {
        try{
            mCameraOpenCloseLock.acquire();

            if(null != mCaptureSession){
                mCaptureSession.close();
                mCaptureSession = null;
            }

            if(null != mCamera){
                mCamera.close();
                mCamera = null;
            }

            if(null != mImageReader)
            {
                mImageReader.close();
                mImageReader = null;
            }
        }catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera closing", e);
        }finally {
            mCameraOpenCloseLock.release();
        }
    }


    private void setupCameraOutputs(int width, int height)
    {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try{

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);

            StreamConfigurationMap map =characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)), new CompareSizesByArea());

            //Find out if we need to swap dimension to get the preview size relative to sensor coordinate
            int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;

            //Log.i("CameraRenderView", "CameraRenderView displayRotation");

            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e("CameraRenderView", "Display rotation is invalid: " + displayRotation);
            }

            //Log.i("CameraRenderView", "CameraRenderView displaySize");
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            Log.i("CameraRenderView", "CameraRenderView PreviewSize");
            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;


        }catch (CameraAccessException e){
            e.printStackTrace();
        }catch (NullPointerException e){
            Log.e("CameraRenderView", "This device doesn't support Camera2 API");
        }
    }


    private Surface getPreviewSurface(Size size){
        if(mSurface == null)
        {
            //Get the SurfaceTexture from SurfaceView GL Context
            mSurfaceTexture = nativeSurfaceTexture(mCameraId == CAMERA_FACE_BACK?true:false);
            mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    nativeRequestUpdateTexture();
                }
            });
            //This is the output surface we need to start preview
            mSurface = new Surface(mSurfaceTexture);
        }

        mSurfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
        return mSurface;
    }

    private void destroyPreviewSurface()
    {
        if(mSurface != null)
        {

            mSurfaceTexture.release();
            nativeDestroyTexture();
            mSurfaceTexture = null;
            mSurface.release();
            mSurface = null;
        }
    }


    private Activity getActivity()
    {
        return mWeakActivity!= null?mWeakActivity.get():null;
    }


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("CameraRenderView", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    static class CompareSizesByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long)lhs.getWidth()*lhs.getHeight() - (long)rhs.getWidth()*rhs.getHeight());
        }
    }


    static native void nativeCreateApp();
    static native void nativeResumeApp();
    static native void nativeSetSurface(Surface surface);
    static native void nativePauseApp();
    static native void nativeDestroyApp();
    static native SurfaceTexture nativeSurfaceTexture(boolean flip);
    static native void nativeRequestUpdateTexture();
    static native void nativeDestroyTexture();
}

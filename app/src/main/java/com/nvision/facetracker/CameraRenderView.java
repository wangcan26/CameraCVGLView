package com.nvision.facetracker;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CameraRenderView extends SurfaceView implements SurfaceHolder.Callback{

    private static final boolean DIRECT_TO_VIEW = false;

    private WeakReference<Activity>     mWeakActivity;
    private CameraManager               mCamManager;
    private CameraDevice                mCamera = null;
    private String                      mCameraId =  null;

    private CaptureRequest.Builder      mPreviewBuilder;
    private CameraCaptureSession        mCaptureSession;

    private ImageReader                 mImageReader;

    boolean                     mIsSurfaceAvailable;
    private SurfaceHolder       mSurfaceHolder;
    private Surface             mSurface;
    private SurfaceTexture      mSurfaceTexture;
    private volatile Handler    //Create a handler from the ui thread
            mUIHandler = new Handler(Looper.getMainLooper());
    private Semaphore           mCameraOpenCloseLock = new Semaphore(1);
    private Object              mThreadLock = new Object();
    private HandlerThread       mCamSessionThread;
    private Handler             mCamSessionHandler;

    private HandlerThread       mImageSessionThread;
    private Handler             mImageSessionHandler;

    private HandlerThread       mCaptureThread;
    private Handler             mCaptureHandler;

    // Durations in nanoseconds
    private static final long MICRO_SECOND = 1000;
    private static final long MILLI_SECOND = MICRO_SECOND * 1000;
    private static final long ONE_SECOND = MILLI_SECOND * 1000;

    private Object              mLock = new Object();

    private int                 mSensorOrientation;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private Size                mPreviewSize;
    private int mWidth,  mHeight;


    public static int IMAGE_WIDTH = 640, IMAGE_HEIGHT= 480;
    public static final String CAMERA_FACE_BACK = "" + CameraCharacteristics.LENS_FACING_BACK;
    public static final String CAMERA_FACE_FRONT = "" + CameraCharacteristics.LENS_FACING_FRONT;


    ExecutorService            mExecutorService;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //CameraDevice StateCallback
    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i("CameraRenderView", "CameraRenderView CameraDevice opened");
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
            Log.i("CameraRenderView", "CameraRenderView CameraDevice onDisconncted");

        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {

            Log.i("CameraRenderView", "CameraRenderView CameraDevice begin");
            mCamera = null;
            mCameraId = null;
            nativeNotifyCameraWait();

            synchronized (mThreadLock)
            {
                mThreadLock.notify();
            }

            Log.i("CameraRenderView", "CameraRenderView CameraDevice closed");

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.i("CameraRenderView", "CameraRenderView CameraDevice Error");
        }


    };

    // Session State Callback
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if(null == mCamera) return;
            mCaptureSession = cameraCaptureSession;

            try{

                Log.i("CameraRenderView", "CameraRenderView createCaptureSession");
                mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //Set Surface of SurfaceView as the target of the builder
                if(DIRECT_TO_VIEW)
                {
                    mPreviewBuilder.addTarget(mSurfaceHolder.getSurface());

                }else{
                    mPreviewBuilder.addTarget(mSurface);
                }
                mPreviewBuilder.addTarget(mImageReader.getSurface());

                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                mPreviewBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 0);
                mPreviewBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, ONE_SECOND/30);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);

                //int rotation = ((WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                //Log.i("CameraRenderView", "CameraRenderView CameraCaptureSession " + rotation);
                //mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
                startPreview(mCaptureSession);

            }catch (CameraAccessException e){
                e.printStackTrace();
            }

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e("CameraRenderView", "onConfigureFailed");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            mCaptureSession = null;
            destroyImageReader();
            if(mCamSessionHandler != null)
            {
                mCamSessionHandler.sendEmptyMessage(MSG_CAM_CLOSE);
            }
            Log.i("CameraRenderView", "CameraRenderView CaptureSession closed");
        }
    };

    private int mCaptureNumber = 0;
    private boolean mCameraReady = false;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            //Log.i("CameraRenderView","CameraCaptureSession Capture Completed");
            if(!mCameraReady)
            {
                if(mCaptureNumber == 4){
                    if(mCamSessionHandler != null) mCamSessionHandler.sendEmptyMessage(MSG_CAM_READY);
                    mCameraReady = true;
                }
                mCaptureNumber++;
            }


        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

        }

        private void process(CaptureRequest result)
        {
            Log.i("CameraRenderView","CameraCaptureSession Capture Process");
            //Nothing
        }
    };

    private long last_time = System.currentTimeMillis();
    private int  duration_time = 0;
    private int  frame_number = 0;
    //This is a callback object for ImageReader OnImageAvailble will be called when a still image is ready for process
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

            last_time = System.currentTimeMillis();
            Image image = imageReader.acquireLatestImage();
            if(image != null)
            {
                imageToYBytes(image);
                image.close();
            }

            long cur_time = System.currentTimeMillis();
            //Compute the fps
            synchronized (mLock)
            {
                duration_time += (cur_time-last_time);
                frame_number++;
            }
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
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.addCallback(this);
        mExecutorService = Executors.newCachedThreadPool();
        //Create a App
        nativeCreateApp(activity.getExternalFilesDir(null).getPath());

    }

    public void onResume()
    {
        Log.i("CameraRenderView", "CameraRenderView resume .....");
        nativeResumeApp();
    }

    public void onPause()
    {
        Log.i("CameraRenderView", "CameraRenderView Pause .....");
        nativePauseApp();

    }

    public void deinit()
    {
        nativeDestroyApp();
    }

    //Call in a thread that different from ImageReader Callback
    public void testMat(final ImageView imageView)
    {
        /*synchronized (mLock)
        {
            if(duration_time > MICRO_SECOND)
            {


                float fps = (float)MICRO_SECOND/frame_number;
                Log.i("CameraRenderView", "CameraRenderView ImageReader imageToByteArray2 " + fps);
                frame_number = 0;
                duration_time = 0;
            }
        }*/
        final Bitmap bitmap = Bitmap.createBitmap(IMAGE_HEIGHT, IMAGE_WIDTH, Bitmap.Config.ARGB_8888);
        nativeTestIMage(bitmap);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i("MainActivity", "MainActivity onCreate mImageView set ImageBitmap");
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i("CameraRenderView", "CameraRenderView surfaceCreated ....");
        mIsSurfaceAvailable = false;
        mCameraId = null;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i("CameraRenderView", "CameraRenderView surfaceChanged ....");
        mWidth = width;
        mHeight = height;

        //This method may block the ui thread until the gl context and surface texture id created
        if(!DIRECT_TO_VIEW)
            nativeSetSurface(surfaceHolder.getSurface());

        configureCamera(width, height);

        if(!mIsSurfaceAvailable)
        {
            mCaptureNumber = 0;
            mCameraReady = false;
            startCameraSessionThread();
            openCamera();
            mIsSurfaceAvailable = true;
        }
        Log.i("CameraRenderView", "CameraRenderView surfaceChanged end....");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i("CameraRenderView", "CameraRenderView surfaceDestroyed ...");

        if(mCamSessionHandler != null)
        {
            mCamSessionHandler.sendEmptyMessage(MSG_SESSION_CLOSE);
        }
        //Wait for CameraSession closed

        if(mCaptureSession != null)
        {
            synchronized (mThreadLock)
            {
                try {
                    mThreadLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        stopCameraSessionThread();
        //Release GL Resources
        if(!DIRECT_TO_VIEW)
        {
            nativeSetSurface(null);
            destroySurfaceTexture();

        }
        Log.i("CameraRenderView", "CameraRenderView surfaceDestroyed end...");
    }

    private static final int MSG_CAM_CLOSE = 0;
    private static final int MSG_SESSION_CLOSE = 1;
    private static final int MSG_CAM_READY = 2;
    private void startCameraSessionThread()
    {
        mCamSessionThread = new HandlerThread("Camera2");
        mCamSessionThread.start();
        mCamSessionHandler = new Handler(mCamSessionThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what)
                {
                    case MSG_CAM_CLOSE:
                        closeCamera();
                        break;
                    case MSG_SESSION_CLOSE:
                        closeCaptureSession();
                        break;
                    case MSG_CAM_READY:
                        nativeNotifyCameraReady();
                        break;
                }


                return true;
            }
        });

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

    private void startImageWorkerThread()
    {
        Log.i("CameraRenderView", "CameraRenderView CreateImageWorkThread");
        mImageSessionThread = new HandlerThread("ImageSession");
        mImageSessionThread.start();
        mImageSessionHandler = new Handler(mImageSessionThread.getLooper());
    }

    private void stopImageWorkerThread()
    {
        if(mImageSessionThread != null)
        {
            mImageSessionThread.quitSafely();
            try{

                mImageSessionThread.join();
                mImageSessionThread = null;
                mImageSessionHandler = null;
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    private void startCaptureThread()
    {
        mCaptureThread = new HandlerThread("Capture");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());
    }


    private void stopCaptureThread()
    {
        if(mCaptureThread != null)
        {
            mCaptureThread.quitSafely();
            try {
                mCaptureThread.join();
                mCaptureHandler = null;
                mCaptureThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isSurfaceAvailable()
    {
        return mSurfaceHolder!= null&&mIsSurfaceAvailable;
    }

    private void createCameraPreviewSession() throws CameraAccessException
    {
        try{

            //Get the SurfaceTexture from SurfaceView GL Context
            if(!DIRECT_TO_VIEW)
            {
                mSurfaceTexture = getSurfaceTexture();
                mSurface = new Surface(mSurfaceTexture);
            }
            startImageWorkerThread();
            createImageReader();

            List<Surface> outputs = null;
            if(DIRECT_TO_VIEW)
            {
                outputs = Arrays.asList(
                        mSurfaceHolder.getSurface(), mImageReader.getSurface());
            }else{
                outputs = Arrays.asList(
                        mSurface, mImageReader.getSurface());
            }

            mCamera.createCaptureSession(outputs, mSessionStateCallback, mCamSessionHandler);
        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void closeCaptureSession()
    {
        stopImageWorkerThread(); ///Stop send buffer to native, then destroy image reader


        if(mCaptureSession !=  null)
        {
            mCaptureSession.close();
        }


        Log.i("CameraRenderView", "CameraRenderView close CaptureSession .....");

    }

    private  void startPreview(final CameraCaptureSession session) throws CameraAccessException{
        session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mUIHandler); //Must mCamSessionHandler

        Log.i("CameraRenderView", "CameraRenderView startPreview ...");
    }

    private void configureCamera(int width, int height)
    {
        //Prepare for camera
        mCameraId = CAMERA_FACE_BACK;
        //Prepare for ImageReader
        setupCameraOutputs(width, height);
    }

    private void openCamera()
    {
        Log.i("CameraRenderView", "CameraRenderView openCamera begin ...");
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestCameraPermission(getActivity(), true);
            return;
        }

        mCamManager = (CameraManager)getActivity().getSystemService(Context.CAMERA_SERVICE);
        try{
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw  new RuntimeException("Time out waiting to lock camera opening.");
            }
            mCamManager.openCamera(mCameraId,  mCameraDeviceCallback, mCamSessionHandler);

        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }catch (InterruptedException e){
            throw  new RuntimeException("Interupted while trying to lock camera opening.", e);
        }
    }


    private void closeCamera()
    {
        Log.i("CameraRenderView", "CameraRenderView closeCamera begin ...");
        try{
            mCameraOpenCloseLock.acquire();
            if(null != mCamera) {
                mCamera.close();
            }
        }catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera closing", e);
        }finally {
            mCameraOpenCloseLock.release();
        }
        Log.i("CameraRenderView", "CameraRenderView closeCamera end ...");
    }


    private void setupCameraOutputs(int width, int height)
    {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try{

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);

            if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY))
            {
                Log.i("CameraRenderView", "CameraRenderview support hardware legacy");
            }else if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED))
            {
                Log.i("CameraRenderView", "CameraRenderview support hardware limited");
            }
            else if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL))
            {
                Log.i("CameraRenderView", "CameraRenderview support hardware full");
            }else if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3))
            {
                Log.i("CameraRenderView", "CameraRenderview support hardware level 3");
            }

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;

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
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    swappedDimensions = true;
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

            Log.i("CameraRenderView", "CameraRenderView PreviewSize " + width + " " + height + " "+ rotatedPreviewWidth + " " + rotatedPreviewHeight + " "
                    + displayRotation + " " + mSensorOrientation);


            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            if(DIRECT_TO_VIEW)
            {
                /*mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceHolder.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);*/
                mPreviewSize = chooseBigEnoughSize(map.getOutputSizes(SurfaceHolder.class),
                        rotatedPreviewWidth, rotatedPreviewHeight);
            }else{
                mPreviewSize = chooseBigEnoughSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight);
            }

            switch (mSensorOrientation) {
                case 0:
                case 180:
                    if(DIRECT_TO_VIEW){
                        mSurfaceHolder.setFixedSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                    }
                    break;
                case 90:
                case 270:
                    if(DIRECT_TO_VIEW){
                        mSurfaceHolder.setFixedSize(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                    break;
                default:
                    Log.e("CameraRenderView", "Display rotation is invalid: " + displayRotation);
            }



        }catch (CameraAccessException e){
            e.printStackTrace();
        }catch (NullPointerException e){
            Log.e("CameraRenderView", "This device doesn't support Camera2 API");
        }
        Log.i("CameraRenderView", "CameraRenderView configure Camera end");
    }


    boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        final int[] sortedHwLevels = {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        };
        int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (requiredLevel == deviceLevel) {
            return true;
        }

        for (int sortedlevel : sortedHwLevels) {
            if (sortedlevel == requiredLevel) {
                return true;
            } else if (sortedlevel == deviceLevel) {
                return false;
            }
        }
        return false; // Should never reach here
    }

    private void createImageReader()
    {
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mImageSessionHandler);

    }

    private void destroyImageReader()
    {
        if(null != mImageReader)
        {
            mImageReader.setOnImageAvailableListener(null, mCamSessionHandler);
            mImageReader.close();
            mImageReader = null;
        }
    }


    private SurfaceTexture getSurfaceTexture(){
        if(mSurfaceTexture == null)
        {
            mSurfaceTexture = nativeSurfaceTexture(mCameraId == CAMERA_FACE_BACK?true:false);
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    nativeRequestUpdateTexture();
                }
            });
            //This is the output surface we need to start preview
        }
        return mSurfaceTexture;
    }

    private void destroySurfaceTexture()
    {
        if(mSurfaceTexture != null)
        {
            mSurfaceTexture.release();
            mSurfaceTexture.setOnFrameAvailableListener(null);
            nativeDestroyTexture();
            mSurfaceTexture = null;
        }

        if(mSurface != null)
        {
            mSurface.release();
            mSurface = null;
        }
    }

    private Activity getActivity()
    {
        return mWeakActivity!= null?mWeakActivity.get():null;
    }


    static Size chooseBigEnoughSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
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

    //http://www.mclover.cn/blog/index.php/archives/206.html
    private static Bitmap imageToBitmap(Image image)
    {
        Log.i("CameraRenderView", "CameraRenderView imageToBitmap begin");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
        byte[] data0 = new byte[bufferY.remaining()];
        bufferY.get(data0);

        ByteBuffer bufferU = image.getPlanes()[1].getBuffer();
        byte[] data1 = new byte[bufferU.remaining()];
        bufferU.get(data1);

        ByteBuffer bufferV = image.getPlanes()[2].getBuffer();
        byte[] data2 = new byte[bufferV.remaining()];
        bufferV.get(data2);

        try {
            outputBytes.write(data0);
            outputBytes.write(data2);
            outputBytes.write(data1);
        }catch (IOException e)
        {
            e.printStackTrace();
        }
        Log.i("CameraRenderView", "CameraRenderView YUV convert jpeg begin " + image.getWidth() + " " + image.getHeight());

        //Convert YUV to Jpeg
        final YuvImage yuvImage = new YuvImage(outputBytes.toByteArray(), ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream outBitmap = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 95, outBitmap);
        Bitmap bitmap = BitmapFactory.decodeByteArray(outBitmap.toByteArray(), 0, outBitmap.size());

        Log.i("CameraRenderView", "CameraRenderView YUV convert jpeg end");

        return bitmap;
    }

    private static void imageToYBytes(Image image)
    {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height];// * ImageFormat.getBitsPerPixel(format) / 8
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        // only yuv
        channelOffset = 0;
        outputStride = 1;

        ByteBuffer buffer = planes[0].getBuffer();
        int rowStride = planes[0].getRowStride();
        int pixelStride = planes[0].getPixelStride();

        int w = width;
        int h = height;
        //offset of the yuv plane
        buffer.position(rowStride * (crop.top) + pixelStride * (crop.left));
        //TODO: RenderScript
        for (int row = 0; row < h-1; row++) {
            int length;

            length = w;
            buffer.get(data, channelOffset, length);
            channelOffset += length;

            buffer.position(buffer.position() + rowStride - length);
        }
        //row = h-1
        buffer.get(data, channelOffset, w);


        data = rotateYDegree90(data, width, height);

        nativeProcessImage(height, width, data);
    }

    //https://www.polarxiong.com/archives/Android-YUV_420_888%E7%BC%96%E7%A0%81Image%E8%BD%AC%E6%8D%A2%E4%B8%BAI420%E5%92%8CNV21%E6%A0%BC%E5%BC%8Fbyte%E6%95%B0%E7%BB%84.html
    private static void imageToByteArray2(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height* ImageFormat.getBitsPerPixel(format) / 8];//
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        // only yuv
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height;
                    outputStride = 1;
                    break;
                case 2:
                    channelOffset = (int) (width * height * 1.25);
                    outputStride = 1;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));

            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }

        data = rotateYUV420Degree90(data, width, height);
        //Log.i("CameraRenderView", "CameraRenderView image width-height : " + image.getWidth() + " " + image.getHeight());

        nativeProcessImage(width, height, data);
    }


    private static void imageToByteArray(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }


        nativeProcessImage(width, height, data);
    }


    public static byte[] rotateYUV420Degree270(byte[] data, int imageWidth,
                                               int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int nWidth = 0, nHeight = 0;
        int wh = 0;
        int uvHeight = 0;
        if (imageWidth != nWidth || imageHeight != nHeight) {
            nWidth = imageWidth;
            nHeight = imageHeight;
            wh = imageWidth * imageHeight;
            uvHeight = imageHeight >> 1;// uvHeight = height / 2
        }
        // ??Y
        int k = 0;
        for (int i = 0; i < imageWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < imageHeight; j++) {
                yuv[k] = data[nPos + i];
                k++;
                nPos += imageWidth;
            }
        }
        for (int i = 0; i < imageWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                yuv[k] = data[nPos + i];
                yuv[k + 1] = data[nPos + i + 1];
                k += 2;
                nPos += imageWidth;
            }
        }
        return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
    }

    private static byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int i = 0;
        int count = 0;
        for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
            yuv[count] = data[i];
            count++;
        }
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count++] = data[i - 1];
            yuv[count++] = data[i];
        }
        return yuv;
    }

    public static byte[] rotateYDegree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight]; // * 3 / 2
        // Rotate the Y luma
        int i = 0;
        //TODO: RenderScript
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        return yuv;
    }

    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2]; //
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth)
                        + (x - 1)];
                i--;
            }
        }
        return yuv;
    }





    static class CompareSizesByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long)lhs.getWidth()*lhs.getHeight() - (long)rhs.getWidth()*rhs.getHeight());
        }
    }

    static native void nativeCreateApp(String appPath);
    static native void nativeResumeApp();
    static native void nativeSetSurface(Surface surface);
    static native void nativePauseApp();
    static native void nativeDestroyApp();
    static native void nativeNotifyCameraReady();
    static native void nativeNotifyCameraWait();
    static native SurfaceTexture nativeSurfaceTexture(boolean flip);
    static native void nativeRequestUpdateTexture();
    static native void nativeDestroyTexture();

    static native void nativeProcessImage(int width, int height, byte[] data);
    static native void nativeTestIMage(Bitmap bitmap);
}

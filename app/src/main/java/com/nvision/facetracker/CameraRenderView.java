package com.nvision.facetracker;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
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
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;

import com.nvision.face_tracker_android.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
            mUIHandler = new Handler(Looper.getMainLooper());
    private Semaphore           mCameraOpenCloseLock = new Semaphore(1);
    private HandlerThread       mCamSessionThread;
    private Handler             mCamSessionHandler;

    private HandlerThread       mImageSessionThread;
    private Handler             mImageSessionHandler;

    // Durations in nanoseconds
    private static final long MICRO_SECOND = 1000;
    private static final long MILLI_SECOND = MICRO_SECOND * 1000;
    private static final long ONE_SECOND = MILLI_SECOND * 1000;

    private Bitmap              mBitmap;
    private Object              mLock = new Object();
    private HandlerThread       mImageThread;
    private Handler             mImageHandler;
    private static final int    MSG_IMAGE_PROCESS = 0;

    private int                 mSensorOrientation;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private Size                mPreviewSize;
    private int mWidth, mHeight;


    public static int IMAGE_WIDTH = 640, IMAGE_HEIGHT= 480;
    public static final String CAMERA_FACE_BACK = "" + CameraCharacteristics.LENS_FACING_BACK;
    public static final String CAMERA_FACE_FRONT = "" + CameraCharacteristics.LENS_FACING_FRONT;

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
                mPreviewBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, ONE_SECOND/30);
                //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);

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
    private int  duration_time = 0;
    private int  frame_number = 0;
    //This is a callback object for ImageReader OnImageAvailble will be called when a still image is ready for process
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

            Message msg = mImageHandler.obtainMessage(MSG_IMAGE_PROCESS, imageReader);
            mImageHandler.sendMessage(msg);

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

        //Create Image Worker thread
        startImageWorkerThread();

        //Create a App
        nativeCreateApp();
    }

    public void onResume()
    {
        startCameraSessionThread();
        if(isSurfaceAvailable()) openCamera();
        else mSurfaceHolder.addCallback(this);
        nativeResumeApp();
    }

    public void onPause()
    {
        closeCamera();
        stopCameraSessionThread();
        nativePauseApp();
    }

    public void deinit()
    {
        nativeDestroyTexture();
        nativeDestroyApp();

        stopImageWorkerThread();
    }

    //Call in a thread that different from ImageReader Callback
    public void testMat(final ImageView imageView)
    {
        synchronized (mLock)
        {
            if(duration_time > MICRO_SECOND)
            {
                final Bitmap bitmap = Bitmap.createBitmap(IMAGE_HEIGHT, IMAGE_WIDTH, Bitmap.Config.ARGB_8888);
                nativeTestIMage(bitmap);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("MainActivity", "MainActivity onCreate mImageView set ImageBitmap");
                        imageView.setImageBitmap(bitmap);
                    }
                });

                float fps = (float)MICRO_SECOND/frame_number;
                Log.i("CameraRenderView", "CameraRenderView ImageReader imageToByteArray2 " + fps);
                frame_number = 0;
                duration_time = 0;
            }
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        mWidth = width;
        mHeight = height;

        //This method may block the ui thread until the gl context and surface texture id created
        nativeSetSurface(surfaceHolder.getSurface());
        mIsSurfaceAvailable = true;

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        nativeSetSurface(null);
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

    private void startImageWorkerThread()
    {

        mImageSessionThread = new HandlerThread("ImageSession");
        mImageSessionThread.start();
        mImageSessionHandler = new Handler(mImageSessionThread.getLooper());

        mImageThread = new HandlerThread("ImageProcess");
        mImageThread.start();
        mImageHandler = new Handler(mImageThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what){
                    case MSG_IMAGE_PROCESS:
                        last_time = System.currentTimeMillis();
                        Log.i("CameraRenderView", "Image Worker Process Image");
                        ImageReader imageReader = (ImageReader) message.obj;
                        Image image = imageReader.acquireLatestImage();

                        imageToYBytes(image);
                        image.close();
                        mImageHandler.removeMessages(MSG_IMAGE_PROCESS);

                        long cur_time = System.currentTimeMillis();
                        Log.i("CameraRenderView", "CameraRenderView Process Time one Frame: " + (cur_time-last_time));

                        //Compute the fps
                        synchronized (mLock)
                        {
                            duration_time += (cur_time-last_time);
                            frame_number++;
                        }
                        break;
                }
                return false;
            }
        });
    }

    private void stopImageWorkerThread()
    {
        mImageThread.quitSafely();
        try{
            mImageThread.join();
            mImageThread = null;
            mImageHandler = null;
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
            //Get the SurfaceTexture from SurfaceView GL Context
            SurfaceTexture texture = nativeSurfaceTexture(mCameraId == CAMERA_FACE_BACK?true:false);

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            texture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    nativeRequestUpdateTexture();
                }
            });
            //This is the output surface we need to start preview
            Surface surface = new Surface(texture);

            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //Set Surface of SurfaceView as the target of the builder
            mPreviewBuilder.addTarget(surface);
            mPreviewBuilder.addTarget(mImageReader.getSurface());
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mCamSessionHandler);
        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private  void startPreview(final CameraCaptureSession session) throws CameraAccessException{
        session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mUIHandler);
    }


    private void openCamera()
    {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestCameraPermission(getActivity(), true);
            return;
        }

        //Prepare for camera
        mCamManager = (CameraManager)getActivity().getSystemService(Context.CAMERA_SERVICE);
        mCameraId = CAMERA_FACE_BACK;
        //Prepare for ImageReader
        setupCameraOutputs(mWidth, mHeight);

        try{
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw  new RuntimeException("Time out waiting to lock camera opening.");
            }
            mCamManager.openCamera(mCameraId, mCameraDeviceCallback, mCamSessionHandler);
        }catch (CameraAccessException e)
        {
            e.printStackTrace();
        }catch (InterruptedException e){
            throw  new RuntimeException("Interupted while trying to lock camera opening.", e);
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

            mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,ImageFormat.YUV_420_888, 2);

            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mImageSessionHandler);

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
        //Log.i("CameraRenderView", "CameraRenderView image width-height : " + image.getWidth() + " " + image.getHeight());

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


    static native void nativeCreateApp();
    static native void nativeResumeApp();
    static native void nativeSetSurface(Surface surface);
    static native void nativePauseApp();
    static native void nativeDestroyApp();
    static native SurfaceTexture nativeSurfaceTexture(boolean flip);
    static native void nativeRequestUpdateTexture();
    static native void nativeDestroyTexture();

    static native void nativeProcessImage(int width, int height, byte[] data);
    static native void nativeTestIMage(Bitmap bitmap);
}

package com.otaliastudios.cameraview.demo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import androidx.annotation.NonNull;

import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Preview;
import com.otaliastudios.cameraview.filter.Filters;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Handler.Callback;

//ffmpeg
import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

//apache commons imaging
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;


public class CameraActivity extends AppCompatActivity implements View.OnClickListener, OptionView.Callback, SensorEventListener {

    private final static CameraLogger LOG = CameraLogger.create("DemoApp");
    private final static boolean USE_FRAME_PROCESSOR = true;
    private final static boolean DECODE_BITMAP = false;

    private CameraView camera;
    private ViewGroup controlPanel;
    private long mCaptureTime;

    private int mCurrentFilter = 0;
    private final Filters[] mAllFilters = Filters.values();

    public static String initialFolderPath = "/sdcard/Frames";
    public static String videoFilePath;

    public static SensorManager sensorManager;

    Sensor accelerometer;
    Sensor gyroscope;

    float[] accelerometerReading = new float[3];
    float[] gyroscopeReading = new float[3];

    Multimap<Integer,float[]> sensorReadingHolder = ArrayListMultimap.create();

    Map<Integer,float[]> sensorAccelReadingHolder = new HashMap<>();
    Map<Integer,float[]> sensorGyroReadingHolder = new HashMap<>();

    // currentTimeStamp = Long.toString(System.currentTimeMillis()); //need to change to actual timer instead of current system time, start when video starts
    Timer sensorReadTimer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


        setContentView(R.layout.activity_camera);
        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE);

        camera = findViewById(R.id.camera);
        camera.setLifecycleOwner(this);
        camera.addCameraListener(new Listener());


        if (USE_FRAME_PROCESSOR) {
            camera.addFrameProcessor(new FrameProcessor() {
                private long lastTime = System.currentTimeMillis();

                @Override
                public void process(@NonNull Frame frame) {
                    long newTime = frame.getTime();
                    long delay = newTime - lastTime;
                    lastTime = newTime;
                    //LOG.v("Frame delayMillis:", delay, "FPS:", 1000 / delay);
                    if (DECODE_BITMAP) {
                        if (frame.getFormat() == ImageFormat.NV21
                                && frame.getDataClass() == byte[].class) {
                            byte[] data = frame.getData();
                            YuvImage yuvImage = new YuvImage(data,
                                    frame.getFormat(),
                                    frame.getSize().getWidth(),
                                    frame.getSize().getHeight(),
                                    null);
                            ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
                            yuvImage.compressToJpeg(new Rect(0, 0,
                                    frame.getSize().getWidth(),
                                    frame.getSize().getHeight()), 100, jpegStream);
                            byte[] jpegByteArray = jpegStream.toByteArray();
                            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray,
                                    0, jpegByteArray.length);
                            //noinspection ResultOfMethodCallIgnored
                            bitmap.toString();
                        }
                    }
                }
            });
        }

        findViewById(R.id.edit).setOnClickListener(this);
        findViewById(R.id.capturePicture).setOnClickListener(this);
        findViewById(R.id.capturePictureSnapshot).setOnClickListener(this);
        findViewById(R.id.captureVideo).setOnClickListener(this);
        findViewById(R.id.captureVideoSnapshot).setOnClickListener(this);
        findViewById(R.id.toggleCamera).setOnClickListener(this);
        findViewById(R.id.changeFilter).setOnClickListener(this);

        controlPanel = findViewById(R.id.controls);
        ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
        final View watermark = findViewById(R.id.watermark);

        List<Option<?>> options = Arrays.asList(
                // Layout
                new Option.Width(), new Option.Height(),
                // Engine and preview
                new Option.Mode(), new Option.Engine(), new Option.Preview(),
                // Some controls
                new Option.Flash(), new Option.WhiteBalance(), new Option.Hdr(),
                new Option.PictureMetering(), new Option.PictureSnapshotMetering(),
                new Option.PictureFormat(),
                // Video recording
                new Option.PreviewFrameRate(), new Option.VideoCodec(), new Option.Audio(), new Option.AudioCodec(),
                // Gestures
                new Option.Pinch(), new Option.HorizontalScroll(), new Option.VerticalScroll(),
                new Option.Tap(), new Option.LongTap(),
                // Watermarks
                new Option.OverlayInPreview(watermark),
                new Option.OverlayInPictureSnapshot(watermark),
                new Option.OverlayInVideoSnapshot(watermark),
                // Frame Processing
                new Option.FrameProcessingFormat(),
                // Other
                new Option.Grid(), new Option.GridColor(), new Option.UseDeviceOrientation()
        );
        List<Boolean> dividers = Arrays.asList(
                // Layout
                false, true,
                // Engine and preview
                false, false, true,
                // Some controls
                false, false, false, false, false, true,
                // Video recording
                false, false, false, true,
                // Gestures
                false, false, false, false, true,
                // Watermarks
                false, false, true,
                // Frame Processing
                true,
                // Other
                false, false, true
        );
        for (int i = 0; i < options.size(); i++) {
            OptionView view = new OptionView(this);
            //noinspection unchecked
            view.setOption(options.get(i), this);
            view.setHasDivider(dividers.get(i));
            group.addView(view,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        controlPanel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
                b.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        // Animate the watermark just to show we record the animation in video snapshots
        ValueAnimator animator = ValueAnimator.ofFloat(1F, 0.8F);
        animator.setDuration(300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scale = (float) animation.getAnimatedValue();
                watermark.setScaleX(scale);
                watermark.setScaleY(scale);
                watermark.setRotation(watermark.getRotation() + 2);
            }
        });
        animator.start();

    }

    private class Listener extends CameraListener {

        @Override
        public void onVideoRecordingEnd() {
            super.onVideoRecordingEnd();
            Message aMessage = new Message();
            Bundle b = new Bundle();
            b.putString("Video","Stop");
            aMessage.setData(b);
            handler.sendMessage(aMessage);
            System.out.println(aMessage.getWhen());
            //message("Video taken. Processing...", false);
            LOG.w("onVideoRecordingEnd!");
            // Sometime longer recording times cause issues going straight to frame split
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            File afile = new File(videoFilePath);
            getFramesFromVideo(afile);
        }

        @Override
        public void onCameraOpened(@NonNull CameraOptions options) {
            ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
            for (int i = 0; i < group.getChildCount(); i++) {
                OptionView view = (OptionView) group.getChildAt(i);
                view.onCameraOpened(camera, options);
            }
        }

        @Override
        public void onCameraError(@NonNull CameraException exception) {
            super.onCameraError(exception);
            message("Got CameraException #" + exception.getReason(), true);
        }

        @Override
        public void onPictureTaken(@NonNull PictureResult result) {
            super.onPictureTaken(result);
            if (camera.isTakingVideo()) {
                message("Captured while taking video. Size=" + result.getSize(), false);
                return;
            }

            // This can happen if picture was taken with a gesture.
            long callbackTime = System.currentTimeMillis();
            if (mCaptureTime == 0) mCaptureTime = callbackTime - 300;
            LOG.w("onPictureTaken called! Launching activity. Delay:", callbackTime - mCaptureTime);
            PicturePreviewActivity.setPictureResult(result);
            Intent intent = new Intent(CameraActivity.this, PicturePreviewActivity.class);
            intent.putExtra("delay", callbackTime - mCaptureTime);
            startActivity(intent);
            mCaptureTime = 0;
            LOG.w("onPictureTaken called! Launched activity.");
        }

        @Override
        public void onVideoTaken(@NonNull VideoResult result) {
            super.onVideoTaken(result);
            LOG.w("onVideoTaken called! Launching activity.");
            VideoPreviewActivity.setVideoResult(result);
            Intent intent = new Intent(CameraActivity.this, VideoPreviewActivity.class);
            startActivity(intent);
            LOG.w("onVideoTaken called! Launched activity.");
        }

        @Override
        public void onVideoRecordingStart() {
            super.onVideoRecordingStart();
            LOG.w("onVideoRecordingStart!");
            Message aMessage = new Message();
            Bundle b = new Bundle();
            b.putString("Video","Start");
            aMessage.setData(b);
            handler.sendMessage(aMessage);
        }

        @Override
        public void onExposureCorrectionChanged(float newValue, @NonNull float[] bounds, @Nullable PointF[] fingers) {
            super.onExposureCorrectionChanged(newValue, bounds, fingers);
            message("Exposure correction:" + newValue, false);
        }

        @Override
        public void onZoomChanged(float newValue, @NonNull float[] bounds, @Nullable PointF[] fingers) {
            super.onZoomChanged(newValue, bounds, fingers);
            message("Zoom:" + newValue, false);
        }
    }

    private void captureVideo() {
        if (camera.getMode() == Mode.PICTURE) {
            message("Can't record HQ videos while in PICTURE mode.", false);
            return;
        }
        if (camera.isTakingPicture() || camera.isTakingVideo()) return;
        try {
            Date currentTime = Calendar.getInstance().getTime();
            String videoFileName = Long.toString(currentTime.getTime())+".mp4";
            camera.takeVideo(new File(initialFolderPath, videoFileName), 200); // sort this out at some point!!! should be linked with stop recording button not timer
            videoFilePath = initialFolderPath + "/" + videoFileName;
            Log.i(Config.TAG, "video path is: "+ videoFilePath);
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Cannot write text to External Storage!", Toast.LENGTH_SHORT).show();
        }
    }

    private void getFramesFromVideo(File movieFile){
        MediaInformation info = FFprobe.getMediaInformation(movieFile.getPath());
        System.out.println(info);

        //int returnCode = FFmpeg.execute("-skip_frame nokey -i " + videoFilePath + " -vsync 0 -frame_pts true "+ initialFolderPath +"/out%d.jpg");
        //int returnCode = FFmpeg.execute("-i " + videoFilePath + " -r 30/1 "+ initialFolderPath +"/out%03d.jpg");
        int returnCode = FFmpeg.execute("-i " + videoFilePath + " -r 30/1 "+ initialFolderPath +"/original%d.jpg");

        if (returnCode == RETURN_CODE_SUCCESS) {
            Log.i(Config.TAG, "Async command execution completed successfully.");
        } else if (returnCode == RETURN_CODE_CANCEL) {
            Log.i(Config.TAG, "Async command execution cancelled by user.");
        } else {
            Log.i(Config.TAG, String.format("Async command execution failed with rc=%d.", returnCode));
        }

        try {
            ffmpegRotate();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /*
    Uses FFmpeg to rotate images, either horizontally, vertically or both
     */

    private void ffmpegRotate() throws IOException {
        Random random = new Random();
        int returnCode;

        File directory = new File(initialFolderPath);
        File[] files = directory.listFiles();

        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".jpg")) {
                try {
                    if(files[i].getName().endsWith(Integer.toString(i) + ".jpg")){
                        accelerometerReading = sensorAccelReadingHolder.get(i);
                        gyroscopeReading = sensorGyroReadingHolder.get(i);
                        ExifDataHandler.addData(files[i].getAbsoluteFile(),accelerometerReading,gyroscopeReading);
                    }
                } catch (ImageReadException e) {
                    e.printStackTrace();
                } catch (ImageWriteException e) {
                    e.printStackTrace();
                }
            }
        }

        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".jpg")) {
                String imageFilePath = files[i].getPath();
                int orientation = random.nextInt(3 - 0);
                switch (orientation) {
                    case 0:
                        returnCode = FFmpeg.execute("-i " + imageFilePath + " -vf hflip" + " " + initialFolderPath + "/flipped" + i + ".jpg");
                        break;
                    case 1: // flip vertical
                        returnCode = FFmpeg.execute("-i " + imageFilePath + " -vf vflip" + " " + initialFolderPath + "/flipped" + i + ".jpg");
                        System.out.println("Case 1");
                        break;
                    case 2: //flip horizontal
                        returnCode = FFmpeg.execute("-i " + imageFilePath + " -vf hflip,vflip" + " " + initialFolderPath + "/flipped" + i + ".jpg");
                        System.out.println("Case 2");
                        break;
                    default:
                        throw new IllegalStateException("Unexpected ffmpeg return code");
                }
                    if (returnCode == RETURN_CODE_SUCCESS) {
                        Log.i(Config.TAG, "Async command execution completed successfully.");
                    } else if (returnCode == RETURN_CODE_CANCEL) {
                        Log.i(Config.TAG, "Async command execution cancelled by user.");
                    } else {
                        Log.i(Config.TAG, String.format("Async command execution failed with rc=%d.", returnCode));
                    }
                }
            }
        }

     public void startSensorTimer(){
        sensorReadTimer = new Timer();
        sensorReadTimer.scheduleAtFixedRate(new TimerTask() {
            int counter = 0;
            @Override
            public void run() {
                sensorAccelReadingHolder.put(counter,accelerometerReading);
                sensorGyroReadingHolder.put(counter,gyroscopeReading);
                counter++;
            }
        }, 0,333); //33ms for 30fps footage
     }

     public void stopSensorTimer(){
        sensorReadTimer.cancel();
     }


    /*
    All event listeners and handlers
    */

    final Handler handler = new Handler(new Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            Bundle b = new Bundle();
            b = msg.getData();
            String value = b.getString("Video");
            switch (value){
                case "Start":
                    startSensorTimer();
                    break;
                case "Stop":
                    stopSensorTimer();
                    break;
                default:
                    System.out.println("No message match found");
            }
            return false;
        }
    });

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener( this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener( this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener((SensorEventListener) this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
                break;
            }
            case Sensor.TYPE_GAME_ROTATION_VECTOR: {
                System.arraycopy(event.values, 0, gyroscopeReading, 0, gyroscopeReading.length);
                System.out.println("gyroscope x =" + gyroscopeReading[0] + ",y= " + gyroscopeReading[1] + ",z= " + gyroscopeReading[2]);
                break;
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit: edit(); break;
            case R.id.capturePicture: capturePicture(); break;
            case R.id.capturePictureSnapshot: capturePictureSnapshot(); break;
            case R.id.captureVideo: captureVideo(); break;
            case R.id.captureVideoSnapshot: captureVideoSnapshot(); break;
            case R.id.toggleCamera: toggleCamera(); break;
            case R.id.changeFilter: changeCurrentFilter(); break;
        }
    }

    @Override
    public void onBackPressed() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        if (b.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            b.setState(BottomSheetBehavior.STATE_HIDDEN);
            return;
        }
        super.onBackPressed();
    }

    private void edit() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        b.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    /*
    All basic camera functions
     */

    private void capturePicture() {
        if (camera.getMode() == Mode.VIDEO) {
            message("Can't take HQ pictures while in VIDEO mode.", false);
            return;
        }
        if (camera.isTakingPicture()) return;
        mCaptureTime = System.currentTimeMillis();
        message("Capturing picture...", false);
        camera.takePicture();
    }

    private void capturePictureSnapshot() {
        if (camera.isTakingPicture()) return;
        if (camera.getPreview() != Preview.GL_SURFACE) {
            message("Picture snapshots are only allowed with the GL_SURFACE preview.", true);
            return;
        }
        mCaptureTime = System.currentTimeMillis();
        message("Capturing picture snapshot...", false);
        camera.takePictureSnapshot();
    }

    private void captureVideoSnapshot() {
        if (camera.isTakingVideo()) {
            message("Already taking video.", false);
            return;
        }
        if (camera.getPreview() != Preview.GL_SURFACE) {
            message("Video snapshots are only allowed with the GL_SURFACE preview.", true);
            return;
        }
        message("Recording snapshot for 5 seconds...", true);
        camera.takeVideoSnapshot(new File(getFilesDir(), "video.mp4"), 1000);
    }

    private void toggleCamera() {
        if (camera.isTakingPicture() || camera.isTakingVideo()) return;
        switch (camera.toggleFacing()) {
            case BACK:
                message("Switched to back camera!", false);
                break;
            case FRONT:
                message("Switched to front camera!", false);
                break;
        }
    }

    private void changeCurrentFilter() {
        if (camera.getPreview() != Preview.GL_SURFACE) {
            message("Filters are supported only when preview is Preview.GL_SURFACE.", true);
            return;
        }
        if (mCurrentFilter < mAllFilters.length - 1) {
            mCurrentFilter++;
        } else {
            mCurrentFilter = 0;
        }
        Filters filter = mAllFilters[mCurrentFilter];
        message(filter.toString(), false);

        // Normal behavior:
        camera.setFilter(filter.newInstance());
    }

    @Override
    public <T> boolean onValueChanged(@NonNull Option<T> option, @NonNull T value, @NonNull String name) {
        if ((option instanceof Option.Width || option instanceof Option.Height)) {
            Preview preview = camera.getPreview();
            boolean wrapContent = (Integer) value == ViewGroup.LayoutParams.WRAP_CONTENT;
            if (preview == Preview.SURFACE && !wrapContent) {
                message("The SurfaceView preview does not support width or height changes. " +
                        "The view will act as WRAP_CONTENT by default.", true);
                return false;
            }
        }
        option.set(camera, value);
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        b.setState(BottomSheetBehavior.STATE_HIDDEN);
        message("Changed " + option.getName() + " to " + name, false);
        return true;
    }

    //request Permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean valid = true;
        for (int grantResult : grantResults) {
            valid = valid && grantResult == PackageManager.PERMISSION_GRANTED;
        }
        if (valid && !camera.isOpened()) {
            camera.open();
        }
    }

    private void message(@NonNull String content, boolean important) {
        if (important) {
            LOG.w(content);
            Toast.makeText(this, content, Toast.LENGTH_LONG).show();
        } else {
            LOG.i(content);
            Toast.makeText(this, content, Toast.LENGTH_SHORT).show();
        }
    }
}

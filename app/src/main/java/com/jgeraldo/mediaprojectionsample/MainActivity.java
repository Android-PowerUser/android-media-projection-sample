package com.jgeraldo.mediaprojectionsample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_MEDIA_PROJECTION_STARTED = "com.jgeraldo.mediaprojectionsample.ACTION_MEDIA_PROJECTION_STARTED";

    public static final String TAG = "MediaProjectionSample";

    private boolean isReceiverRegistered = false;

    private MediaProjectionManager mediaProjectionManager;

    private Surface mSurface;

    private Handler mHandler;

    private ActivityResultLauncher<Intent> startMediaProjectionActivity;

    // Creating a custom BroadcastReceiver class so we can use it externally without needing to declare on the Manifest.
    // The only reason we are using a Broadcast here is to guarantee that we'll only get the MediaProjection instance
    //  when the service has started (otherwise it would throw an exception) and also because we want to show the
    //  shared screen in a SurfaceView hosted on this Activity's (so we couldn't access it from the service directly).
    public class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MEDIA_PROJECTION_STARTED.equals(intent.getAction())) {
                // Handle the message from the service
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");

                MediaProjectionManager projectionManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection != null) {
                    startScreenCapture(mediaProjection);
                }
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView mSurfaceView = findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();

        mHandler = new Handler(Looper.getMainLooper());

        Button button = findViewById(R.id.button);
        button.setOnClickListener(view -> {
            requestScreenCapturePermission();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaProjectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        // tracks the createScreenCaptureIntent() result
        startMediaProjectionActivity =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            int resultCode = result.getResultCode();
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    MediaProjectionManager projectionManager =
                                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                    MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                    if (mediaProjection != null) {
                                        startScreenCapture(mediaProjection);
                                    }
                                } else {
                                    try {
                                        Intent serviceIntent = new Intent(this, MyMediaProjectionService.class);
                                        serviceIntent.putExtra("resultCode", resultCode);
                                        serviceIntent.putExtra("data", data);

                                        ContextCompat.startForegroundService(this, serviceIntent);
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "Error while trying to get the MediaProjection instance: " + e.getMessage());
                                    }
                                }
                            } else {
                                Toast.makeText(this, "Screen sharing permission denied",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "REGISTERING RECEIVER T");
                LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter/*, Context.RECEIVER_NOT_EXPORTED*/);
            } else {
                Log.d(TAG, "REGISTERING RECEIVER <T");
                LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            }
            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }
    }

    private void requestScreenCapturePermission() {
        if (startMediaProjectionActivity != null) {
            Log.d(TAG, "REQUESTING SCREEN CAPTURE INTENT PERMISSION");
            mediaProjectionManager = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);

            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            Log.d(TAG, "CREATING THE SCREEN CAPTURE INTENT");
            startMediaProjectionActivity.launch(captureIntent);
        }
    }

    public void startScreenCapture(MediaProjection mediaProjection) {
        MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                // Handle MediaProjection stopped event here
            }
        };

        mediaProjection.registerCallback(callback, null);

        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                720,
                1080,
                getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface,
                null,
                mHandler);
        // Do whatever you need with the virtualDisplay
    }
}
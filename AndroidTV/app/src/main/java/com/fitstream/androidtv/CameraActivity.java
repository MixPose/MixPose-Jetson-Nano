/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.fitstream.androidtv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.fitstream.androidtv.tokbox.OpenTokConfig;
import com.fitstream.androidtv.tokbox.WebServiceCoordinator;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import ai.fritz.core.FritzOnDeviceModel;
import ai.fritz.poseestimationmodel.PoseEstimationOnDeviceModel;
import ai.fritz.vision.FritzVision;
import ai.fritz.vision.FritzVisionImage;
import ai.fritz.vision.ImageRotation;
import ai.fritz.vision.poseestimation.FritzVisionPosePredictor;
import ai.fritz.vision.poseestimation.FritzVisionPosePredictorOptions;
import ai.fritz.vision.poseestimation.FritzVisionPoseResult;
import ai.fritz.vision.poseestimation.Pose;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

/*
 * Main Activity class that loads {@link MainFragment}.
 */
public class CameraActivity extends Activity
        implements CameraDialog.CameraDialogParent,
        CameraViewInterface.Callback,
        WebServiceCoordinator.Listener,
        Session.SessionListener,
        PublisherKit.PublisherListener,
        SubscriberKit.SubscriberListener{

    private static final String TAG = "Debug";
    public UVCCameraTextureView mTextureView;
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private static final int REQUEST_CODE = 1;
    private List<String> mMissPermissions = new ArrayList<>();
    private static final int RC_SETTINGS_SCREEN_PERM = 123;
    private static final int RC_VIDEO_APP_PERM = 124;

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;

    private boolean isRequest;
    private boolean isPreview;

    private FritzVisionPosePredictor posePredictor;
    private FritzVisionPoseResult poseResult;
    private float minPoseThreshold = 0.6f;


    // Suppressing this warning. mWebServiceCoordinator will get GarbageCollected if it is local.
    @SuppressWarnings("FieldCanBeLocal")
    private WebServiceCoordinator mWebServiceCoordinator;

    private Session mSession;
    private Subscriber mSubscriber;

    private FrameLayout mSubscriberViewContainer;

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {

                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };

    boolean btn = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_camera);
        checkAndRequestPermissions();


        ImageView btnLeaderBoard = (ImageView)findViewById(R.id.btnLeaderBoard);
        findViewById(R.id.btnLeaderBoard)
                .setOnClickListener(new View.OnClickListener(){

                    @Override
                    public void onClick(View v) {
                        if (btn) {
                            btnLeaderBoard.setImageResource(R.mipmap.leaderboard);
                            btn = false;

                        } else
                        {
                            btnLeaderBoard.setImageResource(R.mipmap.leaderboardicon);
                            btn = true;
                        }


                    }
                }
        );

        mSubscriberViewContainer = (FrameLayout)findViewById(R.id.subscriber_container);

        FritzOnDeviceModel onDeviceModel = new PoseEstimationOnDeviceModel();
        posePredictor = FritzVision.PoseEstimation.getPredictor(onDeviceModel);

        mTextureView = (UVCCameraTextureView)findViewById(R.id.camera_view);
        // step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);

        final ImageView surface = (ImageView)findViewById(R.id.surface);
        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {

            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
                Log.d(TAG, "onPreviewResult: " + nv21Yuv.length);
                //Bitmap bitmap = BitmapFactory.decodeByteArray(nv21Yuv, 0, nv21Yuv.length);

                YuvImage yuvImage = new android.graphics.YuvImage(nv21Yuv, ImageFormat.NV21, mCameraHelper.getPreviewWidth(), mCameraHelper.getPreviewHeight(), null);

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, mCameraHelper.getPreviewWidth(), mCameraHelper.getPreviewHeight()), 100, os);
                byte[] jpegByteArray = os.toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);


                final FritzVisionImage fritzVisionImage = FritzVisionImage.fromBitmap(bitmap);

                Bitmap b = Bitmap.createBitmap(surface.getWidth(),  (int)(surface.getWidth()/mUVCCameraView.getAspectRatio()), Bitmap.Config.ARGB_8888);
                Log.d("size", String.valueOf(surface.getWidth()) + "," + String.valueOf((int)(surface.getWidth()/mUVCCameraView.getAspectRatio())));

                // Run the model to find poses in the image.
                poseResult = posePredictor.predict(fritzVisionImage);
                // Draw the result.
                if (poseResult != null) {
                    surface.setVisibility(View.VISIBLE);
                    List<Pose> poses = poseResult.getPoses();

                    for (Pose pose : poses) {

                        Canvas canvas = new Canvas(b);

                        pose.draw(canvas);
                        surface.setImageBitmap(b);

                    }

                    if(poses.size() == 0)
                    {
                        Canvas canvas = new Canvas(b);
                        canvas.drawColor(Color.TRANSPARENT);
                        surface.setImageBitmap(b);
                    }
                }
                else
                {
                    Canvas canvas = new Canvas(b);
                    canvas.drawColor(Color.TRANSPARENT);
                    surface.setImageBitmap(b);
                }
            }
        });

        /*
        if (OpenTokConfig.isWebServerConfigUrlValid()) {
            mWebServiceCoordinator = new WebServiceCoordinator(this, this);
            mWebServiceCoordinator.fetchSessionConnectionData(OpenTokConfig.SESSION_INFO_ENDPOINT);
        } else {
            showConfigError("Configuration Error", OpenTokConfig.webServerConfigErrorMessage);
        }*/

        initializeSession(OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID, OpenTokConfig.TOKEN);
    }


    private void initializeSession(String apiKey, String sessionId, String token) {

        mSession = new Session.Builder(this, apiKey, sessionId).build();
        mSession.setSessionListener(this);
        mSession.connect(token);
    }

    /* Web Service Coordinator delegate methods */

    @Override
    public void onSessionConnectionDataReady(String apiKey, String sessionId, String token) {

        Log.d(TAG, "ApiKey: "+apiKey + " SessionId: "+ sessionId + " Token: "+token);
        initializeSession(apiKey, sessionId, token);
    }

    @Override
    public void onWebServiceCoordinatorError(Exception error) {

        Log.e(TAG, "Web Service error: " + error.getMessage());
        Toast.makeText(this, "Web Service error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        finish();
    }


    @Override
    public void onConnected(Session session) {

        Log.d(TAG, "onConnected: Connected to session: "+session.getSessionId());
    }

    @Override
    public void onDisconnected(Session session) {

        Log.d(TAG, "onDisconnected: Disconnected from session: "+session.getSessionId());
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        Log.d(TAG, "onStreamReceived: New Stream Received "+stream.getStreamId() + " in session: "+session.getSessionId());

        if (mSubscriber == null) {
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriber.setSubscriberListener(this);
            mSession.subscribe(mSubscriber);
            mSubscriberViewContainer.addView(mSubscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {

        Log.d(TAG, "onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());

        if (mSubscriber != null) {
            mSubscriber = null;
            mSubscriberViewContainer.removeAllViews();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.e(TAG, "onError: "+ opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - "+opentokError.getMessage() + " in session: "+ session.getSessionId());

        showOpenTokError(opentokError);
    }

    /* Publisher Listener methods */

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        Log.d(TAG, "onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        Log.d(TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        Log.e(TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        Log.d(TAG, "onConnected: Subscriber connected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        Log.d(TAG, "onDisconnected: Subscriber disconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        Log.e(TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    private void showOpenTokError(OpentokError opentokError) {

        Toast.makeText(this, opentokError.getErrorDomain().name() +": " +opentokError.getMessage() + " Please, see the logcat.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void showConfigError(String alertTitle, final String errorMessage) {
        Log.e(TAG, "Error " + alertTitle + ": " + errorMessage);
        new AlertDialog.Builder(this)
                .setTitle(alertTitle)
                .setMessage(errorMessage)
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        CameraActivity.this.finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    protected void onPause() {

        Log.d(TAG, "onPause");

        super.onPause();

        if (mSession != null) {
            mSession.onPause();
        }

    }

    @Override
    protected void onResume() {

        Log.d(TAG, "onResume");

        super.onResume();

        if (mSession != null) {
            mSession.onResume();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.releaseFile();
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    public boolean isCameraOpened() {
        return mCameraHelper.isCameraOpened();
    }


    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }


    private void checkAndRequestPermissions() {
        mMissPermissions.clear();
        for (String permission : REQUIRED_PERMISSION_LIST) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                mMissPermissions.add(permission);
            }
        }
        // check permissions has granted
        if (mMissPermissions.isEmpty()) {
            //startMainActivity();
            //no permission
        } else {
            ActivityCompat.requestPermissions(this,
                    mMissPermissions.toArray(new String[mMissPermissions.size()]),
                    REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    mMissPermissions.remove(permissions[i]);
                }
            }
        }
        // Get permissions success or not
        /*
        if (mMissPermissions.isEmpty()) {
            startMainActivity();
        } else {
            Toast.makeText(SplashActivity.this, "get permissions failed,exiting...",Toast.LENGTH_SHORT).show();
            SplashActivity.this.finish();
        }*/
    }
}

/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends Activity  implements View.OnClickListener {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;
    private static final long LOCK_FOCUS_DELAY_ON_FOCUSED = 5000;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private Handler mHandler;
    private BleService mBle;
    private List<String> DEVICE_NAMES = new ArrayList<>(Arrays.asList("PetCamera"));
    private Intent BLEServerIntent;

    private int SendDataSpeedOneSec  = 10;
    private Timer SendDataTimer=new Timer();
    private Timer RobotResetTimer =new Timer();
    private int FaceCount = 0 ;
    private PointF FacePos=new PointF(1920/2,1080/2);
    private Button UpButton,LeftButton,RightButton,BotButton;
    private final int EVENT_PLUS = 2;
    private final int EVENT_SUB = 1;
    private final int EVENT_WAIT = 0;
    private final char ROBOT_COMMAND_MOVE ='s';
    private final char ROBOT_COMMITE_INIT='e';
    private boolean IsRobotResetTimerOn =false;
    private int RobotResetSec =  0 ;






    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        UpButton=(Button)findViewById(R.id.btnUP);
        LeftButton=(Button)findViewById(R.id.btnLeft);
        RightButton=(Button)findViewById(R.id.btnRight);
        BotButton=(Button)findViewById(R.id.btnBot);
        UpButton.setOnClickListener(this);
        LeftButton.setOnClickListener(this);
        RightButton.setOnClickListener(this);
        BotButton.setOnClickListener(this);


        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        prepareBLE();
        mHandler = new MyHandler(this);


        SendDataTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mGraphicOverlay.getSize() <=0)
                {
                    RobotResetSec ++;
                    if(RobotResetSec>50) {
                        RobotContorller(ROBOT_COMMITE_INIT, 0, 0);
                        RobotResetSec = 0;

                    }
                    //SendBleMsg(DEVICE_NAMES.get(0)+",e00");

                }
                else
                {
                    RobotResetSec= 0 ;
                    PointF ImageCenter = new PointF(1920/2,1080/2);
                    PointF FaceVec =new PointF( FacePos.x -ImageCenter.x,FacePos.y -ImageCenter.y);
                    int X_Event=0,Y_Event=0;
                    if(Math.abs(FacePos.x -ImageCenter.x) <400 )
                    {
                        X_Event =EVENT_WAIT;

                    }
                    else
                    {
                        if(FacePos.x -ImageCenter.x < 0 )X_Event =EVENT_PLUS;
                        else X_Event =   EVENT_SUB;
                    }

                    if(Math.abs(FacePos.y -ImageCenter.y) <300)
                    {
                        Y_Event =EVENT_WAIT;

                    }
                    else
                    {
                        if(FacePos.y -ImageCenter.y < 0 )Y_Event =EVENT_PLUS;
                        else Y_Event =EVENT_SUB;
                    }
                   // SendBleMsg(DEVICE_NAMES.get(0)+",s"+(FacePos.x-1920/2)  +";Y:"+(FacePos.y-1080/2 )+"\n");
                   RobotContorller(ROBOT_COMMAND_MOVE,X_Event,Y_Event);


                }
            }
        },0,1000/SendDataSpeedOneSec);

    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(1920, 1080)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .setAutoFocusEnabled(true)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {

            mFaceGraphic.setId(faceId);

        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            FacePos =mFaceGraphic.getFacePos();
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
    public ServiceConnection BLEConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e("Mike", "onServiceConnected");
            //取得service的實體
            mBle = ((BleService.LocalBinder) iBinder).getService();
            //設定BLE Device name
            mBle.setBleDeviceNames(DEVICE_NAMES);
            //取得service的callback，在這邊是顯示接收BLE的資訊
            mBle.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private void prepareBLE() {
        //region 請求權限 android 6.0+
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
            //判断是否需要 向用户解释，为什么要申请该权限
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {

            }
        }
        //endregion

        //region 綁定service
        BLEServerIntent = new Intent(this, BleService.class);
        bindService(BLEServerIntent, BLEConnection, Context.BIND_AUTO_CREATE);
        //endregion
    }
    private static class MyHandler extends Handler {
        private final WeakReference<FaceTrackerActivity> mActivity;

        public MyHandler(FaceTrackerActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    String BleString = (String) msg.obj;
                    System.out.println("GET BLE Message "+BleString);

                    //mActivity.get().textView1.setText(BleString);
                    break;
                default:
                    break;
            }
        }
    }

    private void SendBleMsg(String _msg){
        String[] msgs = _msg.split(",");
        if(mBle!=null)
            mBle.writeCharacteristic(msgs[0],msgs[1]);
    }

    @Override
    public void onClick(View v) {
        System.out.println("OnClick");
        switch (v.getId()) {

            case R.id.btnBot:
                RobotContorller(ROBOT_COMMAND_MOVE,EVENT_WAIT,EVENT_SUB);
                break;

            case R.id.btnRight:
                RobotContorller(ROBOT_COMMAND_MOVE,EVENT_SUB,EVENT_WAIT);
                break;

            case R.id.btnLeft:
                RobotContorller(ROBOT_COMMAND_MOVE,EVENT_PLUS,EVENT_WAIT);
                break;
            case R.id.btnUP:

                RobotContorller(ROBOT_COMMAND_MOVE,EVENT_WAIT,EVENT_PLUS);
                break;

        }
    }
    void RobotContorller (char CommitCode,int X_Event,int Y_Event)
    {

            SendBleMsg(DEVICE_NAMES.get(0)+","+CommitCode+X_Event+ Y_Event);

    }




}

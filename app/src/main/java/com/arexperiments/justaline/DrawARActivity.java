// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.arexperiments.justaline;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.icu.util.Calendar;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arexperiments.justaline.analytics.AnalyticsEvents;
import com.arexperiments.justaline.analytics.Fa;
import com.arexperiments.justaline.model.Stroke;
import com.arexperiments.justaline.rendering.AnchorRenderer;
import com.arexperiments.justaline.rendering.BackgroundRenderer;
import com.arexperiments.justaline.rendering.LineShaderRenderer;
import com.arexperiments.justaline.rendering.LineUtils;
import com.arexperiments.justaline.rendering.PointCloudRenderer;
import com.arexperiments.justaline.view.BrushSelector;
import com.arexperiments.justaline.view.ClearDrawingDialog;
import com.arexperiments.justaline.view.DebugView;
import com.arexperiments.justaline.view.ErrorDialog;
import com.arexperiments.justaline.view.LeaveRoomDialog;
import com.arexperiments.justaline.view.PairButton;
import com.arexperiments.justaline.view.PairButtonToolTip;
import com.arexperiments.justaline.view.PairView;
import com.arexperiments.justaline.view.PlaybackView;
import com.arexperiments.justaline.view.RecordButton;
import com.arexperiments.justaline.view.TrackingIndicator;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotTrackingException;
import com.uncorkedstudios.android.view.recordablesurfaceview.RecordableSurfaceView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;


/**
 * This is a complex example that shows how to create an augmented reality (AR) application using
 * the ARCore API.
 */

public class DrawARActivity extends BaseActivity
        implements RecordableSurfaceView.RendererCallbacks, View.OnClickListener,
        RecordButton.Listener, ClearDrawingDialog.Listener, PlaybackView.Listener,
        ErrorDialog.Listener, RoomManager.StrokeUpdateListener, PairView.Listener,
        LeaveRoomDialog.Listener, PairSessionManager.AnchorStateListener,
        PairButtonToolTip.Listener, PairSessionManager.PartnerUpdateListener {

    private static final String TAG = "DrawARActivity";

    private static final boolean JOIN_GLOBAL_ROOM = BuildConfig.GLOBAL;

    private static final int TOUCH_QUEUE_SIZE = 10;

    private Fa mAnalytics;

    enum Mode {
        DRAW, PAIR_PARTNER_DISCOVERY, PAIR_ANCHOR_RESOLVING, PAIR_ERROR, PAIR_SUCCESS
    }

    private Mode mMode = Mode.DRAW;

    private View mDrawUiContainer;

    // Set to true ensures requestInstall() triggers installation if necessary.
    private boolean mUserRequestedARCoreInstall = true;

    private RecordableSurfaceView mSurfaceView;

    private Session mSession;

    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();

    private LineShaderRenderer mLineShaderRenderer = new LineShaderRenderer();
//    private DebugMeshShaderRenderer mLineShaderRenderer = new DebugMeshShaderRenderer();

    private final PointCloudRenderer pointCloud = new PointCloudRenderer();

    private AnchorRenderer zeroAnchorRenderer;

    private AnchorRenderer cloudAnchorRenderer;

    private Frame mFrame;

    private float[] projmtx = new float[16];

    private float[] viewmtx = new float[16];

    private float[] mZeroMatrix = new float[16];

    private float mScreenWidth = 0;

    private float mScreenHeight = 0;

    private Vector2f mLastTouch;

    private AtomicInteger touchQueueSize;

    private AtomicReferenceArray<Vector2f> touchQueue;

    private float mLineWidthMax = 0.33f;

    private float[] mLastFramePosition;

    private Boolean isDrawing = false;

    private AtomicBoolean bHasTracked = new AtomicBoolean(false);

    private AtomicBoolean bTouchDown = new AtomicBoolean(false);

    private AtomicBoolean bClearDrawing = new AtomicBoolean(false);

    private AtomicBoolean bUndo = new AtomicBoolean(false);

    private AtomicBoolean bNewStroke = new AtomicBoolean(false);

    private List<Stroke> mStrokes;

    private File mOutputFile;

    private BrushSelector mBrushSelector;

    private RecordButton mRecordButton;

    private View mUndoButton;

    private TrackingIndicator mTrackingIndicator;

    private View mOverflowButton;

    private LinearLayout mOverflowLayout;

    private View mClearDrawingButton;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    /*
     * Track number frames where we lose ARCore tracking. If we lose tracking for less than
     * a given number then continue painting.
     */
    private static final int MAX_UNTRACKED_FRAMES = 5;

    private int mFramesNotTracked = 0;

    private PlaybackView mPlaybackView;

    private DebugView mDebugView;

    private boolean mDebugEnabled = false;

    private long mRenderDuration;

    /*
     * Session sharing
     */

    private Anchor mAnchor;

    private Map<String, Stroke> mSharedStrokes = new HashMap<>();

    private PairButton mPairButton;

    private TextView mPairActiveView;

    private PairButtonToolTip mPairButtonToolTip;

    private PairView mPairView;

    private PairSessionManager mPairSessionManager;

    /**
     * Setup the app when main activity is created
     */
    @SuppressLint("ApplySharedPref")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Debug view
        if (BuildConfig.DEBUG) {
            mDebugView = findViewById(R.id.debug_view);
            mDebugView.setVisibility(View.VISIBLE);
            mDebugEnabled = true;
        }

        mAnalytics = Fa.get();

        mTrackingIndicator = findViewById(R.id.finding_surfaces_view);

        mSurfaceView = findViewById(R.id.surfaceview);
        mSurfaceView.setRendererCallbacks(this);

        mOverflowButton = findViewById(R.id.button_overflow_menu);
        mOverflowButton.setOnClickListener(this);
        mOverflowLayout = findViewById(R.id.layout_menu_items);
        mClearDrawingButton = findViewById(R.id.menu_item_clear);
        mClearDrawingButton.setOnClickListener(this);
        findViewById(R.id.menu_item_about).setOnClickListener(this);
        findViewById(R.id.menu_item_share_app).setOnClickListener(this);

//        findViewById(R.id.menu_item_crash).setOnClickListener(this);
//        findViewById(R.id.menu_item_hide_ui).setOnClickListener(this);

        mPairButton = findViewById(R.id.button_pair);
        mPairButton.setOnClickListener(this);
        mPairButtonToolTip = findViewById(R.id.tooltip_button_pair);
        mPairButtonToolTip.setListener(this);
        mPairActiveView = findViewById(R.id.pair_active);

        mUndoButton = findViewById(R.id.undo_button);

        // set up brush selector
        mBrushSelector = findViewById(R.id.brush_selector);

        mRecordButton = findViewById(R.id.record_button);
        mRecordButton.setEnabled(false);

        // Reset the zero matrix
        Matrix.setIdentityM(mZeroMatrix, 0);

        mStrokes = new ArrayList<>();
        touchQueueSize = new AtomicInteger(0);
        touchQueue = new AtomicReferenceArray<>(TOUCH_QUEUE_SIZE);

        mPlaybackView = findViewById(R.id.playback);

        mDrawUiContainer = findViewById(R.id.draw_container);

        mPairView = findViewById(R.id.view_join);
        mPairView.setListener(this);

        if (JOIN_GLOBAL_ROOM) {
            mPairSessionManager = new GlobalPairSessionManager(this);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pick global session")
                    .setItems(R.array.sessions_array, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            GlobalRoomManager.setGlobalRoomName(String.valueOf(which));
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            mPairSessionManager = new PairSessionManager(this);
        }
        mPairSessionManager.setPairingStateChangeListener(mPairView);
        mPairSessionManager.addPartnerUpdateListener(mPairButton);
        mPairSessionManager.addPartnerUpdateListener(this);
        mPairSessionManager.setAnchorStateListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mPairSessionManager.login(this);
        if (mPairSessionManager.isPaired()) {
            mPairSessionManager.resumeListeners(this);
        }
    }

    @Override
    protected void onStop() {
        mPairSessionManager.pauseListeners();

        super.onStop();
    }

    /**
     * onResume part of the Android Activity Lifecycle
     */
    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (PermissionHelper.hasRequiredPermissions(this)) {

            // Check if ARCore is installed/up-to-date
            int message = -1;
            Exception exception = null;
            try {
                if (mSession == null) {
                    switch (ArCoreApk.getInstance()
                            .requestInstall(this, mUserRequestedARCoreInstall)) {
                        case INSTALLED:
                            mSession = new Session(this);

                            break;
                        case INSTALL_REQUESTED:
                            // Ensures next invocation of requestInstall() will either return
                            // INSTALLED or throw an exception.
                            mUserRequestedARCoreInstall = false;
                            // at this point, the activity is paused and user will go through
                            // installation process
                            return;
                    }
                }
            } catch (Exception e) {
                exception = e;
                message = getARCoreInstallErrorMessage(e);
            }

            // display possible ARCore error to user
            if (message >= 0) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Exception creating session", exception);
                finish();
                return;
            }

            // Create default config and check if supported.
            Config config = new Config(mSession);
            config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
            config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
            if (!mSession.isSupported(config)) {
                Toast.makeText(getApplicationContext(), R.string.ar_not_supported,
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            mSession.configure(config);

            // Note that order of session/surface resume matters - session must be resumed
            // before the surface view is resumed or the surface may call back on a session that is
            // not ready.
            try {
                mSession.resume();
            } catch (CameraNotAvailableException e) {
                ErrorDialog.newInstance(R.string.error_camera_not_available, true)
                        .show(this);
            } catch (Exception e) {
                ErrorDialog.newInstance(R.string.error_resuming_session, true).show(this);
            }

            mSurfaceView.resume();
        } else {
            // take user to permissions activity
            startActivity(new Intent(this, PermissionsActivity.class));
            finish();
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        mScreenHeight = displayMetrics.heightPixels;
        mScreenWidth = displayMetrics.widthPixels;

        mRecordButton.reset();
        mRecordButton.setListener(this);

        mPlaybackView.setListener(this);

        mPlaybackView.resume();

        if (mPairSessionManager.isPaired()) {

            if (!SessionHelper.shouldContinuePairedSession(this)) {
                mPairSessionManager.checkForPartners(new RoomManager.PartnerDetectionListener() {
                    @Override
                    public void onPartnersDetected() {
                        // Stay in room
                    }

                    @Override
                    public void onNoPartnersDetected() {
                        mPairSessionManager.leaveRoom(false);
                    }
                });
            } // time limit has not elapsed, force rejoin room (listeners restarted in onStart)

        } else if (!SessionHelper.shouldContinueSession(this)) {
            // if user has left activity for too long, clear the strokes from the previous session
            bClearDrawing.set(true);
            showStrokeDependentUI();
        }

        mPairSessionManager.setSession(mSession);

        mPairView.setListener(this);

        // TODO: Only used id hidden by "Hide UI menu"
        findViewById(R.id.draw_container).setVisibility(View.VISIBLE);

        if (!BuildConfig.SHOW_NAVIGATION) {
            mRecordButton.setVisibility(View.GONE);
            mOverflowButton.setVisibility(View.GONE);
        }
    }

    /**
     * onPause part of the Android Activity Lifecycle
     */
    @Override
    public void onPause() {
        if (mRecordButton.isRecording()) {
            mRecordButton.setRecording(false);
        }

        // Note that the order matters - SurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mSurfaceView.pause();
        if (mSession != null) {
            mSession.pause();
        }

        mRecordButton.setListener(null);
        mTrackingIndicator.resetTrackingTimeout();

        if (mPlaybackView != null) {
            mPlaybackView.pause();
            mPlaybackView.setListener(null);
        }

        SessionHelper.setSessionEnd(this);

        mPairView.setListener(null);

        super.onPause();
    }


    /**
     * addStroke adds a new stroke to the scene
     */
    private void addStroke() {
        mLineWidthMax = mBrushSelector.getSelectedLineWidth().getWidth();

        Stroke stroke = new Stroke();
        stroke.localLine = true;
        stroke.setLineWidth(mLineWidthMax);
        mStrokes.add(stroke);

        // update firebase
        int index = mStrokes.size() - 1;
//        mPairSessionManager.updateStroke(index, mStrokes.get(index));
        mPairSessionManager.addStroke(mStrokes.get(index));

        showStrokeDependentUI();

        mAnalytics.setUserProperty(AnalyticsEvents.USER_PROPERTY_HAS_DRAWN,
                AnalyticsEvents.VALUE_TRUE);

        mTrackingIndicator.setDrawnInSession();
    }

    /**
     * addPoint2f adds a point to the current stroke
     *
     * @param touchPoint a 2D point in screen space and is projected into 3D world space
     */
    private void addPoint2f(Vector2f... touchPoint) {
        Vector3f[] newPoints = new Vector3f[touchPoint.length];
        for (int i = 0; i < touchPoint.length; i++) {
            newPoints[i] = LineUtils
                    .GetWorldCoords(touchPoint[i], mScreenWidth, mScreenHeight, projmtx, viewmtx);
        }

        addPoint3f(newPoints);
    }

    /**
     * addPoint3f adds a point to the current stroke
     *
     * @param newPoint a 3D point in world space
     */
    private void addPoint3f(Vector3f... newPoint) {
        Vector3f point;
        int index = mStrokes.size() - 1;

        if (index < 0)
            return;

        for (int i = 0; i < newPoint.length; i++) {
            if (mAnchor != null && mAnchor.getTrackingState() == TrackingState.TRACKING) {
                point = LineUtils.TransformPointToPose(newPoint[i], mAnchor.getPose());
                mStrokes.get(index).add(point);
            } else {
                mStrokes.get(index).add(newPoint[i]);
            }
        }

        // update firebase database
        mPairSessionManager.updateStroke(mStrokes.get(index));
        isDrawing = true;
    }

    /**
     * update() is executed on the GL Thread.
     * The method handles all operations that need to take place before drawing to the screen.
     * The method :
     * extracts the current projection matrix and view matrix from the AR Pose
     * handles adding stroke and points to the data collections
     * updates the ZeroMatrix and performs the matrix multiplication needed to re-center the drawing
     * updates the Line Renderer with the current strokes, color, distance scale, line width etc
     */
    private void update() {
        try {
            final long updateStartTime = System.currentTimeMillis();

            // Update ARCore frame
            mFrame = mSession.update();

            // Notify the hostManager of all the anchor updates.
            Collection<Anchor> updatedAnchors = mFrame.getUpdatedAnchors();
            mPairSessionManager.onUpdate(updatedAnchors);

            // Update tracking states
            mTrackingIndicator.setTrackingStates(mFrame, mAnchor);
            if (mTrackingIndicator.trackingState == TrackingState.TRACKING && !bHasTracked.get()) {
                bHasTracked.set(true);
                mAnalytics
                        .setUserProperty(AnalyticsEvents.USER_PROPERTY_TRACKING_ESTABLISHED,
                                AnalyticsEvents.VALUE_TRUE);
            }

            // Get projection matrix.
            mFrame.getCamera().getProjectionMatrix(projmtx, 0, AppSettings.getNearClip(),
                    AppSettings.getFarClip());
            mFrame.getCamera().getViewMatrix(viewmtx, 0);

            float[] position = new float[3];

            mFrame.getCamera().getPose().getTranslation(position, 0);

            // Multiply the zero matrix
            Matrix.multiplyMM(viewmtx, 0, viewmtx, 0, mZeroMatrix, 0);

            // Check if camera has moved much, if thats the case, stop touchDown events
            // (stop drawing lines abruptly through the air)
            if (mLastFramePosition != null) {
                Vector3f distance = new Vector3f(position[0], position[1], position[2]);
                distance.sub(new Vector3f(mLastFramePosition[0], mLastFramePosition[1],
                        mLastFramePosition[2]));

                if (distance.length() > 0.15) {
                    bTouchDown.set(false);
                }
            }

            mLastFramePosition = position;

            // Add points to strokes from touch queue
            int numPoints = touchQueueSize.get();
            if (numPoints > TOUCH_QUEUE_SIZE) {
                numPoints = TOUCH_QUEUE_SIZE;
            }

            if (numPoints > 0) {
                if (bNewStroke.get()) {
                    bNewStroke.set(false);
                    addStroke();
                }

                Vector2f[] points = new Vector2f[numPoints];
                for (int i = 0; i < numPoints; i++) {
                    points[i] = touchQueue.get(i);
                    mLastTouch = new Vector2f(points[i].x, points[i].y);
                }
                addPoint2f(points);
            }

            // If no new points have been added, and touch is down, add last point again
            if (numPoints == 0 && bTouchDown.get()) {
                addPoint2f(mLastTouch);
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (numPoints > 0) {
                touchQueueSize.set(0);
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (bClearDrawing.get()) {
                bClearDrawing.set(false);
                clearDrawing();
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            // Check if we are still drawing, otherwise finish line
            if (isDrawing && !bTouchDown.get()) {
                isDrawing = false;
                if (!mStrokes.isEmpty()) {
                    mStrokes.get(mStrokes.size() - 1).finishStroke();
                }
            }

            // Update line animation
//            for (int i = 0; i < mStrokes.size(); i++) {
//                mStrokes.get(i).update();
//            }
            boolean renderNeedsUpdate = false;
            for (Stroke stroke : mSharedStrokes.values()) {
                if (stroke.update()) {
                    renderNeedsUpdate = true;
                }
            }
            if (renderNeedsUpdate) {
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (bUndo.get()) {
                bUndo.set(false);
                if (mStrokes.size() > 0) {
                    int index = mStrokes.size() - 1;
                    mPairSessionManager.undoStroke(mStrokes.get(index));
                    mStrokes.remove(index);
                    if (mStrokes.isEmpty()) {
                        showStrokeDependentUI();
                    }
                    mLineShaderRenderer.bNeedsUpdate.set(true);
                }
            }
            if (mLineShaderRenderer.bNeedsUpdate.get()) {
                mLineShaderRenderer.setColor(AppSettings.getColor());
                mLineShaderRenderer.mDrawDistance = AppSettings.getStrokeDrawDistance();
                float distanceScale = 0.0f;
                mLineShaderRenderer.setDistanceScale(distanceScale);
                mLineShaderRenderer.setLineWidth(mLineWidthMax);
                mLineShaderRenderer.clear();
                mLineShaderRenderer.updateStrokes(mStrokes, mSharedStrokes);
                mLineShaderRenderer.upload();
            }

            // Debug view
            if (mDebugEnabled) {
                final long deltaTime = System.currentTimeMillis() - updateStartTime;
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDebugView
                                .setRenderInfo(mLineShaderRenderer.mNumPoints, deltaTime,
                                        mRenderDuration);
                    }
                });

            }

        } catch (Exception e) {
            Log.e(TAG, "update: ", e);
        }
    }

    /**
     * renderScene() clears the Color Buffer and Depth Buffer, draws the current texture from the
     * camera
     * and draws the Line Renderer if ARCore is tracking the world around it
     */
    private void renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mFrame != null) {
            mBackgroundRenderer.draw(mFrame);
        }

        // Draw debug anchors
        if (BuildConfig.DEBUG) {
            if (mFrame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                zeroAnchorRenderer.draw(viewmtx, projmtx, false);
            }
        }

        // Draw background.
        if (mFrame != null) {

            // Draw Lines
            if (mTrackingIndicator.isTracking() || (
                    // keep painting through 5 frames where we're not tracking
                    (bHasTracked.get() && mFramesNotTracked < MAX_UNTRACKED_FRAMES))) {

                if (!mTrackingIndicator.isTracking()) {
                    mFramesNotTracked++;
                } else {
                    mFramesNotTracked = 0;
                }

                // If the anchor is set, set the modelMatrix of the line renderer to offset to the anchor
                if (mAnchor != null && mAnchor.getTrackingState() == TrackingState.TRACKING) {
                    mAnchor.getPose().toMatrix(mLineShaderRenderer.mModelMatrix, 0);

                    if (BuildConfig.DEBUG) {
                        mAnchor.getPose().toMatrix(cloudAnchorRenderer.mModelMatrix, 0);
                        cloudAnchorRenderer.draw(viewmtx, projmtx, true);
                    }
                }

                // Render the lines
                mLineShaderRenderer
                        .draw(viewmtx, projmtx, mScreenWidth, mScreenHeight,
                                AppSettings.getNearClip(),
                                AppSettings.getFarClip());
            }

            if (mDebugEnabled) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDebugView.setAnchorTracking(mAnchor);
                    }
                });
            }
        }

        if (mMode == Mode.PAIR_PARTNER_DISCOVERY || mMode == Mode.PAIR_ANCHOR_RESOLVING) {
            if (mFrame != null) {
                PointCloud pointCloud = mFrame.acquirePointCloud();
                this.pointCloud.update(pointCloud);
                this.pointCloud.draw(viewmtx, projmtx);

                // Application is responsible for releasing the point cloud resources after
                // using it.
                pointCloud.release();
            }
        }

    }

    /**
     * Clears the Datacollection of Strokes and sets the Line Renderer to clear and update itself
     * Designed to be executed on the GL Thread
     */
    private void clearDrawing() {
        mStrokes.clear();
        mLineShaderRenderer.clear();
        mPairSessionManager.clearStrokes();
        showStrokeDependentUI();
    }


    /**
     * onClickUndo handles the touch input on the GUI and sets the AtomicBoolean bUndo to be true
     * the actual undo functionality is executed in the GL Thread
     */
    public void onClickUndo(View button) {

        bUndo.set(true);

        mAnalytics.setUserProperty(AnalyticsEvents.USER_PROPERTY_TAPPED_UNDO,
                AnalyticsEvents.VALUE_TRUE);
    }

    private void toggleOverflowMenu() {
        if (mOverflowLayout.getVisibility() == View.VISIBLE) {
            hideOverflowMenu();
        } else {
            showOverflowMenu();
        }
    }

    private void showOverflowMenu() {
        if (!mPlaybackView.isOpen()) { // only show overflow if not in playback view
            mOverflowLayout.setVisibility(View.VISIBLE);
        }
    }

    private void hideOverflowMenu() {
        mOverflowLayout.setVisibility(View.GONE);
    }


    private boolean stopRecording() {
        boolean stoppedSuccessfully;
        try {
            stoppedSuccessfully = mSurfaceView.stopRecording();
        } catch (RuntimeException e) {
            stoppedSuccessfully = false;
            Fa.get().exception(e, "Error stopping recording");
        }
        if (stoppedSuccessfully) {
            mRecordButton.setEnabled(false);
            openPlayback(mOutputFile);
            Log.v(TAG, "Recording Stopped");
        } else {
            // reset everything to try again
            onPlaybackClosed();
            ErrorDialog.newInstance(R.string.stop_recording_failed, false).show(this);
        }

        mOverflowButton.setVisibility(View.VISIBLE);
        enableView(mPairButton);

        return stoppedSuccessfully;
    }


    private boolean startRecording() {
        boolean startSuccessful = mSurfaceView.startRecording();

        if (startSuccessful) {
            mOverflowButton.setVisibility(View.GONE);
            hideOverflowMenu();
            disableView(mPairButton);
            Log.v(TAG, "Recording Started");
        } else {
            Toast.makeText(this, R.string.start_recording_failed, Toast.LENGTH_SHORT).show();
            prepareForRecording();
        }
        return startSuccessful;
    }


    private void openPlayback(File file) {
        mPlaybackView.open(file);
        hideView(mDrawUiContainer);
        mPairButtonToolTip.hide();
        hideView(mTrackingIndicator);
    }


    /**
     * onClickClear handle showing an AlertDialog to clear the drawing
     */
    private void onClickClear() {
        ClearDrawingDialog.newInstance(mPairSessionManager.isPaired()).show(this);
        mAnalytics.setUserProperty(AnalyticsEvents.USER_PROPERTY_TAPPED_CLEAR,
                AnalyticsEvents.VALUE_TRUE);
    }

    // ------- Touch events

    /**
     * onTouchEvent handles saving the lastTouch screen position and setting bTouchDown and
     * bNewStroke
     * AtomicBooleans to trigger addPoint3f and addStroke on the GL Thread to be called
     */
    @Override
    public boolean onTouchEvent(MotionEvent tap) {
        int action = tap.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            closeViewsOutsideTapTarget(tap);
        }

        // do not accept touch events through the playback view
        // or when we are not tracking
        if (mPlaybackView.isOpen() || !mTrackingIndicator.isTracking()) {
            if (bTouchDown.get()) {
                bTouchDown.set(false);
            }
            return false;
        }

        if (mMode == Mode.DRAW) {
            if (action == MotionEvent.ACTION_DOWN) {
                touchQueue.set(0, new Vector2f(tap.getX(), tap.getY()));
                bNewStroke.set(true);
                bTouchDown.set(true);
                touchQueueSize.set(1);

                bNewStroke.set(true);
                bTouchDown.set(true);

                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (bTouchDown.get()) {
                    int numTouches = touchQueueSize.addAndGet(1);
                    if (numTouches <= TOUCH_QUEUE_SIZE) {
                        touchQueue.set(numTouches - 1, new Vector2f(tap.getX(), tap.getY()));
                    }
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP
                    || tap.getAction() == MotionEvent.ACTION_CANCEL) {
                bTouchDown.set(false);
                return true;
            }
        }

        return false;
    }

    private void closeViewsOutsideTapTarget(MotionEvent tap) {
        if (isOutsideViewBounds(mOverflowLayout, (int) tap.getRawX(), (int) tap.getRawY())
                && mOverflowLayout.getVisibility() == View.VISIBLE) {
            hideOverflowMenu();
        }
        if (isOutsideViewBounds(mBrushSelector, (int) tap.getRawX(), (int) tap.getRawY())
                && mBrushSelector.isOpen()) {
            mBrushSelector.close();
        }
        if (isOutsideViewBounds(mPairButtonToolTip, (int) tap.getRawX(), (int) tap.getRawY())
                && mPairButtonToolTip.getVisibility() == View.VISIBLE) {
            mPairButtonToolTip.hide();
        }
    }

    private boolean isOutsideViewBounds(View view, int x, int y) {
        Rect outRect = new Rect();
        int[] location = new int[2];
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return !outRect.contains(x, y);
    }

    private File createVideoOutputFile() {

        File tempFile;

        File dir = new File(getCacheDir(), "captures");

        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        Calendar c = Calendar.getInstance();

        String filename = "JustALine_" +
                c.get(Calendar.YEAR) + "-" +
                (c.get(Calendar.MONTH) + 1) + "-" +
                c.get(Calendar.DAY_OF_MONTH)
                + "_" +
                c.get(Calendar.HOUR_OF_DAY) +
                c.get(Calendar.MINUTE) +
                c.get(Calendar.SECOND);

        tempFile = new File(dir, filename + ".mp4");

        return tempFile;

    }

    @Override
    public void onSurfaceDestroyed() {
        mBackgroundRenderer.clearGL();
        mLineShaderRenderer.clearGL();
    }

    @Override
    public void onSurfaceCreated() {
        prepareForRecording();

        zeroAnchorRenderer = new AnchorRenderer();
        cloudAnchorRenderer = new AnchorRenderer();
        pointCloud.createOnGlThread(/*context=*/ this);
    }

    private void prepareForRecording() {
        Log.d(TAG, "prepareForRecording: ");
        try {
            mOutputFile = createVideoOutputFile();
            android.graphics.Point size = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getRealSize(size);
            mSurfaceView.initRecorder(mOutputFile, size.x, size.y, null, null);

            // on some devices, this will not be on the UI thread when called from onSurfaceCreated
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRecordButton.setEnabled(true);
                }
            });
        } catch (IOException ioex) {
            Log.e(TAG, "Couldn't setup recording", ioex);
            Fa.get().exception(ioex, "Error setting up recording");
        }


    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        int rotation = Surface.ROTATION_0;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rotation = Surface.ROTATION_90;
        }
        mSession.setDisplayGeometry(rotation, width, height);
    }


    @Override
    public void onContextCreated() {
        mBackgroundRenderer.createOnGlThread(this);
        mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
        try {
            mLineShaderRenderer.createOnGlThread(DrawARActivity.this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mLineShaderRenderer.bNeedsUpdate.set(true);
    }

    @Override
    public void onPreDrawFrame() {
        update();
    }

    @Override
    public void onDrawFrame() {
        long renderStartTime = System.currentTimeMillis();

        renderScene();

        mRenderDuration = System.currentTimeMillis() - renderStartTime;
    }

    private void showStrokeDependentUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUndoButton.setVisibility(mStrokes.size() > 0 ? View.VISIBLE : View.GONE);
                mClearDrawingButton.setVisibility(
                        (mStrokes.size() > 0 || mSharedStrokes.size() > 0) ? View.VISIBLE
                                : View.GONE);
                mTrackingIndicator.setHasStrokes(mStrokes.size() > 0);
            }
        });
    }

    @Override
    public void onClearDrawingConfirmed() {
        bClearDrawing.set(true);
        showStrokeDependentUI();
    }

    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_message));
        startActivity(Intent.createChooser(intent, getString(R.string.share_with)));
    }


    @Override
    public void onClick(View v) {
        boolean hideOverflow = true;
        boolean hidePairToolTip = true;
        switch (v.getId()) {
            case R.id.button_overflow_menu:
                toggleOverflowMenu();
                hideOverflow = false;
                break;
            case R.id.menu_item_clear:
                onClickClear();
                break;
            case R.id.menu_item_about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_item_share_app:
                shareApp();
                mAnalytics.send(AnalyticsEvents.EVENT_TAPPED_SHARE_APP);
                break;
//            case R.id.menu_item_crash:
//                throw new RuntimeException("Intentional crash from overflow menu option");
//            case R.id.menu_item_hide_ui:
//                findViewById(R.id.draw_container).setVisibility(View.INVISIBLE);
//                break;
            case R.id.button_pair:
                if (mPairSessionManager.isInRoom()) {
                    LeaveRoomDialog.newInstance().show(this);
                } else if (mPairButtonToolTip.getVisibility() == View.GONE) {
                    mPairButtonToolTip.show();
                    mPairButton.setContentDescription(getString(R.string.content_description_close_join_friend_menu));
                    mPairButton.setAccessibilityTraversalBefore(R.id.pair_tooltip_title);
                    hidePairToolTip = false;
                }
                break;
        }
        mBrushSelector.close();
        if (hideOverflow) {
            hideOverflowMenu();
        }
        if (hidePairToolTip) {
            mPairButtonToolTip.hide();
            if (!mPairSessionManager.isPaired())
                mPairButton.setContentDescription(getString(R.string.content_description_join_friend));
        }
    }

    @Override
    public boolean onRequestRecordingStart() {
        return startRecording();
    }

    @Override
    public boolean onRequestRecordingStop() {
        return stopRecording();
    }

    @Override
    public void onRequestRecordingCancel() {
        try {
            mSurfaceView.stopRecording();
        } catch (RuntimeException e) {
            Fa.get().exception(e, "Error stopping recording during cancel");
        }

        // reset everything to try again
        onPlaybackClosed();

        mOverflowButton.setVisibility(View.VISIBLE);

        enableView(mPairButton);

    }

    @Override
    public void onBackPressed() {
        if (mPlaybackView.isOpen()) {
            mPlaybackView.close();
        } else if (mMode == Mode.PAIR_PARTNER_DISCOVERY || mMode == Mode.PAIR_ANCHOR_RESOLVING) {
            mPairView.hide();
            setMode(Mode.DRAW);
            mPairSessionManager.leaveRoom(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mPlaybackView == null || !mPlaybackView.isOpen()) {
            super.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public void onPlaybackClosed() {
        showView(mDrawUiContainer);
        showView(mTrackingIndicator);

        setupImmersive();
        mRecordButton.reset();
        prepareForRecording();
    }

    @Override
    public void requestStoragePermission() {
        PermissionHelper.requestStoragePermission(this, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == PermissionHelper.REQUEST_CODE_STORAGE_PERMISSIONS) {
            // send storage permissions result to playback view
            mPlaybackView.onRequestPermissionsResult(requestCode, permissions, grantResults);
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onPairPressed() {
        mPairSessionManager.startPairingSession(this);

        mPairButton.setContentDescription(getString(R.string.content_description_disconnect_from_friend));

        Fa.get().send(AnalyticsEvents.EVENT_TAPPED_START_PAIR);
        Fa.get().setUserProperty(AnalyticsEvents.USER_PROPERTY_HAS_TAPPED_PAIR,
                AnalyticsEvents.VALUE_TRUE);
    }

    @Override
    public void onJoinRoomPressed() {
        try {
            ((GlobalPairSessionManager) mPairSessionManager).joinGlobalRoom(this);
        } catch (ClassCastException e) {
            Fa.get().exception(e, "Join Room pressed in production app");
        }
    }

    /**
     * Update views for the given mode
     */
    private void setMode(Mode mode) {
        if (mMode != mode) {
            mMode = mode;

            switch (mMode) {
                case DRAW:
                    showView(mDrawUiContainer);
                    showView(mTrackingIndicator);
                    mTrackingIndicator.setDrawPromptEnabled(true);
                    mTrackingIndicator.removeListener(mPairView);
                    mPairView.hide();
                    break;
                case PAIR_ANCHOR_RESOLVING:
                    hideView(mDrawUiContainer);
                    mTrackingIndicator.setDrawPromptEnabled(false);
                    showView(mTrackingIndicator);
                    mTrackingIndicator.addListener(mPairView);
                    break;
                case PAIR_PARTNER_DISCOVERY:
                case PAIR_ERROR:
                case PAIR_SUCCESS:
                    hideView(mDrawUiContainer);
                    hideView(mTrackingIndicator);
                    mTrackingIndicator.setDrawPromptEnabled(false);
                    mTrackingIndicator.removeListener(mPairView);
                    mPairView.show();
                    mPairView.onErrorRemoved();
                    break;
            }
        }
    }

    @Override
    public void setAnchor(Anchor anchor) {
        mAnchor = anchor;

        for (Stroke stroke : mStrokes) {
            Log.d(TAG, "setAnchor: pushing line");
            stroke.offsetToPose(mAnchor.getPose());
            mPairSessionManager.addStroke(stroke);
        }

        mLineShaderRenderer.bNeedsUpdate.set(true);
    }

    @Override
    public void onModeChanged(Mode mode) {
        setMode(mode);
    }

    private void showView(View toShow) {
        toShow.setVisibility(View.VISIBLE);
        toShow.animate().alpha(1).start();
    }

    private void hideView(final View toHide) {
        toHide.animate().alpha(0).withEndAction(new Runnable() {
            @Override
            public void run() {
                toHide.setVisibility(View.GONE);
            }
        }).start();
    }

    public void enableView(View toEnable) {
        toEnable.setEnabled(true);
        toEnable.animate().alpha(1f);
    }

    public void disableView(View toDisable) {
        toDisable.setEnabled(false);
        toDisable.animate().alpha(.5f);
    }

    @Override
    public void onPairCanceled() {
        mPairView.hide();

        setMode(Mode.DRAW);

        mPairSessionManager.leaveRoom(false);
    }

    @Override
    public void onPairViewClosed() {
        setMode(Mode.DRAW);
    }

    @Override
    public void onReadyToSetAnchor() {
        mPairSessionManager.readyToSetAnchor();
        Fa.get().send(AnalyticsEvents.EVENT_TAPPED_READY_TO_SET_ANCHOR);
    }

    public void createAnchor() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Pose pose = mFrame.getCamera().getPose();

                try {
                    mAnchor = mSession.createAnchor(pose);
                } catch (NotTrackingException e) {
                    Log.e(TAG, "Cannot create anchor when not tracking", e);
                    mTrackingIndicator.addListener(new TrackingIndicator.DisplayListener() {
                        @Override
                        public void onErrorDisplaying() {
                            // Do nothing, can't set anchor
                        }

                        @Override
                        public void onErrorRemoved() {
                            mTrackingIndicator.removeListener(this);
                            createAnchor();
                        }
                    });
                    return;
                }

                mPairSessionManager.onAnchorCreated();
                if (mStrokes.size() > 0) {
                    for (int i = 0; i < mStrokes.size(); i++) {
                        mStrokes.get(i).offsetToPose(pose);
                        if (mStrokes.get(i).hasFirebaseReference())
                            mPairSessionManager.updateStroke(mStrokes.get(i));
                        else
                            mPairSessionManager.addStroke(mStrokes.get(i));
                    }
                    mLineShaderRenderer.bNeedsUpdate.set(true);
                }

                mPairSessionManager.setAnchor(mAnchor);
            }
        });
    }

    @Override
    public void clearLines() {
        mSharedStrokes.clear();
        mStrokes.clear();
        mLineShaderRenderer.bNeedsUpdate.set(true);
    }

    @Override
    public void onAnchorChangedLeftRoom() {
        ErrorDialog.newInstance(R.string.drawing_session_ended, false)
                .show(this);
    }

    @Override
    public void onConnectivityLostLeftRoom() {
        ErrorDialog.newInstance(R.string.pair_no_data_connection_title,
                R.string.pair_no_data_connection_body, false)
                .show(this);
    }

    @Override
    public void clearAnchor(Anchor anchor) {
        if (anchor != null && anchor.equals(mAnchor)) {
            for (Stroke stroke : mStrokes) {
                stroke.offsetFromPose(mAnchor.getPose());
            }
            mAnchor = null;
            Matrix.setIdentityM(mLineShaderRenderer.mModelMatrix, 0);
        }
    }

    @Override
    public void setRoomNumber(String roomKey) {
        if (mDebugEnabled) {
            mDebugView.setRoomNumber(roomKey);
        }
    }

    @Override
    public void onReadyResolveAnchor() {
        mPairSessionManager.resolveAnchorFromAnchorId();
    }

    @Override
    public void setAnchorResolvingMode() {
        setMode(Mode.PAIR_ANCHOR_RESOLVING);
    }

    @Override
    public void setPairErrorMode() {
        setMode(Mode.PAIR_ERROR);
    }

    @Override
    public void setPairSuccessMode() {
        setMode(Mode.PAIR_SUCCESS);
    }

    @Override
    public void attemptPartnerDiscovery() {
        mPairSessionManager.startPairingSession(this);
    }

    @Override
    public void onPartnerCountChanged(int partnerCount) {
        if (partnerCount < 2) {
            mPairActiveView.setText(R.string.partner_lost);
            mPairActiveView.setBackgroundResource(R.drawable.bg_pair_state_partner_lost);
        } else {
            mPairActiveView.setText(R.string.partner_paired);
            mPairActiveView.setBackgroundResource(R.drawable.bg_pair_state_paired);
        }
    }

    @Override
    public void onConnectedToSession() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPairSessionManager.setStrokeListener(DrawARActivity.this);
                mPairActiveView.setText(R.string.partner_paired);
                showView(mPairActiveView);

                mTrackingIndicator.setAnchorTrackingMessageEnabled(true);
                mTrackingIndicator.setShowPairedSessionDrawPrompt(true);
            }
        });
    }

    @Override
    public void onDisconnectedFromSession() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideView(mPairActiveView);

                mPairButton.setContentDescription(getString(R.string.content_description_join_friend));

                mTrackingIndicator.setAnchorTrackingMessageEnabled(false);
                mTrackingIndicator.setShowPairedSessionDrawPrompt(false);
            }
        });
    }

    @Override
    public void onLineAdded(String uid, Stroke value) {
        value.localLine = false;
        value.calculateTotalLength();
        mSharedStrokes.put(uid, value);
        showStrokeDependentUI();
        mLineShaderRenderer.bNeedsUpdate.set(true);
    }

    @Override
    public void onLineRemoved(String uid) {
        if (mSharedStrokes.containsKey(uid)) {
            mSharedStrokes.remove(uid);
            mLineShaderRenderer.bNeedsUpdate.set(true);
        } else {
            for (Stroke stroke : mStrokes) {
                if (uid.equals(stroke.getFirebaseKey())) {
                    mStrokes.remove(stroke);
                    if (!stroke.finished) {
                        bTouchDown.set(false);
                    }
                    mLineShaderRenderer.bNeedsUpdate.set(true);
                    break;
                }
            }
        }

        showStrokeDependentUI();
    }

    @Override
    public void onLineUpdated(String uid, Stroke value) {
        Stroke stroke = mSharedStrokes.get(uid);
        if (stroke == null) {
            return;
        }
        stroke.updateStrokeData(value);
        mLineShaderRenderer.bNeedsUpdate.set(true);
    }

    @Override
    public void exitApp() {
        finish();
    }

    @Override
    public void onExitRoomSelected() {
        mPairSessionManager.leaveRoom(true);
        Fa.get().send(AnalyticsEvents.EVENT_TAPPED_DISCONNECT_PAIRED_SESSION);
    }

}

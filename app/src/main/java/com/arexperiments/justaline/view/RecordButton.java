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

package com.arexperiments.justaline.view;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.arexperiments.justaline.R;
import com.arexperiments.justaline.analytics.AnalyticsEvents;
import com.arexperiments.justaline.analytics.Fa;

import java.util.concurrent.TimeUnit;

/**
 * Created by Kat on 11/13/17.
 * Custom view for record button on DrawARActivity
 */

public class RecordButton extends FrameLayout {

    private static final String TAG = "RecordButton";

    /**
     * Used to determine if user is attempting to tap to start recording or tap and hold
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long MAX_TAP_LENGTH = 40;

    /**
     * Media Recorder does not immediately start recording, prep time is used to show that something
     * is happening before showing that the media recorder is recording
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long RECORDING_PREP_TIME = 400;

    /**
     * The max duration for videos recorded in this app
     */
    private final long MAX_DURATION = TimeUnit.SECONDS.toMillis(10);

    /*
     * Views
     */
    private ImageView mStopButton;

    private View mRecordingBackground;

    private RecordButtonProgressBar mProgressBar;

    /**
     * Listener to inform of taps and maximum time reached
     */
    private Listener mListener;

    private Fa mAnalytics;

    /*
     * Recording and interaction state
     */

    private enum RecordingState {
        NOT_RECORDING, RECORDING_REQUESTED, RECORDING
    }

    private RecordingState mRecordingState = RecordingState.NOT_RECORDING;

    private boolean mTapTwo = false;

    private long mTapDownTime = -1;

    /*
     * Timer
     */
    private Handler mHandler;

    private Runnable mRecordingRunnable;

    private long mRecordStartTime = -1;

    private long mTick = 20;

    private boolean mCounterRunning = false;

    public RecordButton(Context context) {
        super(context);
        init();
    }

    public RecordButton(Context context,
                        @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_record_button, this);
        mAnalytics = Fa.get();

        mStopButton = findViewById(R.id.red_square);
        mRecordingBackground = findViewById(R.id.recording_background);
        mProgressBar = findViewById(R.id.progress);

        //
        // set up timer
        //
        mHandler = new Handler();
        mRecordingRunnable = new Runnable() {
            @Override
            public void run() {
                long counterDuration = getCurrentCounterDuration();
                mProgressBar.setCurrentDuration(counterDuration, MAX_DURATION);

                if (counterDuration >= MAX_DURATION) {
                    setRecording(false);
                    mHandler.removeCallbacks(mRecordingRunnable);
                } else if (mCounterRunning) {
                    mHandler.postDelayed(mRecordingRunnable, mTick);
                }
            }
        };
    }

    @Override
    public boolean performClick() {
        // accessibility events
        switch (mRecordingState) {
            case NOT_RECORDING:
                startRecordingPrep();

                // clear content description so no message is stated right when recording starts
                setContentDescription("");

                return super.performClick();
            case RECORDING:
                setRecording(false);

                setContentDescription(getContext().getString(R.string.content_description_record));

                mAnalytics.send(AnalyticsEvents.EVENT_RECORD,
                        AnalyticsEvents.PARAM_RECORD_METHOD,
                        AnalyticsEvents.VALUE_RECORD_METHOD_ACCESSIBLE_TAP);
                return super.performClick();
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isEnabled()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    switch (mRecordingState) {
                        case NOT_RECORDING:
                            // User is interacting with record button, initialize recording process
                            mTapDownTime = SystemClock.elapsedRealtime();
                            mTapTwo = false;
                            startRecordingPrep();
                            break;
                        case RECORDING_REQUESTED:
                            // continue prepping recording, treat as first tap
                            mTapTwo = true;
                            break;
                        default:
                            // user is tapping a second time intending to stop the recording
                            // we may stop the recording if enough time has passed since starting
                            mTapTwo = true;
                            break;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!isTouchEventInBounds(this, event) && !mTapTwo) {
                        // if user is holding button and then moves finger off of record button, cancel
                        cancelRecording();
                    } else if (!mTapTwo
                            && SystemClock.elapsedRealtime() - mTapDownTime <= MAX_TAP_LENGTH) {
                        // user tapped and released to start recording
                    } else if (mRecordingState != RecordingState.RECORDING
                            || (SystemClock.elapsedRealtime() - mTapDownTime)
                            < RECORDING_PREP_TIME) {
                        // user has either tapped twice or released their hold before recording has
                        // started
                        // allow recording to start
                    } else {
                        // user either tapped a second time or released their original tap
                        setRecording(false);

                        mAnalytics.send(AnalyticsEvents.EVENT_RECORD,
                                AnalyticsEvents.PARAM_RECORD_METHOD,
                                mTapTwo ? AnalyticsEvents.VALUE_RECORD_METHOD_TAP
                                        : AnalyticsEvents.VALUE_RECORD_METHOD_HOLD);
                    }

                    break;

            }
        }

        return true;
    }


    private boolean isTouchEventInBounds(View view, MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();

        Rect outRect = new Rect();
        int[] location = new int[2];
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);

        return outRect.contains(x, y);
    }

    private void startCounter() {
        mCounterRunning = true;
        mProgressBar.setCurrentDuration(getCurrentCounterDuration(), MAX_DURATION);
        mHandler.postDelayed(mRecordingRunnable, mTick);
    }

    private void stopCounter() {
        mCounterRunning = false;
        mHandler.removeCallbacks(mRecordingRunnable);
    }

    public void setRecording(boolean recording) {
        if (recording) {
            // set to on
            mStopButton.animate().scaleX(1).scaleY(1);
            mRecordStartTime = SystemClock.elapsedRealtime();
            startCounter();

            setContentDescription(getContext().getString(R.string.content_description_stop_recording));
        } else {

            if (mRecordingState == RecordingState.RECORDING && mListener != null) {
                mListener.onRequestRecordingStop();
            }
            resetAnimation();
        }
        mRecordingState = recording ? RecordingState.RECORDING : RecordingState.NOT_RECORDING;
    }

    public void cancelRecording() {
        Log.d(TAG, "cancelRecording");

        if (mRecordingState == RecordingState.RECORDING && mListener != null) {
            mListener.onRequestRecordingCancel();
        }
        resetAnimation();

        mRecordingState = RecordingState.NOT_RECORDING;
    }

    private void startRecordingPrep() {
        // tell listeners to start recording
        if (mListener != null) {
            mRecordingState = RecordingState.RECORDING_REQUESTED;
            boolean startSuccessful = mListener.onRequestRecordingStart();
            if (startSuccessful) {
                // show touch feedback that we are starting recording
                // when animation is finished, recording should have started
                mRecordingBackground.animate().scaleX(1).scaleY(1)
                        .setDuration(RECORDING_PREP_TIME)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animator) {

                            }

                            @Override
                            public void onAnimationEnd(Animator animator) {
                                // recording should have started, update our state
                                setRecording(true);
                            }

                            @Override
                            public void onAnimationCancel(Animator animator) {
                            }

                            @Override
                            public void onAnimationRepeat(Animator animator) {

                            }
                        });
            } else {
                reset();
            }
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void reset() {
        resetAnimation();
        mRecordStartTime = -1;
        mProgressBar.setCurrentDuration(0, MAX_DURATION);
        mProgressBar.reset();
    }

    private void resetAnimation() {
        mStopButton.animate().scaleX(0).scaleY(0);
        mRecordingBackground.animate().scaleX(0).scaleY(0).setListener(null);
        stopCounter();
    }

    private long getCurrentCounterDuration() {
        if (mRecordStartTime > 0) {
            return SystemClock.elapsedRealtime() - mRecordStartTime;
        } else {
            return 0;
        }
    }

    public boolean isRecording() {
        return mRecordingState == RecordingState.RECORDING;
    }

    public interface Listener {

        boolean onRequestRecordingStart();

        boolean onRequestRecordingStop();

        void onRequestRecordingCancel();
    }

}

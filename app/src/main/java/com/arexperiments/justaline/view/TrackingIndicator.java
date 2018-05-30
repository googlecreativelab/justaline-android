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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.arexperiments.justaline.R;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kaitlynanderson on 2/7/18.
 * Custom View for showing curly line while not tracking
 */

public class TrackingIndicator extends ConstraintLayout {

    private static final String TAG = "TrackingIndicator";

    private static final int ANIMATION_DURATION_FADE = 300;

    private static final int ANIMATION_DURATION_IMAGE = 1300;

    private static final int STATE_NONE = -1;

    private static final int STATE_NOT_TRACKING = 1;

    private static final int STATE_NOT_TRACKING_ESCALATED = 2;

    private static final int STATE_NOT_TRACKING_ANCHOR = 3;

    private static final int STATE_DRAW_PROMPT = 4;

    private static final int STATE_DRAW_PROMPT_PAIRED = 5;

    private static final float IMAGE_ANIMATION_DISTANCE_DP = 25;

    private static final int SURFACE_RENDER_TIMEOUT_INTERVAL = 3000;

    private View mImageView;

    private TextView mNotTrackingTextView;

    private TextView mNotTrackingEscalatedTextView;

    private TextView mAnchorNotTrackingTextView;

    private float mAnimationDistance;

    private int mState = TrackingIndicator.STATE_NONE;

    private boolean mAnimating = false;

    public TrackingState trackingState;

    private TrackingState anchorTrackingState;

    private DrawPrompt mDrawPrompt;

    private AtomicBoolean bHasDrawnInSession = new AtomicBoolean(false);

    private Handler mHandler;

    private Runnable mTrackingIndicatorTimeoutRunnable;

    private boolean mNotTrackingEscalating = false;

    private boolean mNotTrackingEscalated = false;

    private boolean mHasStrokes = false;

    private boolean mDrawPromptEnabled = true;

    private boolean mShowPairedSessionDrawPrompt = false;

    private boolean anchorTrackingMessageEnabled = false;


    public TrackingIndicator(Context context) {
        super(context);
        init();
    }

    public TrackingIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TrackingIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_finding_surfaces, this);

        float screenDensity = getContext().getResources().getDisplayMetrics().density;
        mAnimationDistance = screenDensity * IMAGE_ANIMATION_DISTANCE_DP;

        mImageView = findViewById(R.id.image_tracking);

        mNotTrackingTextView = findViewById(R.id.textView_looking);
        mNotTrackingEscalatedTextView = findViewById(R.id.textView_cant_find);
        mAnchorNotTrackingTextView = findViewById(R.id.textView_anchor);

        mDrawPrompt = findViewById(R.id.draw_prompt);

        mHandler = new Handler(Looper.getMainLooper());

        mTrackingIndicatorTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "tracking timeout, escalate");
                mNotTrackingEscalated = true;
            }
        };
    }

    private void setState(final int state) {
        final int prevState = mState;
        mState = state;
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                for (DisplayListener listener : listeners) {
                    if (!isErrorState(state) && isErrorState(prevState)) {
                        listener.onErrorRemoved();
                    } else {
                        listener.onErrorDisplaying();
                    }
                }

                switch (state) {
                    case STATE_NONE:
                        hideDrawPrompt();
                        stopTrackingAnimation();
                        fadeOutView(mImageView);
                        fadeOutView(mNotTrackingTextView);
                        fadeOutView(mNotTrackingEscalatedTextView);
                        fadeOutView(mAnchorNotTrackingTextView);
                        break;
                    case STATE_NOT_TRACKING:
                        hideDrawPrompt();
                        startTrackingAnimation();
                        fadeInView(mImageView);
                        fadeInView(mNotTrackingTextView);
                        fadeOutView(mAnchorNotTrackingTextView);
                        mNotTrackingEscalatedTextView.setAlpha(0f);
                        announceForAccessibility(getContext().getString(R.string.tracking_indicator_text_looking));
                        break;
                    case STATE_NOT_TRACKING_ESCALATED:
                        hideDrawPrompt();
                        startTrackingAnimation();
                        fadeInView(mImageView);
                        fadeOutView(mNotTrackingTextView);
                        fadeInView(mNotTrackingEscalatedTextView);
                        fadeOutView(mAnchorNotTrackingTextView);
                        announceForAccessibility(getContext().getString(R.string.tracking_indicator_text_cant_find));
                        break;
                    case STATE_NOT_TRACKING_ANCHOR:
                        hideDrawPrompt();
                        startTrackingAnimation();
                        fadeInView(mImageView);
                        fadeInView(mAnchorNotTrackingTextView);
                        fadeOutView(mNotTrackingTextView);
                        fadeOutView(mNotTrackingEscalatedTextView);
                        announceForAccessibility(getContext().getString(R.string.tracking_indicator_text_anchor_not_tracking));
                        break;
                    case STATE_DRAW_PROMPT:
                        mDrawPrompt.setPromptText(false);
                        showDrawPrompt();
                        stopTrackingAnimation();
                        fadeOutView(mImageView);
                        fadeOutView(mAnchorNotTrackingTextView);
                        fadeOutView(mNotTrackingTextView);
                        fadeOutView(mNotTrackingEscalatedTextView);
                        announceForAccessibility(getContext().getString(R.string.draw_prompt));
                        break;
                    case STATE_DRAW_PROMPT_PAIRED:
                        mDrawPrompt.setPromptText(true);
                        showDrawPrompt();
                        stopTrackingAnimation();
                        fadeOutView(mImageView);
                        fadeOutView(mAnchorNotTrackingTextView);
                        fadeOutView(mNotTrackingTextView);
                        fadeOutView(mNotTrackingEscalatedTextView);
                        announceForAccessibility(getContext().getString(R.string.draw_prompt_paired));
                        break;
                }
            }
        });
    }

    private void fadeInView(View view) {
        view.animate().setDuration(ANIMATION_DURATION_FADE).alpha(1f).start();
    }

    private void fadeOutView(View view) {
        view.animate().setDuration(ANIMATION_DURATION_FADE).alpha(0f).start();
    }

    private void showDrawPrompt() {
        mDrawPrompt.showPrompt(!bHasDrawnInSession.get() || mShowPairedSessionDrawPrompt);
    }

    private void hideDrawPrompt() {
        if (mDrawPrompt.isShowing()) {
            mDrawPrompt.hidePrompt();
        }
    }

    public int getState() {
        return mState;
    }

    private void startTrackingAnimation() {
        if (!mAnimating) {
            mAnimating = true;
            startForwardTrackingAnimation();
        }
    }

    private void startForwardTrackingAnimation() {
        if (isPowerSaveMode()) {
            return;
        }
        mImageView.animate().translationX(-mAnimationDistance).setDuration(ANIMATION_DURATION_IMAGE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mAnimating) {
                            startReverseTrackingAnimation();
                        } else {
                            mImageView.setTranslationX(0);
                        }
                    }
                }).start();
    }

    private void startReverseTrackingAnimation() {
        if (isPowerSaveMode()) {
            return;
        }
        mImageView.animate().translationX(mAnimationDistance).setDuration(ANIMATION_DURATION_IMAGE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mAnimating) {
                            startForwardTrackingAnimation();
                        } else {
                            mImageView.setTranslationX(0);
                        }
                    }
                }).start();
    }

    private void stopTrackingAnimation() {
        mAnimating = false;
    }

    public boolean isTracking() {
        return trackingState == TrackingState.TRACKING &&
                (anchorTrackingState == null || anchorTrackingState == TrackingState.TRACKING);
    }

    public void resetTrackingTimeout() {
        mNotTrackingEscalating = false;
        mNotTrackingEscalated = false;
        mHandler.removeCallbacks(mTrackingIndicatorTimeoutRunnable);
    }

    public void setTrackingStates(Frame frame, Anchor anchor) {
        trackingState = frame.getCamera().getTrackingState();
        anchorTrackingState = anchor == null ? null : anchor.getTrackingState();

        if (trackingState != TrackingState.TRACKING && !mNotTrackingEscalating) {
            mNotTrackingEscalating = true;
            mHandler.postDelayed(mTrackingIndicatorTimeoutRunnable,
                    SURFACE_RENDER_TIMEOUT_INTERVAL);
        }

        if (trackingState == TrackingState.TRACKING) {
            resetTrackingTimeout();
        }

        updateUI();
    }

    private void updateUI() {
        int state = STATE_NONE;
        if (anchorTrackingMessageEnabled && anchorTrackingState != null && anchorTrackingState != TrackingState.TRACKING) {
            state = STATE_NOT_TRACKING_ANCHOR;
        } else if (trackingState != TrackingState.TRACKING) {
            state = mNotTrackingEscalated ? STATE_NOT_TRACKING_ESCALATED : STATE_NOT_TRACKING;
        } else if (mDrawPromptEnabled && mShowPairedSessionDrawPrompt) {
            state = STATE_DRAW_PROMPT_PAIRED;
        } else if (!mHasStrokes && mDrawPromptEnabled) {
            state = STATE_DRAW_PROMPT;
        }

        if (mState != state) {
            Log.d(TAG, "updateUI: " + mState + " " + state + " " + getVisibility());
            setState(state);
        }
    }


    public void setDrawnInSession() {
        Log.d(TAG, "setDrawnInSession: ");
        bHasDrawnInSession.set(true);

        if (mShowPairedSessionDrawPrompt) {
            mShowPairedSessionDrawPrompt = false;
        }

        setHasStrokes(true);
    }

    public void setShowPairedSessionDrawPrompt(boolean showPairedSessionDrawPrompt) {
        mShowPairedSessionDrawPrompt = showPairedSessionDrawPrompt;
        updateUI();
    }

    public void setHasStrokes(boolean hasStrokes) {
        if (hasStrokes != mHasStrokes) {
            Log.d(TAG, "setHasStrokes: " + hasStrokes);
            mHasStrokes = hasStrokes;

            updateUI();
        }
    }

    public void setAnchorTrackingMessageEnabled(boolean anchorTrackingMessageEnabled) {
        this.anchorTrackingMessageEnabled = anchorTrackingMessageEnabled;
    }

    public void setDrawPromptEnabled(boolean drawPromptEnabled) {
        mDrawPromptEnabled = drawPromptEnabled;
        updateUI();
    }

    private List<DisplayListener> listeners = new ArrayList<>();

    public void addListener(DisplayListener displayListener) {
        if (displayListener != null) {
            listeners.add(displayListener);

            if (mState == STATE_NONE) {
                displayListener.onErrorRemoved();
            } else {
                displayListener.onErrorDisplaying();
            }
        }

    }

    public void removeListener(DisplayListener displayListener) {
        listeners.remove(displayListener);
    }

    private boolean isPowerSaveMode() {
        return ((PowerManager) getContext().getSystemService(Context.POWER_SERVICE))
                .isPowerSaveMode();
    }

    private boolean isErrorState(int state) {
        return state == STATE_NOT_TRACKING || state == STATE_NOT_TRACKING_ESCALATED
                || state == STATE_NOT_TRACKING_ANCHOR;
    }

    public interface DisplayListener {

        void onErrorDisplaying();

        void onErrorRemoved();
    }
}

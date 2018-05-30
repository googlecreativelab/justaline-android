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
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.arexperiments.justaline.R;

/**
 * Created by kaitlynanderson on 3/13/18.
 */

public class DrawPrompt extends FrameLayout {

    private static final String TAG = "DrawPrompt";

    private static final int FADE_DURATION = 250;

    private static final int REDUCED_DELAY = 200;

    private static final int SCALE_ANIMATION_DURATION = 600;

    private TextView mTextView;

    private ImageView mCircleView;

    private Animator.AnimatorListener mEnlargedListener;

    private Animator.AnimatorListener mReducedListener;

    private boolean isShowing = false;

    public DrawPrompt(Context context) {
        super(context);
        init();
    }

    public DrawPrompt(Context context,
                      @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawPrompt(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DrawPrompt(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                      int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_draw_prompt, this);

        mTextView = findViewById(R.id.textview);
        mCircleView = findViewById(R.id.image);
        setupListeners();
    }

    public void setPromptText(boolean pairedMessage) {
        if (pairedMessage) {
            mTextView.setText(R.string.draw_prompt_paired);
        } else {
            mTextView.setText(R.string.draw_prompt);
        }

    }

    public void showPrompt(boolean withText) {
        if (!isShowing) {
            isShowing = true;

            mTextView.setVisibility(withText ? View.VISIBLE : View.INVISIBLE);
            fadeInView(this);
            startReduceAnimation();
        }

        if (withText && mTextView.getVisibility() != VISIBLE) {
            mTextView.setVisibility(View.VISIBLE);
        } else if (!withText && mTextView.getVisibility() == VISIBLE) {
            mTextView.setVisibility(View.INVISIBLE);
        }
    }

    public void hidePrompt() {
        isShowing = false;
        fadeOutView(this);
        stopCircleAnimation();
    }

    public boolean isShowing() {
        return isShowing;
    }

    // Initialize listeners to instantiate looping of animations
    private void setupListeners() {
        mEnlargedListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // When we have finished the animation from small to big,
                // start animation from big to small
                if (isShowing) {
                    startReduceAnimation();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };
        mReducedListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // When we have finished the animation from right to left,
                // start animation from left to right
                if (isShowing) {
                    startEnlargeAnimation();
                }

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };
    }

    private void startEnlargeAnimation() {
        if (isPowerSaveMode()) {
            return;
        }
        mCircleView.animate().scaleX(1f).scaleY(1f).setListener(mEnlargedListener)
                .setDuration(SCALE_ANIMATION_DURATION)
                .setStartDelay(REDUCED_DELAY).start();
    }

    private void startReduceAnimation() {
        if (isPowerSaveMode()) {
            return;
        }
        mCircleView.animate().scaleY(0.66f).scaleX(0.66f).setListener(mReducedListener)
                .setDuration(SCALE_ANIMATION_DURATION).start();
    }


    private void fadeInView(View view) {
        view.animate().alpha(1f).setDuration(FADE_DURATION).start();
    }

    private void fadeOutView(View view) {
        view.animate().alpha(0f).setDuration(FADE_DURATION).start();
    }

    private void stopCircleAnimation() {
        mCircleView.animate().setListener(null);
    }

    private boolean isPowerSaveMode() {
        return ((PowerManager) getContext().getSystemService(Context.POWER_SERVICE))
                .isPowerSaveMode();
    }
}

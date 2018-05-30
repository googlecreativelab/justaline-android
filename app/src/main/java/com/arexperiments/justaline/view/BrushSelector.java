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
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.arexperiments.justaline.AppSettings;
import com.arexperiments.justaline.R;

/**
 * Created by Kat on 11/13/17.
 * Custom view for selecting brush size
 */

public class BrushSelector extends ConstraintLayout implements View.OnClickListener {

    private static final String TAG = "BrushSelector";

    private static final int SMALL_BRUSH = 0;

    private static final int MEDIUM_BRUSH = 1;

    private static final int LARGE_BRUSH = 2;

    private static final Pair<Integer, AppSettings.LineWidth> defaultBrush = new Pair<>(MEDIUM_BRUSH,
            AppSettings.LineWidth.MEDIUM);

    private View mBrushButton;

    private View mSmallButton, mMediumButton, mLargeButton;

    private View mSelectedSizeIndicator;

    private int mSelectedBrush = defaultBrush.first;

    private AppSettings.LineWidth mSelectedLineWidth = defaultBrush.second;

    private boolean mIsOpen = true;

    //the locations of the buttons
    private int mSmallButtonLoc[] = new int[2];

    private int mMediumButtonLoc[] = new int[2];

    private int mLargeButtonLoc[] = new int[2];

    public BrushSelector(Context context) {
        super(context);
        init();
    }

    public BrushSelector(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BrushSelector(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_brush_selector, this);

        mBrushButton = findViewById(R.id.brush_button);
        mBrushButton.setOnClickListener(this);

        mSelectedSizeIndicator = findViewById(R.id.selected_size_indicator);

        mSmallButton = findViewById(R.id.brush_selection_small);
        mMediumButton = findViewById(R.id.brush_selection_medium);
        mLargeButton = findViewById(R.id.brush_selection_large);

        mSmallButton.setOnClickListener(this);
        mMediumButton.setOnClickListener(this);
        mLargeButton.setOnClickListener(this);

        mBrushButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performClick();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && mIsOpen) {
                    //get the point where we let go
                    float yloc = event.getRawY();

                    AppSettings.LineWidth lineWidth;

                    //determine which button was released over
                    if (mSmallButtonLoc[1] < yloc && yloc < (mSmallButtonLoc[1] + mSmallButton
                            .getHeight())) {
                        //prevent calling an update when not needed
                        if (mSelectedBrush != SMALL_BRUSH) {
                            lineWidth = AppSettings.LineWidth.SMALL;
                            onBrushSizeSelected(lineWidth);
                        }
                    } else if (mMediumButtonLoc[1] < yloc && yloc < (mMediumButtonLoc[1]
                            + mMediumButton.getHeight())) {
                        if (mSelectedBrush != MEDIUM_BRUSH) {
                            lineWidth = AppSettings.LineWidth.MEDIUM;
                            onBrushSizeSelected(lineWidth);
                        }
                    } else if (mLargeButtonLoc[1] < yloc && yloc < (mLargeButtonLoc[1]
                            + mLargeButton.getHeight())) {
                        if (mSelectedBrush != LARGE_BRUSH) {
                            lineWidth = AppSettings.LineWidth.LARGE;
                            onBrushSizeSelected(lineWidth);
                        }
                    }

                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    //toggle if over a button
                    float yloc = event.getRawY();
                    if (mSmallButtonLoc[1] < yloc && yloc < (mSmallButtonLoc[1] + mSmallButton
                            .getHeight())) {
                        toggleBrushSelectorVisibility();
                    } else if (mMediumButtonLoc[1] < yloc && yloc < (mMediumButtonLoc[1]
                            + mMediumButton.getHeight())) {
                        toggleBrushSelectorVisibility();
                    } else if (mLargeButtonLoc[1] < yloc && yloc < (mLargeButtonLoc[1]
                            + mLargeButton.getHeight())) {
                        toggleBrushSelectorVisibility();
                    }
                }
                return true;
            }
        });

        this.post(new Runnable() {
            @Override
            public void run() {
                //the navigation bar is visible here at this point and throws off my location capture....
                //have to get the height to fix this
                mSmallButton.getLocationInWindow(mSmallButtonLoc);
                mMediumButton.getLocationInWindow(mMediumButtonLoc);
                mLargeButton.getLocationInWindow(mLargeButtonLoc);
            }
        });

        onBrushSizeSelected(defaultBrush.second);
        toggleBrushSelectorVisibility();
    }

    @Override
    public void onClick(View view) {

        AppSettings.LineWidth lineWidth = null;
        switch (view.getId()) {
            case R.id.brush_button:
                toggleBrushSelectorVisibility();
                return;
            case R.id.brush_selection_small:
                lineWidth = AppSettings.LineWidth.SMALL;
                break;
            case R.id.brush_selection_medium:
                lineWidth = AppSettings.LineWidth.MEDIUM;
                break;
            case R.id.brush_selection_large:
                lineWidth = AppSettings.LineWidth.LARGE;
                break;
        }

        onBrushSizeSelected(lineWidth);

        toggleBrushSelectorVisibility();
    }

    @Override
    public boolean performClick() {
        toggleBrushSelectorVisibility();
        return super.performClick();
    }

    private void onBrushSizeSelected(AppSettings.LineWidth lineWidth) {
        mSelectedLineWidth = lineWidth;

        TypedValue outValue = new TypedValue();

        switch (lineWidth) {
            case SMALL:
                getResources().getValue(R.dimen.brush_scale_small, outValue, true);
                mSelectedBrush = SMALL_BRUSH;
                break;
            case MEDIUM:
                getResources().getValue(R.dimen.brush_scale_medium, outValue, true);
                mSelectedBrush = MEDIUM_BRUSH;
                break;
            default:
            case LARGE:
                getResources().getValue(R.dimen.brush_scale_large, outValue, true);
                mSelectedBrush = LARGE_BRUSH;
                break;
        }

        float scale = outValue.getFloat();

        mSelectedSizeIndicator.animate().scaleX(scale).scaleY(scale);
    }

    private void toggleBrushSelectorVisibility() {
        if (mIsOpen) {
            float y = mSelectedSizeIndicator.getY();
            Animator.AnimatorListener hideListener = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mSmallButton.setVisibility(GONE);
                    mMediumButton.setVisibility(GONE);
                    mLargeButton.setVisibility(GONE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            };
            mSmallButton.animate().alpha(0).setListener(hideListener).translationY(y);
            mMediumButton.animate().alpha(0).translationY(y);
            mLargeButton.animate().alpha(0).translationY(y);
            mSmallButton.setEnabled(false);
            mMediumButton.setEnabled(false);
            mLargeButton.setEnabled(false);

            mBrushButton.setAccessibilityTraversalBefore(R.id.record_button);
        } else {
            Animator.AnimatorListener showListener = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mSmallButton.setVisibility(VISIBLE);
                    mMediumButton.setVisibility(VISIBLE);
                    mLargeButton.setVisibility(VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            };
            mSmallButton.animate().alpha(1).setListener(showListener).translationY(0);
            mMediumButton.animate().alpha(1).translationY(0);
            mLargeButton.animate().alpha(1).translationY(0);
            mSmallButton.setEnabled(true);
            mMediumButton.setEnabled(true);
            mLargeButton.setEnabled(true);

            mBrushButton.setAccessibilityTraversalBefore(R.id.brush_selection_small);
            mSmallButton.setAccessibilityTraversalBefore(R.id.brush_selection_medium);
            mMediumButton.setAccessibilityTraversalBefore(R.id.brush_selection_large);
        }
        mIsOpen = !mIsOpen;
    }

    public AppSettings.LineWidth getSelectedLineWidth() {
        return mSelectedLineWidth;
    }

    public boolean isOpen() {
        return mIsOpen;
    }

    public void close() {
        if (mIsOpen) {
            toggleBrushSelectorVisibility();
        }
    }

}

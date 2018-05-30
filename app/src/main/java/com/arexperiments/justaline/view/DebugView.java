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

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.arexperiments.justaline.BuildConfig;
import com.arexperiments.justaline.R;
import com.google.ar.core.Anchor;

/**
 * TODO: document your custom view class.
 */
public class DebugView extends FrameLayout {

    private static final String TAG = "DrawPrompt";

    private TextView mRenderTextView;

    private long frameNum = 0;

    private TextView mRoomNumberTextView;

    private TextView mAnchorTrackingTextView;

    public DebugView(Context context) {
        super(context);
        init();

    }

    public DebugView(Context context,
                     @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DebugView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    private void init() {
        inflate(getContext(), R.layout.view_debug, this);

        mRenderTextView = findViewById(R.id.render_info);
        setBuildInfo("Build: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        mRoomNumberTextView = findViewById(R.id.room_number);
        mAnchorTrackingTextView = findViewById(R.id.anchor_tracking);

    }

    public void setBuildInfo(String text) {
        ((TextView) findViewById(R.id.build_info)).setText(text);
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    public void setRenderInfo(int numPoints, long updateDuration, long renderDuration) {
        frameNum++;

        if (frameNum % 5 == 0) {
            mRenderTextView.setText(
                    String.format("Num points: %d Update: %dms render: %dms", numPoints,
                            updateDuration, renderDuration));
        }
    }

    public void setRoomNumber(String roomNumber) {
        if (roomNumber != null) {
            mRoomNumberTextView.setText(roomNumber);
        } else {
            mRoomNumberTextView.setText(null);
        }
    }

    public void setAnchorTracking(Anchor anchor) {
        String trackingStateString = null;
        if (anchor != null) {
            switch (anchor.getTrackingState()) {
                case TRACKING:
                    trackingStateString = "Anchor TRACKING";
                    break;
                case PAUSED:
                    trackingStateString = "Anchor PAUSED tracking";
                    break;
                case STOPPED:
                    trackingStateString = "Anchor STOPPED tracking";
                    break;
            }
        }
        mAnchorTrackingTextView.setText(trackingStateString);
    }


}

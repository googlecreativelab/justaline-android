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
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.arexperiments.justaline.BuildConfig;
import com.arexperiments.justaline.R;

/**
 * Created by Kat on 4/10/18.
 */

public class PairButtonToolTip extends LinearLayout {

    Button pairButton;

    Button joinRoomButton;

    private Listener listener;

    public PairButtonToolTip(Context context) {
        super(context);
        init();
    }

    public PairButtonToolTip(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PairButtonToolTip(Context context, AttributeSet attrs,
                             int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_pair_button_tool_tip, this);
        pairButton = findViewById(R.id.pair_tooltip_button);
        pairButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onPairPressed();
                }
                hide();
            }
        });

        joinRoomButton = findViewById(R.id.join_room_button);
        if (BuildConfig.GLOBAL) {
            joinRoomButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onJoinRoomPressed();
                    }
                    hide();
                }
            });
        } else {
            joinRoomButton.setVisibility(View.GONE);
        }
    }

    public void show() {
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        setVisibility(View.GONE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {

        void onPairPressed();

        void onJoinRoomPressed();

    }
}

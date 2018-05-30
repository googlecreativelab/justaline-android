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
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.arexperiments.justaline.PairSessionManager;
import com.arexperiments.justaline.R;

/**
 * Created by Kat on 3/29/18.
 */

public class PairButton extends ConstraintLayout
        implements PairSessionManager.PartnerUpdateListener {

    private ImageView mIcon;

    public PairButton(Context context) {
        super(context);
        init();
    }

    public PairButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PairButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_pair_button, this);
        mIcon = findViewById(R.id.icon);
    }

    private void setCount(int count) {
        if (count < 2) {
            mIcon.setActivated(false);
        } else {
            mIcon.setActivated(true);
        }
    }

    @Override
    public void onPartnerCountChanged(int partnerCount) {
        setCount(partnerCount);
    }

    @Override
    public void onConnectedToSession() {
        mIcon.setSelected(true);
    }

    @Override
    public void onDisconnectedFromSession() {
        mIcon.setSelected(false);
    }
}

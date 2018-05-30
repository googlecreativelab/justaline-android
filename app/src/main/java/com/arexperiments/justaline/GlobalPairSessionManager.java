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

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.arexperiments.justaline.view.PairView;
import com.google.ar.core.Anchor;

import static com.arexperiments.justaline.PairSessionManager.PairedState.JOINING;
import static com.arexperiments.justaline.PairSessionManager.PairedState.NOT_PAIRED;
import static com.arexperiments.justaline.view.PairView.PairState.GLOBAL_CONNECTING;
import static com.arexperiments.justaline.view.PairView.PairState.GLOBAL_RESOLVE_ERROR;

/**
 * Created by Kat on 4/10/18.
 */

public class GlobalPairSessionManager extends PairSessionManager
        implements GlobalRoomManager.GlobalRoomListener {

    private static final String TAG = "GlobalPairSessionManage";

    public GlobalPairSessionManager(Context context) {
        super(context);
    }

    @Override
    public RoomManager createRoomManager(Context context) {
        GlobalRoomManager globalRoomManager = new GlobalRoomManager(context);
        globalRoomManager.setGlobalRoomListener(this);
        return globalRoomManager;
    }

    @Override
    public void setupNearby(Activity activity) {
        // Nearby is not necessary for Global room
    }

    @Override
    protected void internalStartPairingSession(Activity activity) {
        mRoomDbManager.joinRoom(null, mUserUid, true, this, null);
    }

    @Override
    void handleResolveAnchorError(Anchor anchor, boolean notTracking,
                                  Anchor.CloudAnchorState state) {
        if (mPairedOrPairing == JOINING) {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(GLOBAL_RESOLVE_ERROR, state, notTracking);
            }

            if (anchor != null) {
                anchor.detach();
            }
        } else {
            super.handleResolveAnchorError(anchor, notTracking, state);
        }
    }

    public void joinGlobalRoom(Activity activity) {
        if (mPairedOrPairing == NOT_PAIRED) {
            internalJoinGlobalRoom(activity);
        }
    }

    @Override
    protected void internalJoinGlobalRoom(Activity activity) {
        if (mPairingStateChangeListener != null) {
            mPairingStateChangeListener.onStateChange(PairView.PairState.LOOKING);
        }
        if (mAnchorStateListener != null) {
            mAnchorStateListener.onModeChanged(DrawARActivity.Mode.PAIR_PARTNER_DISCOVERY);
        }
        if (!App.isOnline()) {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.OFFLINE);
            }
        } else if (mUserUid != null) {
            Log.d(TAG, "joinGlobalRoom: attempting to join room");
            mPartnerInFlow = true;
            mPairedOrPairing = JOINING;
            setupConnectionBroadcastReceiver();
            mRoomDbManager.joinRoom(null, mUserUid, false, this, null);
        } else {
            Log.d(TAG, "joinGlobalRoom: userUid not set");
            mPairedOrPairing = JOINING;
            if (!mLogInInProgress) {
                Log.d(TAG, "joinGlobalRoom: login not in progress, start");
                loginAnonymously(activity);
            }
        }
    }

    @Override
    public void roomDataReady() {
        // attempt to join global room
        if (mPairedOrPairing == JOINING) {
            mRoomDbManager.setAnchorCreationListener(this);
            mRoomDbManager.listenForAnchorErrors(mUserUid, this);
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(GLOBAL_CONNECTING);
            }
        }
    }

    @Override
    public PairedState getPairedState() {
        return mPairedOrPairing;
    }

}

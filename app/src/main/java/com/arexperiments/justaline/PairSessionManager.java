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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arexperiments.justaline.analytics.AnalyticsEvents;
import com.arexperiments.justaline.analytics.Fa;
import com.arexperiments.justaline.model.RoomData;
import com.arexperiments.justaline.model.Stroke;
import com.arexperiments.justaline.view.PairView;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.MessagesClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.Session;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.arexperiments.justaline.view.PairView.PairState.HOST_CONNECTING;
import static com.arexperiments.justaline.view.PairView.PairState.HOST_READY_AND_WAITING;
import static com.arexperiments.justaline.view.PairView.PairState.PARTNER_CONNECTED;
import static com.arexperiments.justaline.view.PairView.PairState.PARTNER_CONNECTING;
import static com.arexperiments.justaline.view.PairView.PairState.PARTNER_READY_AND_WAITING;
import static com.arexperiments.justaline.view.PairView.PairState.PARTNER_RESOLVE_ERROR;

/**
 * Created by Kat on 4/4/18.
 */

public class PairSessionManager
        implements RoomManager.PartnerListener, RoomManager.AnchorCreationListener,
        RoomManager.AnchorResolutionListener {

    private static final String TAG = "PairSessionManager";

    enum PairedState {
        NOT_PAIRED, PAIRING, PAIRED, JOINING
    }

    PairedState mPairedOrPairing = PairedState.NOT_PAIRED;

    boolean mPartnerInFlow = false;

    RoomManager mRoomDbManager;

    private final HostedAnchorManager mHostManager = new HostedAnchorManager();

    String mUserUid;

    private FirebaseAuth mFirebaseAuth;

    private Anchor mAnchor;

    PairingStateChangeListener mPairingStateChangeListener;

    AnchorStateListener mAnchorStateListener;

    private List<PartnerUpdateListener> mPartnerUpdateListeners = new ArrayList<>();

    private String mAnchorId;

    private BroadcastReceiver mConnectivityBroadcastReceiver;

    /*
     * Nearby broadcasting session
     */
    private MessageListener mMessageListener;

    private Message mMessage;

    private MessagesClient mMessagesClient;

    private boolean readyToSetAnchor = false, partnerReadyToSetAnchor = false;

    private static final long DISCOVERY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private static final long PAIR_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private Runnable mDiscoveryTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run: discovery TIMEOUT");
            stopRoomDiscovery();
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.DISCOVERY_TIMEOUT);
            }
            Fa.get().send(AnalyticsEvents.EVENT_PAIR_ERROR_DISCOVERY_TIMEOUT);
        }
    };

    private Runnable mPairTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run: pair TIMEOUT");

            mHostManager.cancelAnchorProcessing();

            if (mPairedOrPairing == PairedState.PAIRING) {
                mRoomDbManager.setAnchorResolutionError(mUserUid);
            }

            Fa.get().send(AnalyticsEvents.EVENT_PAIR_ERROR_SYNC, AnalyticsEvents.PARAM_PAIR_ERROR_SYNC_REASON, AnalyticsEvents.VALUE_PAIR_ERROR_SYNC_REASON_TIMEOUT);
        }
    };

    public PairSessionManager(Context context) {
        mFirebaseAuth = FirebaseAuth.getInstance();
        mRoomDbManager = createRoomManager(context);
    }

    public RoomManager createRoomManager(Context context) {
        return new RoomManager(context);
    }

    public void setupNearby(Activity activity) {
        // Nearby requires an activity context to avoid showing notification errors in place of
        // dialog errors
        mMessagesClient = Nearby.getMessagesClient(activity);

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(Message message) {
                RoomData roomData = new RoomData(message);
                Log.d(TAG, "Found message: " + message.getContent());

                boolean joinNewRoom = mRoomDbManager.shouldJoinReceivedRoom(roomData);
                if (joinNewRoom) {
                    // stop publishing our room and subscribing for others
                    stopRoomDiscovery();

                    // join their room
                    joinRoom(roomData);
                }
                // else {
                // Wait for other user to join our room
                //}
            }

            @Override
            public void onLost(Message message) {
                String messageString = new String(message.getContent());
                Log.d(TAG, "Lost sight of message: " + messageString);
            }
        };
    }

    public void login(Activity activity) {

        FirebaseUser currentUser = mFirebaseAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "onStart: user uid " + currentUser.getUid());
            mUserUid = currentUser.getUid();
        } else {
            loginAnonymously(activity);
        }
    }

    boolean mLogInInProgress = false;

    void loginAnonymously(final Activity activity) {
        mLogInInProgress = true;
        mFirebaseAuth.signInAnonymously()
                .addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        mLogInInProgress = false;
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mFirebaseAuth.getCurrentUser();
                            mUserUid = user.getUid();

                            // if pairing has started, continue now that user is logged in
                            if (mPairedOrPairing == PairedState.PAIRING) {
                                startPairingSession(activity);
                            } else if (mPairedOrPairing == PairedState.JOINING) {
                                internalJoinGlobalRoom(activity);
                            }
                        } else {
                            if (task.getException() != null) {
                                Fa.get().exception(task.getException(), "Could not log in");
                            }
                            // If sign in fails and user is attempting to pair, display a message to the user.
                            if (mPairedOrPairing == PairedState.PAIRING
                                    || mPairedOrPairing == PairedState.JOINING) {
                                if (mPairingStateChangeListener != null) {
                                    if (App.isOnline()) {
                                        mPairingStateChangeListener.onStateChange(
                                                PairView.PairState.UNKNOWN_ERROR);
                                    } else {
                                        mPairingStateChangeListener
                                                .onStateChange(PairView.PairState.OFFLINE);
                                    }
                                }
                                mPairedOrPairing = PairedState.NOT_PAIRED;
                            }
                        }
                    }
                });
    }

    public void setSession(Session session) {
        mHostManager.setSession(session);
    }

    /**
     * Start a pairing session by creating an entry in the Firebase database and subscribing for
     * others' rooms
     */
    void startPairingSession(Activity activity) {

        mPairedOrPairing = PairedState.PAIRING;

        if (mAnchorStateListener != null) {
            mAnchorStateListener.onModeChanged(DrawARActivity.Mode.PAIR_PARTNER_DISCOVERY);
        }

        if (App.isOnline()) {

            // show join views
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.LOOKING);
            }

            if (mUserUid == null) {
                Log.d(TAG, "startPairingSession: userUid not set");
                // cannot continue with pairing if user is not logged in
                if (!mLogInInProgress) {
                    Log.d(TAG, "startPairingSession: login not in progress, start");
                    loginAnonymously(activity);
                }
                return;
            }

            setupConnectionBroadcastReceiver();

            internalStartPairingSession(activity);
        } else {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.OFFLINE);
            }
        }

        mHandler.postDelayed(mDiscoveryTimeoutRunnable, DISCOVERY_TIMEOUT);
    }


    public void setupConnectionBroadcastReceiver() {
        mConnectivityBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isOnline = App.isOnline();
                if (!isOnline) {
                    Log.d(TAG, "setupConnectionBroadcastReceiver: NOT ONLINE");
                    leaveRoom(true);
                    if (mAnchorStateListener != null) {
                        mAnchorStateListener.onConnectivityLostLeftRoom();
                    }
                } else {
                    Log.d(TAG, "setupConnectionBroadcastReceiver: ONLINE");
                }
            }
        };

        Log.d(TAG, "setupConnectionBroadcastReceiver: SET LISTENER");
        App.get().registerReceiver(mConnectivityBroadcastReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * Called only when online and userUid has been set
     */
    protected void internalStartPairingSession(Activity activity) {
        if (mMessagesClient == null) {
            setupNearby(activity);
        }
        // listen for new messages
        mMessagesClient.subscribe(mMessageListener);

        // create a room
        createRoom(activity);
    }


    /**
     * Create an entry in the firebase database
     */
    private void createRoom(final Activity activity) {
        mRoomDbManager.createRoom(mUserUid, new RoomManager.StoreOperationListener() {
            @Override
            public void onRoomCreated(RoomData room, DatabaseError error) {
                if (room != null) {
                    mMessage = room.getMessage();

                    if (mMessagesClient == null) {
                        setupNearby(activity);
                    }
                    // broadcast room details
                    mMessagesClient.publish(mMessage);

                    if (mAnchorStateListener != null) {
                        mAnchorStateListener.setRoomNumber(room.key);
                    }
                } else {
                    Log.e(TAG, "Database error: " + String.valueOf(error) + " " + error
                            .getCode());
                    if (mPairingStateChangeListener != null) {
                        if (App.isOnline()) {
                            mPairingStateChangeListener
                                    .onStateChange(PairView.PairState.UNKNOWN_ERROR);
                        } else {
                            mPairingStateChangeListener.onStateChange(PairView.PairState.OFFLINE);
                        }
                    }
                }
            }
        }, this);
    }

    /**
     * Stop Nearby room discovery
     */
    private void stopRoomDiscovery() {
        if (mMessagesClient != null) {
            if (mMessage != null) {
                mMessagesClient.unpublish(mMessage);
                mMessage = null;
            }
            if (mMessageListener != null) {
                mMessagesClient.unsubscribe(mMessageListener);
            }
        }

        mHandler.removeCallbacks(mDiscoveryTimeoutRunnable);
    }

    /**
     * Add user to room
     */
    private void joinRoom(final RoomData room) {
        Log.d(TAG, "joinRoom: " + room.key);
        // leave current room
        leaveRoom(false);

        mPairedOrPairing = PairedState.PAIRING;
        // Get the anchor id and lines from Firebase database
        mRoomDbManager.joinRoom(room, mUserUid, true, this,
                new RoomManager.PartnerDetectionListener() {
                    @Override
                    public void onPartnersDetected() {

                    }

                    @Override
                    public void onNoPartnersDetected() {
                        onPartnerLeft(true, 1);
                    }
                });
        if (mAnchorStateListener != null) {
            mAnchorStateListener.setRoomNumber(room.key);
        }
    }

    protected void internalJoinGlobalRoom(Activity activity) {
        Fa.get().exception(new RuntimeException("Global room accessed from Production application"),
                "Join Room pressed in production app");
    }

    /**
     * @param isHosting
     */
    @Override
    public void onPartnerJoined(boolean partnerIsPairing, boolean isHosting, int numPartners) {
        if (mPairedOrPairing == PairedState.PAIRING && partnerIsPairing) {
            mPartnerInFlow = true;
            if (isHosting) {
                if (mPairingStateChangeListener != null) {
                    mPairingStateChangeListener
                            .onStateChange(PairView.PairState.HOST_CONNECTED);
                }
            } else {
                if (mPairingStateChangeListener != null) {
                    mPairingStateChangeListener.onStateChange(PARTNER_CONNECTED);
                }
            }
            stopRoomDiscovery();
        }

        for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
            listener.onPartnerCountChanged(numPartners);
        }
    }

    public void checkForPartners(RoomManager.PartnerDetectionListener partnerDetectionListener) {
        mRoomDbManager.checkForPartners(mUserUid, partnerDetectionListener);
    }

    @Override
    public void onPartnerReadyToSetAnchor(boolean isHost) {
        partnerReadyToSetAnchor = true;
        if (isHost) {
            if (readyToSetAnchor) {
                sendSetAnchorEvent();
            }
        } else {
            if (readyToSetAnchor) {
                if (mPairingStateChangeListener != null) {
                    mPairingStateChangeListener.onStateChange(PARTNER_CONNECTING);
                }
                mHandler.postDelayed(mPairTimeoutRunnable, PAIR_TIMEOUT);
            }
        }
    }

    public void readyToSetAnchor() {
        readyToSetAnchor = true;
        mRoomDbManager.setReadyToSetAnchor(mUserUid, this, this);

        if (mRoomDbManager.isHost && partnerReadyToSetAnchor) {
            sendSetAnchorEvent();
        } else if (mRoomDbManager.isHost) {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(HOST_READY_AND_WAITING);
            }
        } else if (partnerReadyToSetAnchor) {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PARTNER_CONNECTING);
            }
            mHandler.postDelayed(mPairTimeoutRunnable, PAIR_TIMEOUT);
        } else {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PARTNER_READY_AND_WAITING);
            }
        }
    }

    private void sendSetAnchorEvent() {
        Log.d(TAG, "sendSetAnchorEvent:");
        if (mAnchorStateListener != null) {
            mAnchorStateListener.createAnchor();
        }
        if (mPairingStateChangeListener != null) {
            mPairingStateChangeListener.onStateChange(HOST_CONNECTING);
        }

        mHandler.postDelayed(mPairTimeoutRunnable, PAIR_TIMEOUT);
    }

    /**
     * Set the anchor object and host it
     */
    public void setAnchor(Anchor anchor) {
        if (mAnchor == null) {
            mAnchor = anchor;

            mHostManager.hostAnchor(mAnchor,
                    new HostedAnchorManager.AnchorHostedListener() {
                        @Override
                        public void onAnchorHosted(Anchor anchor, final String anchorId,
                                                   final Anchor.CloudAnchorState state) {
                            if (isPairedOrPairing() && mPartnerInFlow) {
                                if (Anchor.CloudAnchorState.SUCCESS != state) {
//                                                showMessage("Anchor hosting failed: " + status);
                                    Log.d(TAG, "onAnchorHosted: NOT hosted " + anchorId
                                            + " " + state.toString());
                                    if (mAnchorStateListener != null) {
                                        mAnchorStateListener.clearAnchor(mAnchor);
                                    }
                                    mAnchor.detach();
                                    mAnchor = null;

                                    readyToSetAnchor = false;
                                    partnerReadyToSetAnchor = false;
                                    // UI state will be updated when the error is received in our
                                    // Anchor resolution listener
                                    mRoomDbManager.setAnchorResolutionError(mUserUid);

                                    Fa.get().send(AnalyticsEvents.EVENT_PAIR_ERROR_SYNC,
                                            AnalyticsEvents.PARAM_PAIR_ERROR_SYNC_REASON,
                                            state == null ? null : state.toString());
                                } else {
                                    Log.d(TAG, "onAnchorHosted: HOSTED" + anchorId + " "
                                            + state.toString());
                                    mAnchorId = anchorId;
                                    mRoomDbManager.setAnchorId(mUserUid, anchorId);
                                }
                            }

                        }
                    });
        }
    }

    /**
     * Update the HostManager with the most recent updatedAnchors to get host status
     */
    public void onUpdate(Collection<Anchor> updatedAnchors) {
        if (isInRoom()) {
            mHostManager.onUpdate(updatedAnchors);
        }
    }

    @Override
    public void onAnchorAvailable(String anchorId) {
        Log.d(TAG, "onAnchorAvailable");
        if (mAnchorId != null && mAnchorId.equals(anchorId)) {
            return;
        }

        mAnchorId = anchorId;
        resolveAnchorFromAnchorId();
    }

    @Override
    public void onNoAnchorAvailable() {
        if (mPairedOrPairing == PairedState.JOINING) {
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.GLOBAL_NO_ANCHOR);
            }
        } else if (mPairedOrPairing == PairedState.PAIRED) {
            leaveRoom(true);
            if (mAnchorStateListener != null) {
                mAnchorStateListener.onAnchorChangedLeftRoom();
            }

        }
    }

    @Override
    public void onAnchorResolutionError(String message) {

        mHandler.removeCallbacks(mPairTimeoutRunnable);

        partnerReadyToSetAnchor = false;
        readyToSetAnchor = false;

        mHostManager.cancelAnchorProcessing();

        if (mAnchor != null) {
            if (mAnchorStateListener != null) {
                mAnchorStateListener.clearAnchor(mAnchor);
            }
            mAnchor.detach();
            mAnchor = null;
        }
        mAnchorId = null;

        if (mPairingStateChangeListener != null) {
            if (mRoomDbManager.isHost) {
                mPairingStateChangeListener
                        .onStateChange(PairView.PairState.HOST_RESOLVE_ERROR, message);
            } else {
                mPairingStateChangeListener.onStateChange(PARTNER_RESOLVE_ERROR, message);
            }
        }
    }

    /**
     * Resolve an anchor with the given ID
     */
    public void resolveAnchorFromAnchorId() {
        mHostManager.resolveHostedAnchor(
                mAnchorId, new HostedAnchorManager.AnchorResolvedListener() {
                    @Override
                    public void onAnchorResolved(final Anchor anchor,
                                                 final Anchor.CloudAnchorState state, boolean notTracking) {
                        if (isInRoom() && mPartnerInFlow) {
                            if (notTracking || state != Anchor.CloudAnchorState.SUCCESS) {
                                handleResolveAnchorError(anchor, notTracking, state);

                                String reason = null;
                                if (notTracking) {
                                    reason = AnalyticsEvents.VALUE_PAIR_ERROR_SYNC_REASON_NOT_TRACKING;
                                } else if (state != null) {
                                    reason = state.toString();
                                }

                                Fa.get().send(AnalyticsEvents.EVENT_PAIR_ERROR_SYNC,
                                        AnalyticsEvents.PARAM_PAIR_ERROR_SYNC_REASON, reason);
                                return;
                            }
                            mAnchor = anchor;

                            // set anchor resolved in room before activity so that strokes
                            // can be added
                            mRoomDbManager.setAnchorResolved(mUserUid);

                            if (mAnchorStateListener != null) {
                                mAnchorStateListener.setAnchor(anchor);
                            }
                        }
                    }
                });
    }

    void handleResolveAnchorError(Anchor anchor, boolean notTracking,
                                  Anchor.CloudAnchorState state) {

        if (mPairingStateChangeListener != null) {
            mPairingStateChangeListener
                    .onStateChange(
                            PairView.PairState.PARTNER_RESOLVE_ERROR,
                            state, false);
        }

        if (anchor != null) {
            anchor.detach();
        }

        readyToSetAnchor = false;
        partnerReadyToSetAnchor = false;

        mRoomDbManager.setAnchorResolutionError(mUserUid);
    }

    @Override
    public void onPartnerAnchorResolved(boolean isHosting) {
        Log.d(TAG, "onPartnerAnchorResolved: " + isHosting);
        if (isHosting) {
            mPairedOrPairing = PairedState.PAIRED;
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.SYNCED);
            }
            for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
                listener.onConnectedToSession();
            }
            mHandler.removeCallbacks(mPairTimeoutRunnable);
            Fa.get().send(AnalyticsEvents.EVENT_PAIR_SUCCESS);
        }
    }

    @Override
    public void onMyAnchorResolutionAcknowledged() {

        if (mPairedOrPairing != PairedState.PAIRED) {
            mPairedOrPairing = PairedState.PAIRED;
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener
                        .onStateChange(PairView.PairState.SYNCED);
            }

            for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
                listener.onConnectedToSession();
            }

            mHandler.removeCallbacks(mPairTimeoutRunnable);
            Fa.get().send(AnalyticsEvents.EVENT_PAIR_SUCCESS);
        }
    }

    public void setStrokeListener(RoomManager.StrokeUpdateListener strokeListener) {
        mRoomDbManager.setStrokesListener(strokeListener);
    }

    @Override
    public void onPartnerLeft(boolean partnerWasPairing, int numParticipants) {
        if (partnerWasPairing) {
            mPartnerInFlow = false;
            if (mPairingStateChangeListener != null) {
                mPairingStateChangeListener.onStateChange(PairView.PairState.CONNECTION_LOST);
            }

            mHostManager.cancelAnchorProcessing();
        }

        for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
            listener.onPartnerCountChanged(numParticipants);
        }
    }

    public void onAnchorCreated() {
        mRoomDbManager.setRoomResolved(true);
    }

    public void addStroke(Stroke stroke) {
        mRoomDbManager.addStroke(mUserUid, stroke);
    }

    public void updateStroke(Stroke stroke) {
        mRoomDbManager.updateStroke(stroke);
    }

    public void undoStroke(Stroke stroke) {
        mRoomDbManager.undoStroke(stroke);
    }

    public void clearStrokes() {
        mRoomDbManager.clearStrokes(mUserUid);
    }

    public boolean isInRoom() {
        return mPairedOrPairing != PairedState.NOT_PAIRED;
    }

    private boolean isPairedOrPairing() {
        return mPairedOrPairing == PairedState.PAIRING || mPairedOrPairing == PairedState.PAIRED;
    }

    public boolean isPaired() {
        return mPairedOrPairing == PairedState.PAIRED;
    }

    void leaveRoom(boolean clearLines) {

        pauseListeners();

        if (mAnchorStateListener != null) {
            mAnchorStateListener.clearAnchor(mAnchor);
            mAnchorStateListener.setRoomNumber(null);
            if (clearLines)
                mAnchorStateListener.clearLines();
        }

        if (mAnchor != null) {
            mAnchor.detach();
            mAnchor = null;
        }
        mAnchorId = null;

        mPairedOrPairing = PairedState.NOT_PAIRED;
        mPartnerInFlow = false;

        for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
            listener.onDisconnectedFromSession();
            listener.onPartnerCountChanged(0);
        }

        mRoomDbManager.leaveRoom();
    }

    void pauseListeners() {

        if (mConnectivityBroadcastReceiver != null) {
            Log.d(TAG, "setupConnectionBroadcastReceiver: PAUSE LISTENER");
            App.get().unregisterReceiver(mConnectivityBroadcastReceiver);
            mConnectivityBroadcastReceiver = null;
        }

        mRoomDbManager.pauseListeners(mUserUid);

        stopRoomDiscovery();
        mHandler.removeCallbacks(mPairTimeoutRunnable);

        mHostManager.cancelAnchorProcessing();

        for (PartnerUpdateListener listener : mPartnerUpdateListeners) {
            listener.onPartnerCountChanged(1);
        }

        readyToSetAnchor = false;
        partnerReadyToSetAnchor = false;
    }

    public void resumeListeners(RoomManager.StrokeUpdateListener strokeUpdateListener) {

        setupConnectionBroadcastReceiver();

        mRoomDbManager.resumeListeners(mUserUid, strokeUpdateListener, this, this);
    }

    public void setPairingStateChangeListener(
            PairingStateChangeListener pairingStateChangeListener) {
        mPairingStateChangeListener = pairingStateChangeListener;
    }

    public void setAnchorStateListener(AnchorStateListener anchorStateListener) {
        mAnchorStateListener = anchorStateListener;
    }

    public void addPartnerUpdateListener(PartnerUpdateListener partnerUpdateListener) {
        mPartnerUpdateListeners.add(partnerUpdateListener);
    }

    public void removePartnerUpdateListener(PartnerUpdateListener partnerUpdateListener) {
        mPartnerUpdateListeners.remove(partnerUpdateListener);
    }

    public interface PairingStateChangeListener {

        void onStateChange(PairView.PairState state,
                           Anchor.CloudAnchorState cloudAnchorState, boolean notTracking);

        void onStateChange(PairView.PairState state);

        void onStateChange(PairView.PairState state, String message);
    }

    public interface PartnerUpdateListener {

        void onPartnerCountChanged(int partnerCount);

        void onConnectedToSession();

        void onDisconnectedFromSession();
    }

    public interface AnchorStateListener {

        void setAnchor(Anchor anchor);

        void createAnchor();

        void clearAnchor(Anchor anchor);

        void onModeChanged(DrawARActivity.Mode mode);

        void setRoomNumber(String roomNumber);

        void clearLines();

        void onAnchorChangedLeftRoom();

        void onConnectivityLostLeftRoom();
    }

}

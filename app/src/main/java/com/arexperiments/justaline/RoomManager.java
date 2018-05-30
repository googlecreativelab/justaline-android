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

import android.content.Context;
import android.util.Log;

import com.arexperiments.justaline.model.Anchor;
import com.arexperiments.justaline.model.Participant;
import com.arexperiments.justaline.model.RoomData;
import com.arexperiments.justaline.model.Stroke;
import com.arexperiments.justaline.model.StrokeUpdate;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.OnDisconnect;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to manage all communications with Firebase.
 */
/*package*/ class RoomManager {

    /**
     * A listener interface used for getting the results of a query made to the Firebase Database.
     */
    public interface AnchorCreationListener {

        void onAnchorAvailable(String anchorId);

        void onNoAnchorAvailable();
    }

    public interface AnchorResolutionListener {

        void onAnchorResolutionError(String message);
    }

    public interface StrokeUpdateListener {

        void onLineAdded(String uid, Stroke value);

        void onLineRemoved(String uid);

        void onLineUpdated(String uid, Stroke value);
    }

    /**
     * A listener interface for getting the room code and anchor ID of a store operation.
     */
    public interface StoreOperationListener {

        /**
         * This method is invoked when the room has been created and room code is available
         *
         * @param room  Data from the room that was created
         * @param error The database error.
         */
        void onRoomCreated(RoomData room, DatabaseError error);

    }

    public interface PartnerListener {

        void onPartnerJoined(boolean partnerIsPairing, boolean isHosting, int numPartners);

        void onPartnerAnchorResolved(boolean isHosting);

        void onPartnerLeft(boolean partnerWasPairing, int numParticipants);

        void onPartnerReadyToSetAnchor(boolean isHosting);

        void onMyAnchorResolutionAcknowledged();
    }

    interface PartnerDetectionListener {

        void onPartnersDetected();

        void onNoPartnersDetected();
    }


    private static final String TAG = "RoomManager";

    // Names of the nodes used in the Firebase Database
    private static final String ROOT_FIREBASE_ROOMS = "rooms";

    private static final String KEY_ANCHOR = "anchor";

    // This value must match the property name in Anchor.java
    private static final String KEY_ANCHOR_ERROR = "anchorResolutionError";

    private static final String KEY_TIMESTAMP = "updated_at_timestamp";

    private static final String KEY_STROKES = "lines";

    private static final String KEY_PARTICIPANTS = "participants";

    private static final String KEY_READY_TO_SET_ANCHOR = "readyToSetAnchor";

    final FirebaseApp app;

    /*
     * Track database to make new rooms
     */
    private final DatabaseReference roomsListRef;

    /*
     * Track data in our room
     */

    boolean isHost = false;

    private boolean isRoomResolved = false;

    RoomData mRoomData;

    private DatabaseReference roomRef;

    private DatabaseReference partnersRef;

    private List<String> partners = new ArrayList<>();

    private String pairingPartnerUid;

    protected boolean isPairing = false;

    private ChildEventListener lineListener;

    private ChildEventListener partnersListener;

    private ValueEventListener anchorResolutionListener;

    private OnDisconnect onDisconnectRef;

    private Set<String> localStrokeUids = new HashSet<>();

    /*
     * Partner listeners
     */
    private ValueEventListener anchorListener;

    private final Map<String, StrokeUpdate> strokeQueue = Collections
            .synchronizedMap(new LinkedHashMap<String, StrokeUpdate>());

    private Map<String, StrokeUpdate> completedStrokeUpdates = Collections
            .synchronizedMap(new LinkedHashMap<String, StrokeUpdate>());

    private List<String> uploadingStrokes = new ArrayList<>();

    /**
     * Default constructor for the FirebaseManager class.
     *
     * @param context The application context.
     */
    public RoomManager(Context context) {
        app = FirebaseApp.initializeApp(context);
        if (app != null) {
            final DatabaseReference rootRef = FirebaseDatabase.getInstance(app).getReference();
            roomsListRef = rootRef.child(ROOT_FIREBASE_ROOMS);

            DatabaseReference.goOnline();
        } else {
            Log.d(TAG, "Could not connect to Firebase Database!");
            roomsListRef = null;
        }
    }

    /**
     * Stores the anchor data in the specified game room.
     *
     * @param uid      the user's uid
     * @param listener The listener to be invoked once the operation is successful.
     */
    public void createRoom(final String uid, final StoreOperationListener listener,
                           final PartnerListener partnerListener) {
        if (app == null) {
            listener.onRoomCreated(null, null);
            return;
        }
        isPairing = true;

        Log.d(TAG, "Creating room");
        roomRef = roomsListRef.push();
        Log.d(TAG, "room Created: " + roomRef.getKey());

        isRoomResolved = true;
        Long timestamp = System.currentTimeMillis();
        roomRef.child(KEY_TIMESTAMP).setValue(timestamp);

        partnersRef = roomRef.child(KEY_PARTICIPANTS);
        Participant participant = Participant.readyToSetAnchor(false);
        partnersRef.child(uid).setValue(participant);
        onDisconnectRef = partnersRef.child(uid).onDisconnect();
        onDisconnectRef.removeValue();
        listenForPartners(uid, partnerListener);

        mRoomData = new RoomData(roomRef.getKey(), timestamp);
        listener.onRoomCreated(mRoomData, null);
    }

    private void listenForPartners(final String myUid, final PartnerListener partnerListener) {
        this.partnersListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Log.d(TAG,
                        "PARTICIPANTS onChildAdded: " + dataSnapshot.getKey() + " " + dataSnapshot
                                .getValue().toString());

                String uid = dataSnapshot.getKey();
                Participant partner = dataSnapshot.getValue(Participant.class);

                if (!uid.equals(myUid) && !partners.contains(uid)) {
                    partners.add(uid);

                    // only one other partner can be pairing at a time
                    boolean partnerIsPairing = partner.getPairing() && pairingPartnerUid == null;
                    if (partnerIsPairing) {
                        isHost = myUid.compareTo(uid) < 0;
                        pairingPartnerUid = uid;
                    }

                    if (partnerListener != null) {
                        partnerListener
                                .onPartnerJoined(partnerIsPairing, isHost, partners.size() + 1);
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.d(TAG, "PARTICIPANTS onChildChanged: " + dataSnapshot.getValue().toString());
                String partnerUid = dataSnapshot.getKey();
                if (partnerUid.equals(pairingPartnerUid)) {

                    Participant participant = dataSnapshot.getValue(Participant.class);

                    if (participant.getReadyToSetAnchor() && partnerListener != null) {
                        partnerListener.onPartnerReadyToSetAnchor(isHost);
                    } else if (participant.getAnchorResolved() && partnerListener != null && isHost) {
                        partnerListener.onPartnerAnchorResolved(isHost);
                        // set host to false to avoid anchor flow if/when partner rejoins room
                        isHost = false;
                        isPairing = false;
                        pairingPartnerUid = null;

                        // update participant to not pairing
                        Participant toUpdate = new Participant(true, isPairing);
                        partnersRef.child(myUid).setValue(toUpdate);

                    } else if (!participant.getPairing() && partnerListener != null) {
                        // pairing partner is no longer pairing
                        pairingPartnerUid = null;
                        partnerListener.onPartnerLeft(true, partners.size() + 1);
                    }
                } else if (partnerUid.equals(myUid) && !isHost) {
                    Participant participant = dataSnapshot.getValue(Participant.class);
                    if (participant.getAnchorResolved() && partnerListener != null) {
                        pairingPartnerUid = null;
                        partnerListener.onMyAnchorResolutionAcknowledged();
                    }
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d(TAG, "PARTICIPANTS onChildRemoved: " + dataSnapshot.getKey());

                String uid = dataSnapshot.getKey();

                for (String partnerUid : partners) {
                    if (uid.equals(partnerUid)) {
                        partners.remove(partnerUid);
                        break;
                    }
                }

                boolean wasPairingPartner = uid.equals(pairingPartnerUid);
                if (wasPairingPartner) {
                    pairingPartnerUid = null;
                }

                if (partnerListener != null) {
                    partnerListener.onPartnerLeft(wasPairingPartner, partners.size() + 1);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                Log.d(TAG, "PARTICIPANTS onChildMoved: " + dataSnapshot.getValue().toString());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "PARTICIPANTS onCancelled: ");
            }
        };
        partnersRef.addChildEventListener(partnersListener);
    }

    public void checkForPartners(final String userUid,
                                 final PartnerDetectionListener partnerDetectionListener) {
        if (userUid == null || partnerDetectionListener == null) {
            return;
        }

        if (partnersRef != null) {
            partnersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Iterator<DataSnapshot> it = dataSnapshot.getChildren().iterator();

                    while (it.hasNext()) {
                        DataSnapshot data = it.next();
                        String partnerUid = data.getKey();
                        if (!userUid.equals(partnerUid)) {
                            partnerDetectionListener.onPartnersDetected();
                            return;
                        }
                    }

                    partnerDetectionListener.onNoPartnersDetected();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    partnerDetectionListener.onNoPartnersDetected();
                }
            });
        } else {
            partnerDetectionListener.onNoPartnersDetected();
        }
    }

    public void setReadyToSetAnchor(String userUid, AnchorCreationListener anchorCreationListener,
                                    AnchorResolutionListener anchorResolutionListener) {
        Participant participant = Participant.readyToSetAnchor(true);
        partnersRef.child(userUid).setValue(participant);

        if (isPairing) {
            // must clear anchor before adding creation listener
            roomRef.child(KEY_ANCHOR).removeValue();
            setAnchorCreationListener(anchorCreationListener);
            listenForAnchorErrors(userUid, anchorResolutionListener);
        }
    }

    protected void listenForAnchorErrors(final String userUid,
                                         final AnchorResolutionListener listener) {
        if (this.anchorResolutionListener != null) {
            roomRef.child(KEY_ANCHOR).child(KEY_ANCHOR_ERROR)
                    .removeEventListener(this.anchorResolutionListener);
        }

        this.anchorResolutionListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean error = dataSnapshot.getValue(Boolean.class);
                Log.d(TAG, "anchor resolution changed: " + error);
                if (error != null && error && listener != null) {
                    roomRef.child(KEY_ANCHOR).child("anchorResolutionErrorMessage")
                            .addListenerForSingleValueEvent(
                                    new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            String message = dataSnapshot.getValue(String.class);
                                            listener.onAnchorResolutionError(message);
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });

                    Participant participant = Participant.readyToSetAnchor(false);
                    partnersRef.child(userUid).setValue(participant);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        roomRef.child(KEY_ANCHOR).child(KEY_ANCHOR_ERROR)
                .addValueEventListener(anchorResolutionListener);
    }

    public void setAnchorResolutionError(String userUid) {
        if (roomRef != null && partnersRef != null) {
            // update all users to not ready to set anchor
            Participant participant = new Participant(false, isPairing);
            partnersRef.child(userUid).setValue(participant);

            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put(KEY_READY_TO_SET_ANCHOR, false);
            for (String partnerUid : partners) {
                partnersRef.child(partnerUid).updateChildren(updateMap);
            }

            Log.d(TAG, "setAnchorResolutionError: ");
            // set anchor error
            Anchor anchor = Anchor.getAnchorWithResolutionError();
            roomRef.child(KEY_ANCHOR).setValue(anchor);
        }
    }

    public void setAnchorId(String userUid, String anchorId) {
        if (roomRef == null) {
            return;
        }
        Anchor anchor = new Anchor(anchorId);
        roomRef.child(KEY_ANCHOR).setValue(anchor);

        Participant participant = new Participant(true, isPairing);
        partnersRef.child(userUid).setValue(participant);

        isRoomResolved = true;
    }

    public void addStroke(String uid, Stroke stroke) {
        if (roomRef == null || !isRoomResolved) {
            return;
        }
        stroke.creator = uid;
        DatabaseReference strokeRef = roomRef.child(KEY_STROKES).push();
        localStrokeUids.add(strokeRef.getKey());
        stroke.setFirebaseReference(strokeRef);
        updateStroke(stroke);
    }

    public void updateStroke(Stroke stroke) {
        if (roomRef == null || !isRoomResolved) {
            return;
        }
        if (!stroke.hasFirebaseReference()) {
            throw new Error("Cant update line missing firebase reference");
        }

        StrokeUpdate strokeUpdate = new StrokeUpdate(stroke, false);
        queueStrokeUpdate(strokeUpdate);
    }

    private void queueStrokeUpdate(StrokeUpdate strokeUpdate) {
        boolean shouldQueue;

        synchronized (strokeQueue) {
            shouldQueue = uploadingStrokes.size() > 0;
        }

        if (shouldQueue) {
            Log.d(TAG, "strokeQueue: queueStrokeUpdate: queuing");
            // add stroke update to queue
            synchronized (strokeQueue) {
                strokeQueue.put(strokeUpdate.stroke.getFirebaseKey(), strokeUpdate);
            }

        } else {
            Log.d(TAG, "strokeQueue: queueStrokeUpdate: perform update");
            doStrokeUpdate(strokeUpdate);
        }

    }

    private void doStrokeUpdate(final StrokeUpdate strokeUpdate) {
        if (strokeUpdate.remove) {
            strokeUpdate.stroke.removeFirebaseValue();
        } else {
            DatabaseReference.CompletionListener completionListener
                    = new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError,
                                       DatabaseReference databaseReference) {
                    synchronized (strokeQueue) {
                        completedStrokeUpdates
                                .put(strokeUpdate.stroke.getFirebaseKey(), strokeUpdate);
                        uploadingStrokes.remove(strokeUpdate.stroke.getFirebaseKey());
                        Iterator<Map.Entry<String, StrokeUpdate>> i = strokeQueue.entrySet().iterator();
                        if (i.hasNext()) {
                            Map.Entry<String, StrokeUpdate> entry
                                    = i.next();
                            i.remove();
                            queueStrokeUpdate(entry.getValue());
                        }
                    }
                }
            };
            synchronized (strokeQueue) {
                uploadingStrokes.add(strokeUpdate.stroke.getFirebaseKey());
                strokeUpdate.stroke.setFirebaseValue(strokeUpdate,
                        completedStrokeUpdates.get(strokeUpdate.stroke.getFirebaseKey()),
                        completionListener);
            }
        }
    }

    public void undoStroke(Stroke stroke) {
        if (roomRef == null || !isRoomResolved) {
            return;
        }
        if (stroke.hasFirebaseReference()) {
            StrokeUpdate strokeUpdate = new StrokeUpdate(stroke, true);
            doStrokeUpdate(strokeUpdate);
        }

    }

    public void clearStrokes(String uid) {
        if (roomRef == null || !isRoomResolved) {
            return;
        }
        roomRef.child(KEY_STROKES).removeValue();
        synchronized (strokeQueue) {
            uploadingStrokes.clear();
            strokeQueue.clear();
        }
    }

    /**
     * Reads the current AnchorData from the specified game room.
     *
     * @param roomData The game room number.
     * @return False if a connection to the Firebase Database could not be established, and true
     * otherwise.
     */
    public boolean joinRoom(RoomData roomData, String uid, boolean isPairing,
                            PartnerListener partnerListener, PartnerDetectionListener partnerDetectionListener) {
        this.isPairing = isPairing;
        mRoomData = roomData;

        return joinRoomInternal(roomData.key, uid, partnerListener, partnerDetectionListener);
    }

    // Private method that is invoked with a listener that is never null.
    protected boolean joinRoomInternal(String roomKey, String uid,
                                       PartnerListener partnerListener, PartnerDetectionListener partnerDetectionListener) {
        if (app == null) {
            return false;
        }

        roomRef = roomsListRef.child(roomKey);
        // Let originating user know that another user is here
        partnersRef = roomRef.child(KEY_PARTICIPANTS);
        Participant participant = new Participant(false, isPairing);
        partnersRef.child(uid).setValue(participant);
        onDisconnectRef = partnersRef.child(uid).onDisconnect();
        onDisconnectRef.removeValue();

        listenForPartners(uid, partnerListener);
        checkForPartners(uid, partnerDetectionListener);
        return true;
    }

    public void setAnchorCreationListener(final AnchorCreationListener anchorCreationListener) {
        // remove previous listener if one is set
        if (anchorListener != null) {
            roomRef.child(KEY_ANCHOR).removeEventListener(anchorListener);
        }

        Log.d(TAG, "setAnchorCreationListener");
        // listen for anchor updates
        anchorListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Anchor anchor = null;
                if (dataSnapshot.getValue() != null) {
                    anchor = dataSnapshot.getValue(Anchor.class);
                }
                if (!isHost && anchor != null && anchor.getAnchorId() != null
                        && anchorCreationListener != null) {

                    anchorCreationListener.onAnchorAvailable(anchor.getAnchorId());

                    Log.d(TAG,
                            "setAnchorCreationListener: anchor available, remove anchor creation listener");

                } else if ((anchor == null || anchor.getAnchorId() == null) && anchorCreationListener != null) {
                    anchorCreationListener.onNoAnchorAvailable();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        roomRef.child(KEY_ANCHOR).addValueEventListener(anchorListener);
    }

    public void setAnchorResolved(String uid) {
        this.isRoomResolved = true;

        isPairing = false;

        Participant participant = new Participant(true, isPairing);
        partnersRef.child(uid).setValue(participant);
    }

    void setStrokesListener(final StrokeUpdateListener updateListener) {
        if (lineListener != null) {
            roomRef.child(KEY_STROKES).removeEventListener(lineListener);
        }
        lineListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Log.d(TAG, "LINE onChildAdded: " + dataSnapshot.getValue().toString());
                String uid = dataSnapshot.getKey();
                if (!localStrokeUids.contains(uid)) {

                    if (updateListener != null) {
                        Stroke stroke;
                        try {
                            stroke = dataSnapshot.getValue(Stroke.class);
                        } catch (DatabaseException e) {
                            // lines were cleared while someone was mid-stroke, a partial line was pushed
                            // stroke does not have lineWidth or creator, ignore it
                            return;
                        }
                        updateListener.onLineAdded(uid, stroke);
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.d(TAG, "LINE onChildChanged: ");
                String uid = dataSnapshot.getKey();
                if (!localStrokeUids.contains(uid)) {
                    Stroke stroke;
                    try {
                        stroke = dataSnapshot.getValue(Stroke.class);
                    } catch (DatabaseException e) {
                        // lines were cleared while someone was mid-stroke, a partial line was pushed
                        // stroke does not have lineWidth or creator, ignore it
                        return;
                    }
                    if (updateListener != null) {
                        updateListener.onLineUpdated(uid, stroke);
                    }
                } else {
                    try {
                        dataSnapshot.getValue(Stroke.class);
                    } catch (DatabaseException e) {
                        // update for local line was pushed simultaneously with
                        // a clear from another device. If this occurs,
                        // we will not receive an onChildRemoved callback.
                        // remove our local line
                        if (updateListener != null) {
                            updateListener.onLineRemoved(uid);
                        }
                    }
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d(TAG, "LINE onChildRemoved: " + dataSnapshot.getValue().toString());
                String uid = dataSnapshot.getKey();
                if (localStrokeUids.contains(uid)) {
                    localStrokeUids.remove(uid);
                }
                synchronized (strokeQueue) {
                    if (strokeQueue.containsKey(uid)) {
                        strokeQueue.remove(uid);
                    }
                    if (completedStrokeUpdates.containsKey(uid)) {
                        completedStrokeUpdates.remove(uid);
                    }
                }
                if (updateListener != null) {
                    updateListener.onLineRemoved(uid);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        roomRef.child(KEY_STROKES).addChildEventListener(lineListener);
    }

    public void setRoomResolved(boolean roomResolved) {
        this.isRoomResolved = roomResolved;
    }

    public boolean isRoomResolved() {
        return isRoomResolved;
    }

    public boolean shouldJoinReceivedRoom(RoomData roomData) {
        if (mRoomData == null) {
            // do not have a room data, join the new room
            return true;
        } else {
            return mRoomData.key.compareTo(roomData.key) > 0;
        }
    }

    public void leaveRoom() {
        isRoomResolved = false;
        isHost = false;
        partners.clear();
        pairingPartnerUid = null;

        roomRef = null;

        mRoomData = null;

        synchronized (strokeQueue) {
            strokeQueue.clear();
            completedStrokeUpdates.clear();
        }
        localStrokeUids.clear();
    }

    public void pauseListeners(String uid) {
        if (roomRef != null) {

            // stop listening for anchor to be set
            if (anchorListener != null) {
                roomRef.child(KEY_ANCHOR).removeEventListener(anchorListener);
                anchorListener = null;
            }

            // remove line listener
            if (lineListener != null) {
                roomRef.child(KEY_STROKES).removeEventListener(lineListener);
                lineListener = null;
            }

            // remove user from participants list
            if (roomRef != null) {
                roomRef.child(KEY_PARTICIPANTS).child(uid).removeValue();
                if (onDisconnectRef != null) {
                    onDisconnectRef.cancel();
                    onDisconnectRef = null;
                }
            }

            // remove partner listeners
            if (partnersListener != null && partnersRef != null) {
                // clear partners so we can rebuild when we resume listening
                partners.clear();
                partnersRef.removeEventListener(partnersListener);
                partnersRef = null;
            }

            // stop listening for anchor resolution errors
            if (anchorResolutionListener != null) {
                roomRef.child(KEY_ANCHOR).child(KEY_ANCHOR_ERROR)
                        .removeEventListener(anchorResolutionListener);
            }
        }
    }

    public void resumeListeners(String userUid, StrokeUpdateListener strokeUpdateListener,
                                PartnerListener partnerListener, AnchorCreationListener anchorCreationListener) {
        if (roomRef != null) {

            partnersRef = roomRef.child(KEY_PARTICIPANTS);
            setStrokesListener(strokeUpdateListener);

            listenForPartners(userUid, partnerListener);

            isPairing = false;
            Participant participant = new Participant(true, isPairing);
            partnersRef.child(userUid).setValue(participant);
            onDisconnectRef = partnersRef.child(userUid).onDisconnect();
            onDisconnectRef.removeValue();

            setAnchorCreationListener(anchorCreationListener);
        }
    }

}


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

import com.arexperiments.justaline.model.RoomData;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Created by Kat on 4/10/18.
 */

public class GlobalRoomManager extends RoomManager {

    private static final String TAG = "GlobalRoomManager";

    private static String ROOT_GLOBAL_ROOM = "global_rooms/global_room";

    private static String globalRoomName = "";

    private DatabaseReference globalRoomRef;

    private GlobalRoomListener globalRoomListener;

    /**
     * Default constructor for the FirebaseManager class.
     *
     * @param context The application context.
     */
    public GlobalRoomManager(Context context) {
        super(context);
    }

    public static void setGlobalRoomName(String name) {
        Log.i(TAG, "Set global room name: " + name);
        globalRoomName = name;
    }

    @Override
    public boolean joinRoom(final RoomData roomData, final String uid, final boolean isPairing,
                            final PartnerListener partnerListener, final PartnerDetectionListener partnerDetectionListener) {
        isHost = false;
        this.isPairing = isPairing;

        final DatabaseReference rootRef = FirebaseDatabase.getInstance(app).getReference();
        globalRoomRef = rootRef.child(ROOT_GLOBAL_ROOM + "_" + globalRoomName);

        globalRoomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String roomKey = dataSnapshot.getValue(String.class);
                Log.d(TAG, "received room key: " + roomKey);

                // Create a room if no global room is specified
                if (roomKey == null) {
                    createRoom(uid, new RoomManager.StoreOperationListener() {
                        @Override
                        public void onRoomCreated(RoomData room, DatabaseError error) {
                            globalRoomRef.setValue(room.key);
                        }
                    }, partnerListener);
                } else {
                    mRoomData = new RoomData(roomKey, System.currentTimeMillis());

                    joinRoomInternal(roomKey, uid, partnerListener, partnerDetectionListener);
                    if (globalRoomListener != null) {
                        globalRoomListener.roomDataReady();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO: handle cancelled event
            }
        });

        return true;
    }

    public void setGlobalRoomListener(
            GlobalRoomListener globalRoomListener) {
        this.globalRoomListener = globalRoomListener;
    }

    @Override
    protected void listenForAnchorErrors(String userUid,
                                         AnchorResolutionListener listener) {

        if (globalRoomListener != null
                && globalRoomListener.getPairedState() != PairSessionManager.PairedState.JOINING) {
            super.listenForAnchorErrors(userUid, listener);
        }
    }

    interface GlobalRoomListener {

        void roomDataReady();

        PairSessionManager.PairedState getPairedState();
    }
}

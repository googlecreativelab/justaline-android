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

package com.arexperiments.justaline.model;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;

/**
 * Created by Kat on 4/11/18.
 */

public class Participant {

    @PropertyName("readyToSetAnchor")
    Boolean readyToSetAnchor;

    @PropertyName("anchorResolved")
    Boolean anchorResolved;

    @PropertyName("pairing")
    Boolean isPairing;

    @PropertyName("lastSeen")
    HashMap<String, Object> lastSeen;

    public Participant() {
    }

    public Participant(Boolean anchorResolved, Boolean isPairing) {
        this.readyToSetAnchor = false;
        this.anchorResolved = anchorResolved;
        this.isPairing = isPairing;
        this.lastSeen = new HashMap<>();
        lastSeen.put("timestamp", ServerValue.TIMESTAMP);
    }

    public static Participant readyToSetAnchor(boolean readyToSetAnchor) {
        Participant participant = new Participant();
        participant.readyToSetAnchor = readyToSetAnchor;
        participant.anchorResolved = false;
        participant.isPairing = true;
        participant.lastSeen = new HashMap<>();
        participant.lastSeen.put("timestamp", ServerValue.TIMESTAMP);
        return participant;
    }

    public Boolean getReadyToSetAnchor() {
        return readyToSetAnchor == null ? false : readyToSetAnchor;
    }

    public Boolean getAnchorResolved() {
        return anchorResolved == null ? false : anchorResolved;
    }

    public void setReadyToSetAnchor(Boolean readyToSetAnchor) {
        this.readyToSetAnchor = readyToSetAnchor;
    }

    public void setAnchorResolved(Boolean anchorResolved) {
        this.anchorResolved = anchorResolved;
    }

    public void setPairing(Boolean present) {
        isPairing = present;
    }

    public void setLastSeen(HashMap<String, Object> lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Boolean getPairing() {
        return isPairing == null ? false : isPairing;
    }

    public HashMap<String, Object> getLastSeen() {
        return lastSeen;
    }

    @Exclude
    public long getLastSeenLong() {
        return (long) lastSeen.get("timestamp");
    }
}

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The format of the data stored in a game room dir in the Firebase database.
 */
public class FirebaseAnchorData {

    public String anchorId;

    public Map<String, List<Stroke>> strokes;

    public FirebaseAnchorData() {
        // Constructor required for calls to DataSnapshot.getValue(FirebaseAnchorData.class).
    }

    public FirebaseAnchorData(String anchorId, String uid, List<Stroke> strokes) {
        this.anchorId = anchorId;
        this.strokes = new HashMap<>();
        this.strokes.put(uid, strokes);
    }

    public FirebaseAnchorData(String anchorId, Map<String, List<Stroke>> userStrokesMap) {
        this.anchorId = anchorId;

        strokes = userStrokesMap;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public List<Stroke> getLinesList(String key) {
        return strokes.get(key);
    }
}


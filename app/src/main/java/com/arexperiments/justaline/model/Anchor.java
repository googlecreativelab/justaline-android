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

import com.google.firebase.database.PropertyName;

/**
 * Created by Kat on 4/3/18.
 */

public class Anchor {

    @PropertyName("anchorId")
    protected String anchorId;

    @PropertyName("anchorResolutionError")
    protected boolean anchorResolutionError;

    public Anchor() {
        // Default constructor required for calls to DataSnapshot.getValue(Anchor.class)
    }

    public Anchor(String anchorId) {
        this.anchorId = anchorId;
        this.anchorResolutionError = false;
    }

    public static Anchor getAnchorWithResolutionError() {
        Anchor anchor = new Anchor();
        anchor.anchorId = null;
        anchor.anchorResolutionError = true;
        return anchor;
    }


    public String getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public boolean isAnchorResolutionError() {
        return anchorResolutionError;
    }

}

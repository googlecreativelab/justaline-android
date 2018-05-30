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

/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arexperiments.justaline;


import javax.vecmath.Vector3f;

public class AppSettings {

    private static final Vector3f color = new Vector3f(1f, 1f, 1f);

    private static final float strokeDrawDistance = 0.13f;

    private static final float minDistance = 0.000001f;

    private static final float nearClip = 0.001f;

    private static final float farClip = 100.0f;

    private static final float smoothing = 0.07f;

    private static final int smoothingCount = 1500;

    public enum LineWidth {
        SMALL(0.006f),
        MEDIUM(0.011f),
        LARGE(0.020f);

        private final float width;

        LineWidth(float i) {
            this.width = i;
        }

        public float getWidth() {
            return width;
        }
    }

    public static float getStrokeDrawDistance() {
        return strokeDrawDistance;
    }

    public static Vector3f getColor() {
        return color;
    }

    public static float getMinDistance() {
        return minDistance;
    }

    static float getNearClip() {
        return nearClip;
    }

    static float getFarClip() {
        return farClip;
    }

    public static float getSmoothing() {
        return smoothing;
    }

    public static int getSmoothingCount() {
        return smoothingCount;
    }
}

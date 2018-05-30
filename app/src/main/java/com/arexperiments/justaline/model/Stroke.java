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

import com.arexperiments.justaline.AppSettings;
import com.arexperiments.justaline.BiquadFilter;
import com.arexperiments.justaline.rendering.LineUtils;
import com.google.ar.core.Pose;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3f;

/**
 * Created by Kat on 11/6/17.
 * Single line stroke model for AR
 */
@IgnoreExtraProperties
public class Stroke {

    private static final String TAG = "Stroke";

    @PropertyName("points")
    private ArrayList<Vector3f> points = new ArrayList<>();

    @PropertyName("lineWidth")
    private float lineWidth;

    @PropertyName("creator")
    public String creator = "";

    @Exclude
    private BiquadFilter biquadFilter;

    @Exclude
    private BiquadFilter animationFilter;

    @Exclude
    public boolean localLine = true;

    @Exclude
    public float animatedLength = 0;

    @Exclude
    public float totalLength = 0;

    @Exclude
    private DatabaseReference firebaseReference;

    @Exclude
    public boolean finished = false;


    public Stroke() {
        // Default constructor required for calls to DataSnapshot.getValue(Stroke.class)
        animationFilter = new BiquadFilter(0.025, 1);
    }

    // Add point to stroke
    public void add(Vector3f point) {
        int s = points.size();

        if (s == 0) {
            // Prepare the biquad filter
            biquadFilter = new BiquadFilter(AppSettings.getSmoothing(), 3);
            for (int i = 0; i < AppSettings.getSmoothingCount(); i++) {
                biquadFilter.update(point);
            }
        }

        // Filter the point
        point = biquadFilter.update(point);

        // Check distance, and only add if moved far enough
        if (s > 0) {
            Vector3f lastPoint = points.get(s - 1);

            Vector3f temp = new Vector3f();
            temp.sub(point, lastPoint);

            if (temp.length() < lineWidth / 10) {
                return;
            }
        }

        // Add the point
        points.add(point);

        // Cleanup vertices that are redundant
        if (s > 3) {
            float angle = calculateAngle(s - 2);
            // Remove points that have very low angle change
            if (angle < 0.05) {
                points.remove(s - 2);
            } else {
                subdivideSection(s - 3, 0.3f, 0);
            }
        }

        // Cleanup beginning, remove points that are close to each other
        // This makes the end seem straing
//        if(s < 5 && s > 2){
//            float dist = calculateDistance(0, s-1);
//            if(dist < 0.005) {
//                for (int i = 0; i < s - 2; i++) {
//                    if (calculateDistance(i, i + 1) < 0.005) {
//                        points.remove(i + 1);
//                        startCap.clear();
//                    }
//                }
//            }
//        }

        calculateTotalLength();
    }

    /**
     * Update called when there is new data from Firebase
     *
     * @param data Stroke data to copy from
     */
    public void updateStrokeData(Stroke data) {
        this.points = data.points;
        this.lineWidth = data.lineWidth;

        calculateTotalLength();
    }

    public boolean update() {
        boolean renderNeedsUpdate = false;
        if (!localLine) {
            float before = animatedLength;
            animatedLength = animationFilter.update(totalLength);
            if (Math.abs(animatedLength - before) > 0.001) {
                renderNeedsUpdate = true;
            }
        }
        return renderNeedsUpdate;
    }

    public void finishStroke() {
        finished = true;

        // Calculate total distance traveled
        float dist = 0;

        Vector3f d = new Vector3f();
        for (int i = 0; i < points.size() - 1; i++) {
            d.sub(points.get(i), points.get(i + 1));
            dist += d.length();
        }

        // If line is very short, overwrite it
        if (dist < 0.01) {
            if (points.size() > 2) {
                Vector3f p1 = points.get(0);
                Vector3f p2 = points.get(points.size() - 1);

                points.clear();
                points.add(p1);
                points.add(p2);
            } else if (points.size() == 1) {
                Vector3f v = new Vector3f(points.get(0));
                v.y += 0.0005;
                points.add(v);
            }
        }
    }

    private float calculateDistance(int index1, int index2) {
        Vector3f p1 = points.get(index1);
        Vector3f p2 = points.get(index2);
        Vector3f n1 = new Vector3f();
        n1.sub(p2, p1);
        return n1.length();
    }

    private float calculateAngle(int index) {
        Vector3f p1 = points.get(index - 1);
        Vector3f p2 = points.get(index);
        Vector3f p3 = points.get(index + 1);

        Vector3f n1 = new Vector3f();
        n1.sub(p2, p1);

        Vector3f n2 = new Vector3f();
        n2.sub(p3, p2);

        return n1.angle(n2);
    }

    public void calculateTotalLength() {
        totalLength = 0;
        for (int i = 1; i < points.size(); i++) {
            Vector3f dist = new Vector3f(points.get(i));
            dist.sub(points.get(i - 1));
            totalLength += dist.length();
        }

    }

    private void subdivideSection(int s, float maxAngle, int iteration) {
        if (iteration == 6) {
            return;
        }

        Vector3f p1 = points.get(s);
        Vector3f p2 = points.get(s + 1);
        Vector3f p3 = points.get(s + 2);

        Vector3f n1 = new Vector3f();
        n1.sub(p2, p1);

        Vector3f n2 = new Vector3f();
        n2.sub(p3, p2);

        float angle = n1.angle(n2);

        // If angle is too big, add points
        if (angle > maxAngle) {
            n1.scale(0.5f);
            n2.scale(0.5f);
            n1.add(p1);
            n2.add(p2);

            points.add(s + 1, n1);
            points.add(s + 3, n2);

            subdivideSection(s + 2, maxAngle, iteration + 1);
            subdivideSection(s, maxAngle, iteration + 1);
        }
    }


    public void offsetToPose(Pose pose) {
        for (int i = 0; i < points.size(); i++) {
            Vector3f p = LineUtils.TransformPointToPose(points.get(i), pose);
            points.set(i, p);
        }
    }

    public void offsetFromPose(Pose pose) {
        for (int i = 0; i < points.size(); i++) {
            Vector3f p = LineUtils.TransformPointFromPose(points.get(i), pose);
            points.set(i, p);
        }
    }

    public Vector3f get(int index) {
        return points.get(index);
    }

    public int size() {
        return points.size();
    }

    @SuppressWarnings("unused")
    public List<Vector3f> getPoints() {
        return points;
    }

    @SuppressWarnings("unused")
    public float getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    public void setFirebaseValue(StrokeUpdate strokeUpdate, StrokeUpdate previousStrokeUpdate,
                                 DatabaseReference.CompletionListener completionListener) {
//        Stroke copy = new Stroke();
//        copy.lineWidth = strokeUpdate.stroke.lineWidth;
//        int numPointsToSend = strokeUpdate.stroke.points.size();
//        copy.points = strokeUpdate.stroke.points.subList(0, strokeUpdate.stroke.points.size());
//
//        copy.creator = strokeUpdate.stroke.creator;

        // if points havent been set, or if creator or lineWidth has changed, force a full update
        if (previousStrokeUpdate == null
                || previousStrokeUpdate.stroke.points.size() == 0
                || !previousStrokeUpdate.stroke.creator.equals(strokeUpdate.stroke.creator)
                || previousStrokeUpdate.stroke.lineWidth != strokeUpdate.stroke.lineWidth) {
            firebaseReference.setValue(strokeUpdate.stroke, completionListener);
        } else {
            // If only points have updated, calculate the changes since last update, and only upload those points
            Map<String, Object> pointUpdate = new HashMap<>();
            int i = 0;
            for (Vector3f p : strokeUpdate.stroke.points) {
                // If point exceeds previous strokes length, add it
                if (previousStrokeUpdate.stroke.points.size() <= i) {
                    pointUpdate.put(String.valueOf(i), p);
                } else {
                    // Check if point equals previous point
                    Vector3f prev = previousStrokeUpdate.stroke.points.get(i);
                    if (!p.equals(prev)) {
                        pointUpdate.put(String.valueOf(i), p);
                    }
                }
                i++;
            }

            firebaseReference.child("points").updateChildren(pointUpdate, completionListener);
        }
    }

    public void removeFirebaseValue() {
        firebaseReference.removeValue();
    }

    public void setFirebaseReference(DatabaseReference firebaseReference) {
        this.firebaseReference = firebaseReference;
    }

    public boolean hasFirebaseReference() {
        return firebaseReference != null;
    }

    @Exclude
    public String getFirebaseKey() {
        return firebaseReference == null ? null : firebaseReference.getKey();
    }

    public Stroke copy() {
        Stroke copy = new Stroke();
        copy.creator = creator;
        copy.lineWidth = lineWidth;
        copy.firebaseReference = firebaseReference;
        copy.points = new ArrayList<>(points);
        return copy;
    }
}

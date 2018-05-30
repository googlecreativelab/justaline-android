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

package com.arexperiments.justaline.analytics;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.arexperiments.justaline.App;
import com.arexperiments.justaline.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;


/**
 * Firebase Analytics wrapper class (hence "Fa").
 */
public class Fa {

    private static final String TAG = "FirebaseAnalytics";

    private static Fa instance = null;

    private FirebaseAnalytics fa;

    public static Fa get() {
        if (instance == null) {
            instance = getSync();
        }
        return instance;
    }

    private static synchronized Fa getSync() {
        if (instance == null) {
            instance = new Fa();
        }
        return instance;
    }

    @SuppressWarnings("MissingPermission")
    private Fa() {
        if (!BuildConfig.DEBUG) {

            Log.d(TAG, "FirebaseAnalytics and FirebaseCrash active");

            // cache reference to FirebaseAnalytics
            fa = FirebaseAnalytics.getInstance(App.get());

        } else {

            Log.v(TAG, "FirebaseAnalytics and FirebaseCrash inactive");
        }
    }

    /**
     * Send an event along with an optional Bundle representing custom parameters
     */
    public void send(@NonNull String event, @Nullable Bundle customParams) {
        Log.v(TAG, event + " " + (customParams != null ? customParams : ""));
        if (fa == null) return;

        fa.logEvent(event, customParams);
    }

    /**
     * Send an event without any params
     */
    public void send(@NonNull String event) {
        Log.v(TAG, event);
        if (fa == null) return;
        send(event, null);
    }

    /**
     * Send an event along with one parameter whose value is a String (convenience method)
     */
    public void send(
            @NonNull String event,
            @NonNull String customParam1Name,
            @NonNull String customParam1Value) {
        Log.v(TAG, event + " " + customParam1Name + ": " + customParam1Value);
        if (fa == null) return;
        Bundle b = new Bundle();
        b.putString(customParam1Name, customParam1Value);
        send(event, b);
    }

    /**
     * Send an event along with one parameter whose value is an int (convenience method)
     */
    public void send(
            @NonNull String event,
            @NonNull String customParam1Name,
            int customParam1Value) {
        Bundle b = new Bundle();
        b.putInt(customParam1Name, customParam1Value);
        send(event, b);
    }

    /**
     * Send an event along with two parameters (whose values are Strings)
     * (convenience method)
     */
    public void send(
            @NonNull String event,
            @NonNull String customParam1Name,
            @NonNull String customParam1Value,
            @NonNull String customParam2Name,
            @NonNull String customParam2Value) {
        Bundle b = new Bundle();
        b.putString(customParam1Name, customParam1Value);
        b.putString(customParam2Name, customParam2Value);
        send(event, b);
    }

    /**
     * Send a caught exception to Firebase
     */
    public void exception(Throwable throwable) {
        Log.e(TAG, "Exception: ", throwable);
        if (BuildConfig.DEBUG) {
            return;
        }
        FirebaseCrash.report(throwable);
    }

    /**
     * Send a caught exception to Firebase with an additional logged message
     */
    public void exception(Throwable throwable, String logMessage) {
        Log.e(TAG, "Exception: " + throwable + " | " + logMessage, throwable);
        if (BuildConfig.DEBUG) {
            return;
        }

        FirebaseCrash.logcat(Log.WARN, TAG, logMessage);
        FirebaseCrash.report(throwable);
    }

    /**
     * Sets the value of a Firebase Analytics custom user property. That property must already be
     * set
     * up in the console. Note that the value that is set is 'persistent' across user sessions.
     */
    public void setUserProperty(String property, String value) {
        Log.d(TAG, property + " = " + value);
        if (fa == null) {
            return;
        }
        fa.setUserProperty(property, value);
    }
}
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
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

/**
 * Created by Kat on 2/15/18.
 */

public class SessionHelper {

    private static final String PREF_FILE = "Session";

    private static final String PREF_LAST_SESSION_END = "last_session_end";

    private static final long MAX_TIME_BETWEEN_SESSIONS = TimeUnit.MINUTES.toMillis(3);

    private static final long MAX_TIME_BETWEEN_PARTNER_SESSIONS = TimeUnit.MINUTES.toMillis(1);


    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void setSessionEnd(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        sharedPreferences.edit().putLong(PREF_LAST_SESSION_END, System.currentTimeMillis())
                .commit();
    }

    public static boolean shouldContinueSession(Context context) {
        return getTimeSinceLastSession(context) <= MAX_TIME_BETWEEN_SESSIONS;
    }

    public static boolean shouldContinuePairedSession(Context context) {
        if (BuildConfig.GLOBAL) return true;

        return getTimeSinceLastSession(context) <= MAX_TIME_BETWEEN_PARTNER_SESSIONS;
    }

    private static long getTimeSinceLastSession(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        long prevTime = sharedPreferences.getLong(PREF_LAST_SESSION_END, 0);
        long currentTime = System.currentTimeMillis();
        return currentTime - prevTime;
    }
}

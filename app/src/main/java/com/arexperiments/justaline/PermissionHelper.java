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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.arexperiments.justaline.analytics.AnalyticsEvents;
import com.arexperiments.justaline.analytics.Fa;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to ask camera permission.
 */
public class PermissionHelper {

    private static final String PREF_FILE = "Permissions";

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 0;

    public static final int REQUEST_CODE_STORAGE_PERMISSIONS = 1;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    private static final String WRITE_EXTERNAL_STORAGE_PERMISSION
            = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private static final String RECORD_AUDIO_PERMISSION
            = Manifest.permission.RECORD_AUDIO;

    private static final String PREF_NUM_TIMES_ASKED_REQUIRED_PERMISSIONS
            = "num_times_asked_for_audio_and_camera_permissions";

    private static final String PREF_NUM_TIMES_ASKED_STORAGE_PERMISSIONS
            = "num_times_asked_for_storage_permission";

    private static final String[] REQUIRED_PERMISSIONS = new String[]
            {CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION};

    public static boolean hasRequiredPermissions(Context context) {
        boolean hasNeededPermissions = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    == PackageManager.PERMISSION_DENIED) {
                hasNeededPermissions = false;
            }
        }
        return hasNeededPermissions;
    }

    public static boolean hasStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check to see we have the necessary permissions for this app, and ask for them if we don't.
     * If necessary, show rationale before asking for permissions.
     * For this check, we are only checking audio and camera. We will check storage if user decides
     * to save their recording later.
     *
     * @param activity       the activity calling for required permissions (PermissionsActivity)
     * @param rationaleShown are we currently showing them rationale
     * @return A rationale dialog if one is shown or null if we are requesting the permissions
     */
    public static AlertDialog requestRequiredPermissions(Activity activity,
                                                         boolean rationaleShown) {

        if (shouldSendToSystemPrefsForRequiredPermissions(activity)) {
            return showRequiredPermissionsSystemDialog(activity);
        } else if (!rationaleShown && shouldShowRationaleForRequiredPermissions(activity)) {
            // show rationale
            return showRequiredPermissionRationale(activity);
        } else {

            List<String> neededPermissions = new ArrayList<>();
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission)
                        == PackageManager.PERMISSION_DENIED) {
                    neededPermissions.add(permission);
                }
            }

            // Request the permissions
            ActivityCompat.requestPermissions(activity,
                    neededPermissions.toArray(new String[0]), REQUEST_CODE_REQUIRED_PERMISSIONS);
            // Add another time to the number of times we have asked for permission
            SharedPreferences preferences = getSharedPreferences(activity);
            int numTimes = preferences.getInt(PREF_NUM_TIMES_ASKED_REQUIRED_PERMISSIONS, 0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(PREF_NUM_TIMES_ASKED_REQUIRED_PERMISSIONS, ++numTimes);
            editor.apply();

            return null;
        }
    }

    /**
     * Show dialog with rationale for required permissions
     *
     * @param activity calling activity which the AlertDialog is shown on (PermissionsActivity)
     * @return the dialog that is shown
     */
    private static AlertDialog showRequiredPermissionRationale(final Activity activity) {
        final AlertDialog
                alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(activity.getString(R.string.title_activity_permissions));
        alertDialog.setMessage(activity.getString(R.string.initial_permissions_required));

        alertDialog.setButton(android.support.v7.app.AlertDialog.BUTTON_NEUTRAL,
                activity.getString(R.string.ask_me_again),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        requestRequiredPermissions(activity, true);
                    }
                });

        alertDialog.setCancelable(false);
        alertDialog.show();

        return alertDialog;
    }

    /**
     * Show a dialog with button to send user to System settings to grant us permissions
     *
     * @param activity the activity to show the dialog on (PermissionsActivity)
     * @return The Rationale dialog
     */
    private static AlertDialog showRequiredPermissionsSystemDialog(final Activity activity) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(activity.getString(R.string.title_activity_permissions));
        alertDialog.setMessage(activity.getString(R.string.initial_permissions_required));

        alertDialog.setButton(android.support.v7.app.AlertDialog.BUTTON_NEUTRAL,
                activity.getString(R.string.open_system_permissions),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        openSystemPermissions(activity);
                    }
                });

        alertDialog.setCancelable(false);
        alertDialog.show();

        return alertDialog;
    }


    /**
     * Check to see we have the storage permission and, if not, request it
     */
    public static void requestStoragePermission(Activity activity, boolean rationaleShown) {

        if (!rationaleShown && activity
                .shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE_PERMISSION)) {
            // we've asked them before, show reason
            showStoragePermissionRationale(activity);
        } else {
            // Request the permissions
            ActivityCompat
                    .requestPermissions(activity, new String[]{WRITE_EXTERNAL_STORAGE_PERMISSION},
                            REQUEST_CODE_STORAGE_PERMISSIONS);

            // Add another time to the number of times we have asked for permission
            SharedPreferences preferences = getSharedPreferences(activity);
            int numTimes = preferences.getInt(PREF_NUM_TIMES_ASKED_STORAGE_PERMISSIONS, 0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(PREF_NUM_TIMES_ASKED_STORAGE_PERMISSIONS, ++numTimes);
            editor.apply();
        }

    }

    /**
     * Show rationale for storage permissions
     *
     * @param activity the activity to show the rationale on (PlaybackActivity)
     */
    private static void showStoragePermissionRationale(final Activity activity) {
        final AlertDialog
                alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setMessage(activity.getString(R.string.storage_permission_rationale));
        alertDialog.setButton(android.support.v7.app.AlertDialog.BUTTON_POSITIVE,
                activity.getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        requestStoragePermission(activity, true);
                    }
                });

        alertDialog.show();
    }

    /**
     * Send intent to open System settings for this activity so user can grant permissions
     *
     * @param activity reference to activity that will call this method (PermissionsActivity)
     */
    private static void openSystemPermissions(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    /**
     * @param activity reference to activity that we are checking for rationale needs
     * @return true if we should show a rationale before asking for permissions
     */
    private static boolean shouldShowRationaleForRequiredPermissions(Activity activity) {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (activity.shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine if user has already been asked these permissions and denied us the ability to
     * ask again. If true, send them to system to grant required permissions.
     *
     * @param activity reference to activity we are checking
     * @return true if we cannot ask for the necessary permissions
     */
    private static boolean shouldSendToSystemPrefsForRequiredPermissions(Activity activity) {
        int numTimesAsked = getSharedPreferences(activity)
                .getInt(PREF_NUM_TIMES_ASKED_REQUIRED_PERMISSIONS, 0);
        if (numTimesAsked > 1) {
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(activity, permission)
                        == PackageManager.PERMISSION_DENIED &&
                        !activity.shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void sendPermissionsAnalytics(Fa analytics,
                                                String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            switch (permissions[i]) {
                case CAMERA_PERMISSION:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        analytics.send(AnalyticsEvents.EVENT_CAMERA_PERMISSION_GRANTED, null);
                    } else {
                        analytics.send(AnalyticsEvents.EVENT_CAMERA_PERMISSION_DENIED, null);
                    }
                    break;
                case RECORD_AUDIO_PERMISSION:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        analytics.send(AnalyticsEvents.EVENT_MICROPHONE_PERMISSION_GRANTED,
                                null);
                    } else {
                        analytics.send(AnalyticsEvents.EVENT_MICROPHONE_PERMISSION_DENIED, null);
                    }
                    break;
                case WRITE_EXTERNAL_STORAGE_PERMISSION:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        analytics.send(AnalyticsEvents.EVENT_STORAGE_PERMISSION_GRANTED, null);
                    } else {
                        analytics.send(AnalyticsEvents.EVENT_STORAGE_PERMISSION_DENIED, null);
                    }
                    break;
            }
        }
    }
}

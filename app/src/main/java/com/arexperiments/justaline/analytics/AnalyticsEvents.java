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

/**
 * Created by Kat on 2/8/18.
 */

public class AnalyticsEvents {

    public static final String VALUE_TRUE = "true";

    // Permissions
    public static final String EVENT_CAMERA_PERMISSION_GRANTED = "camera_permission_granted";
    public static final String EVENT_CAMERA_PERMISSION_DENIED = "camera_permission_denied";
    public static final String EVENT_MICROPHONE_PERMISSION_GRANTED = "microphone_permission_granted";
    public static final String EVENT_MICROPHONE_PERMISSION_DENIED = "microphone_permission_denied";
    public static final String EVENT_STORAGE_PERMISSION_GRANTED = "storage_permission_granted";
    public static final String EVENT_STORAGE_PERMISSION_DENIED = "storage_permission_denied";

    // Camera view
    public static final String EVENT_RECORD = "record";
    public static final String PARAM_RECORD_METHOD = "record_method";
    public static final String VALUE_RECORD_METHOD_TAP = "tap";
    public static final String VALUE_RECORD_METHOD_ACCESSIBLE_TAP = "accessible_tap";
    public static final String VALUE_RECORD_METHOD_HOLD = "hold";

    public static final String USER_PROPERTY_HAS_DRAWN = "has_drawn";
    public static final String USER_PROPERTY_TRACKING_ESTABLISHED = "tracking_has_established";
    public static final String USER_PROPERTY_TAPPED_UNDO = "has_tapped_undo";
    public static final String USER_PROPERTY_TAPPED_CLEAR = "has_tapped_clear";
    public static final String EVENT_TAPPED_SHARE_APP = "tapped_share_app";

    // Playback
    public static final String EVENT_TAPPED_SAVE = "tapped_save";
    public static final String EVENT_TAPPED_SHARE_RECORDING = "tapped_share_recording";

    // Pairing
    public static final String EVENT_TAPPED_START_PAIR = "tapped_start_pair";
    public static final String EVENT_PAIR_ERROR_DISCOVERY_TIMEOUT = "pair_error_discovery_timeout";
    public static final String EVENT_TAPPED_READY_TO_SET_ANCHOR = "tapped_ready_to_set_anchor";
    public static final String EVENT_PAIR_ERROR_SYNC = "pair_error_sync";
    public static final String PARAM_PAIR_ERROR_SYNC_REASON = "reason";
    public static final String VALUE_PAIR_ERROR_SYNC_REASON_TIMEOUT = "Pairing Timeout";
    public static final String VALUE_PAIR_ERROR_SYNC_REASON_NOT_TRACKING = "Not Tracking";
    public static final String EVENT_PAIR_SUCCESS = "pair_success";
    public static final String USER_PROPERTY_HAS_TAPPED_PAIR = "has_tapped_pair";
    public static final String EVENT_TAPPED_EXIT_PAIR_FLOW = "tapped_exit_pair_flow";
    public static final String EVENT_TAPPED_DISCONNECT_PAIRED_SESSION = "tapped_disconnect_paired_session";

}

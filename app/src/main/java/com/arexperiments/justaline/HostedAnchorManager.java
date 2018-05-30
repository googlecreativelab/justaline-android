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

import android.util.Log;
import android.util.Pair;

import com.google.ar.core.Anchor;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotTrackingException;

import java.util.Collection;
/**
 * A helper class to handle all the Sharing and Persistence logic.
 */
/*package*/ class HostedAnchorManager {

    private static final String TAG = "HostedAnchorManager";

    /**
     * Listener for HostAnchor Results.
     */
    public interface AnchorHostedListener {

        /**
         * This method is invoked when the results of a HostAnchor call are available.
         *
         * @param anchor   The hosted anchor.
         * @param anchorId The anchor ID of the hosted anchor.
         * @param state    The final status of the call.
         */
        void onAnchorHosted(Anchor anchor, String anchorId, Anchor.CloudAnchorState state);
    }

    /**
     * Listener for GetHostedAnchor Results.
     */
    public interface AnchorResolvedListener {

        /**
         * This method is invoked when the results of a GetHostedAnchor call are available.
         *
         * @param anchor The resolved anchor.
         * @param state  The final status of the call.
         */
        void onAnchorResolved(Anchor anchor, Anchor.CloudAnchorState state, boolean notTracking);
    }

    private Session session = null;

    private Pair<Anchor, AnchorHostedListener> pendingHost;

    private Pair<Anchor, AnchorResolvedListener> pendingResolve;

    /**
     * This method is used to set the session, since it might not be available when this object is
     * created.
     *
     * @param session The ARCore Session to be used.
     */
    public synchronized void setSession(Session session) {
        this.session = session;
    }

    /**
     * This method hosts an anchor using the ARCore S&P APIs.
     *
     * @param anchor   The anchor to be hosted.
     * @param listener The listener to be invoked when the results become available.
     */
    public synchronized void hostAnchor(Anchor anchor, AnchorHostedListener listener) {
        checkSessionNotNull();
        Anchor cloudAnchor = session.hostCloudAnchor(anchor);
        if (listener != null) {
            pendingHost = new Pair<>(cloudAnchor, listener);
        }
    }

    /**
     * This method obtains a hosted anchor using the ARCore S&P APIs, and then makes the results
     * available via the response listener.
     *
     * @param anchorId The anchor ID of the anchor
     * @param listener The listener which should be invoked when the results become available.
     */
    public synchronized boolean resolveHostedAnchor(String anchorId,
                                                    AnchorResolvedListener listener) {
        checkSessionNotNull();

        try {
            Anchor tempAnchor = session.resolveCloudAnchor(anchorId);
            Anchor.CloudAnchorState state = tempAnchor.getCloudAnchorState();
            Log.d(TAG, "resolveHostedAnchor: " + state);
            if (isReturnableStatus(state)) {
                Log.d(TAG, "resolveHostedAnchor: returnable status");
                if (listener != null) {
                    listener.onAnchorResolved(tempAnchor, state, false);
                }
            } else {
                pendingResolve = new Pair<>(tempAnchor, listener);
            }

            return true;
        } catch (NotTrackingException e) {
            Log.w(TAG, "resolveHostedAnchor: error ", e);
            if (listener != null) {
                listener.onAnchorResolved(null, null, true);
            }
            return false;
        }
    }

    public synchronized void cancelAnchorProcessing() {
        Log.d(TAG, "cancelAnchorProcessing: ");
        pendingHost = null;
        pendingResolve = null;
    }

    /**
     * Should be called with the updated anchors available after a Session.Update() call.
     *
     * @param updatedAnchors The anchors returned from frame.getUpdatedAnchors().
     */
    public synchronized void onUpdate(final Collection<Anchor> updatedAnchors) {
        checkSessionNotNull();

        if (pendingHost != null) {
            for (Anchor anchor : updatedAnchors) {
                if (pendingHost.first.equals(anchor)) {
                    Anchor.CloudAnchorState state = anchor.getCloudAnchorState();
                    Log.d(TAG, "onUpdate: " + state);
                    if (isReturnableStatus(state)) {
                        AnchorHostedListener listener = pendingHost.second;
                        pendingHost = null;
                        final String anchorId = anchor.getCloudAnchorId();
                        listener.onAnchorHosted(anchor, anchorId, state);
                    }
                }
            }
        }

        if (pendingResolve != null) {
            Log.d(TAG, "onUpdate: resolveHostedAnchor: pending resolve");

            Anchor resolvingAnchor = pendingResolve.first;
            AnchorResolvedListener listener = pendingResolve.second;

            try {
                Anchor.CloudAnchorState state = resolvingAnchor.getCloudAnchorState();
                Log.d(TAG, "resolveHostedAnchor: " + state);
                if (isReturnableStatus(state)) {
                    pendingResolve = null;
                    if (listener != null) {
                        listener.onAnchorResolved(resolvingAnchor, state, false);
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "resolveHostedAnchor: ", e);
            }

        }

    }

    private void checkSessionNotNull() {
        if (session == null) {
            throw new IllegalStateException("The session cannot be null");
        }
    }

    private static boolean isReturnableStatus(Anchor.CloudAnchorState status) {
        switch (status) {
            case SUCCESS:
            case ERROR_INTERNAL:
            case ERROR_NOT_AUTHORIZED:
            case ERROR_SERVICE_UNAVAILABLE:
            case ERROR_RESOURCE_EXHAUSTED:
            case ERROR_HOSTING_DATASET_PROCESSING_FAILED:
            case ERROR_CLOUD_ID_NOT_FOUND:
            case ERROR_RESOLVING_LOCALIZATION_NO_MATCH:
            case ERROR_RESOLVING_SDK_VERSION_TOO_NEW:
            case ERROR_RESOLVING_SDK_VERSION_TOO_OLD:
                return true;
            case NONE:
            case TASK_IN_PROGRESS:
            default:
                return false;
        }
    }
}


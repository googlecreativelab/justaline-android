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

package com.arexperiments.justaline.view;

import android.animation.Animator;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.arexperiments.justaline.BuildConfig;
import com.arexperiments.justaline.PairSessionManager;
import com.arexperiments.justaline.R;
import com.arexperiments.justaline.analytics.AnalyticsEvents;
import com.arexperiments.justaline.analytics.Fa;
import com.google.ar.core.Anchor;

import java.util.concurrent.TimeUnit;

import static com.airbnb.lottie.LottieDrawable.INFINITE;
import static com.airbnb.lottie.LottieDrawable.RESTART;

/**
 * Created by Kat on 3/29/18.
 */

public class PairView extends ConstraintLayout
        implements PairSessionManager.PairingStateChangeListener,
        TrackingIndicator.DisplayListener {

    private static final long DELAY_STATE_TRANSITION = TimeUnit.SECONDS.toMillis(2);

    public enum PairState {
        LOOKING, DISCOVERY_TIMEOUT,
        HOST_CONNECTED, PARTNER_CONNECTED,
        HOST_SET_ANCHOR, PARTNER_SET_ANCHOR, HOST_READY_AND_WAITING, PARTNER_READY_AND_WAITING,
        HOST_CONNECTING, PARTNER_CONNECTING, GLOBAL_CONNECTING,
        HOST_RESOLVE_ERROR, PARTNER_RESOLVE_ERROR,
        GLOBAL_NO_ANCHOR, GLOBAL_RESOLVE_ERROR,
        SYNCED, FINISHED,
        CONNECTION_LOST, OFFLINE, UNKNOWN_ERROR
    }

    private View closeButton;

    private TextView primaryMessageTextView;

    private LottieAnimationView animationImageView;

    private ImageView imageView;

    private View readyButton;

    private Button tryAgainButton;

    private Button okButton;

    private View fullBackground;

    private PairState state;

    private Listener listener;

    private Handler handler;

    private Runnable delayStateRunnable;

    private TextView secondaryMessageTextView;

    private ConstraintLayout messageContainer;

    private ProgressBar progressBar;

    private CountDownTimer progressCountDownTimer;

    private boolean showingProgress = false;

    private static final long COUNTDOWN_DURATION = TimeUnit.SECONDS.toMillis(30);

    private static final long COUNTDOWN_TICK = 100;

    private static final float COUNTDOWN_MAX_PROGRESS = .8f;

    public PairView(Context context) {
        super(context);
        init();
    }

    public PairView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PairView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_pair, this);

        closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (state == PairState.SYNCED) {
                    hide();
                    if (listener != null) {
                        listener.onPairViewClosed();
                    }
                    return;
                }

                if (listener != null) {
                    listener.onPairCanceled();
                }
                Fa.get().send(AnalyticsEvents.EVENT_TAPPED_EXIT_PAIR_FLOW);
            }
        });

        messageContainer = findViewById(R.id.pairview_message_container);
        primaryMessageTextView = findViewById(R.id.message);
        secondaryMessageTextView = findViewById(R.id.message_secondary);
        imageView = findViewById(R.id.image);

        okButton = findViewById(R.id.ok_button);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (state) {
                    case CONNECTION_LOST:
                        if (listener != null)
                            listener.onPairCanceled();
                        break;
                }
            }
        });

        tryAgainButton = findViewById(R.id.try_again);
        tryAgainButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (state) {
                    case DISCOVERY_TIMEOUT:
                        if (listener != null) {
                            listener.attemptPartnerDiscovery();
                        }
                        break;
                    case HOST_RESOLVE_ERROR:
                        setState(PairState.HOST_SET_ANCHOR);
                        break;
                    case PARTNER_RESOLVE_ERROR:
                        setState(PairState.PARTNER_SET_ANCHOR);
                        break;
                    case GLOBAL_RESOLVE_ERROR:
                        setState(PairState.PARTNER_CONNECTING);
                        if (listener != null) {
                            listener.onReadyResolveAnchor();
                        }
                        break;
                }
                tryAgainButton.setVisibility(View.GONE);
            }
        });

        readyButton = findViewById(R.id.anchor_ready_button);
        readyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (state) {
                    case HOST_SET_ANCHOR:
                    case PARTNER_SET_ANCHOR:
                        if (listener != null) {
                            listener.onReadyToSetAnchor();
                        }
                        break;
                }
                readyButton.setVisibility(View.GONE);

            }
        });

        handler = new Handler(Looper.getMainLooper());
        delayStateRunnable = new Runnable() {
            @Override
            public void run() {
                PairState nextState = null;
                switch (state) {
                    case HOST_CONNECTED:
                        nextState = PairState.HOST_SET_ANCHOR;
                        break;
                    case PARTNER_CONNECTED:
                        nextState = PairState.PARTNER_SET_ANCHOR;
                        break;
                    case SYNCED:
                        nextState = PairState.FINISHED;
                        break;
                }
                if (nextState != null) {
                    setState(nextState);
                }
            }
        };

        fullBackground = findViewById(R.id.full_background);

        progressBar = findViewById(R.id.pair_progress);
        progressCountDownTimer = new CountDownTimer(COUNTDOWN_DURATION, COUNTDOWN_TICK) {
            @Override
            public void onTick(long l) {
                progressBar.setProgress((int) ((COUNTDOWN_DURATION - l) * COUNTDOWN_MAX_PROGRESS));
            }

            @Override
            public void onFinish() {
                progressBar.setProgress((int) (COUNTDOWN_DURATION * COUNTDOWN_MAX_PROGRESS));
            }
        };
    }

    @Override
    public void onStateChange(final PairState state,
                              final Anchor.CloudAnchorState cloudAnchorState,
                              final boolean notTracking) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (notTracking) {
                    setState(state, "NotTrackingException");
                } else {
                    setState(state, cloudAnchorState == null ? null : cloudAnchorState.toString());
                }
            }
        });
    }

    @Override
    public void onStateChange(final PairState state) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                setState(state, null);
            }
        });

    }

    @Override
    public void onStateChange(final PairState state, final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                setState(state, message);
            }
        });
    }

    private void setState(PairState state) {
        setState(state, null);
    }

    class Animation {

        boolean looping = false;

        int width;

        int height;

        Animator.AnimatorListener listener = null;

        public Animation(boolean looping, int widthDimenRes, int heightDimenRes) {
            this.looping = looping;
            this.width = getResources().getDimensionPixelSize(widthDimenRes);
            this.height = getResources().getDimensionPixelSize(heightDimenRes);
        }
    }

    private void setState(PairState state, String error) {
        this.state = state;

        int messageRes;
        int contentDescriptionRes = -1;
        int secondaryMessageRes = -1;
        int imageRes = -1;

        boolean showOk = false;
        boolean showTryAgain = false;
        boolean delayedStateTransition = false;
        Animation animation = null;
        boolean showReadyButton = false;
        boolean showProgress = false;
        boolean showProgressComplete = false;

        switch (state) {
            default:
            case LOOKING:
                messageRes = R.string.pair_looking_for_partner;
                imageRes = R.raw.looking_for_partner;
                animation = new Animation(true, R.dimen.pair_animation_partner_discovery_width,
                        R.dimen.pair_animation_partner_discovery_height);
                break;
            case DISCOVERY_TIMEOUT:
                messageRes = R.string.pair_discovery_timeout;
                imageRes = R.drawable.jal_ui_error_icon_partner;
                showTryAgain = true;
                break;
            case HOST_CONNECTED:
            case PARTNER_CONNECTED:
                messageRes = R.string.pair_connected;
                imageRes = R.raw.partner_found;
                animation = new Animation(false, R.dimen.pair_animation_partner_discovery_width,
                        R.dimen.pair_animation_partner_discovery_height);
                delayedStateTransition = true;
                break;
            case HOST_SET_ANCHOR:
            case PARTNER_SET_ANCHOR:
                if (listener != null) listener.setAnchorResolvingMode();

                messageRes = R.string.pair_look_at_same_thing;
                contentDescriptionRes = R.string.content_description_pair_look_at_same_thing;
                imageRes = R.drawable.jal_pair_phones;
                showReadyButton = true;
                break;
            case HOST_READY_AND_WAITING:
            case PARTNER_READY_AND_WAITING:
                messageRes = R.string.pair_look_at_same_thing;
                contentDescriptionRes = R.string.content_description_pair_look_at_same_thing;
                imageRes = R.drawable.jal_pair_phones;
                secondaryMessageRes = R.string.pair_waiting_for_partner;
                break;
            case HOST_CONNECTING:
            case PARTNER_CONNECTING:
                messageRes = R.string.pair_connect_phones;
                imageRes = R.raw.stay_put;
                animation = new Animation(true, R.dimen.pair_animation_stay_put_width,
                        R.dimen.pair_animation_stay_put_height);
                animation.listener = new Animator.AnimatorListener() {
                    int i = 0;

                    @Override
                    public void onAnimationStart(Animator animator) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                        if (i == 0 && animationImageView != null) {
                            animationImageView.setMinFrame(250);
                            i++;
                        }
                    }
                };
                showProgress = true;
                break;
            case GLOBAL_CONNECTING:
                messageRes = R.string.pair_global_connecting;
                break;
            case HOST_RESOLVE_ERROR:
            case PARTNER_RESOLVE_ERROR:
                if (listener != null) listener.setPairErrorMode();
                messageRes = R.string.pair_anchor_error;
                secondaryMessageRes = R.string.pair_anchor_error_secondary;
                imageRes = R.drawable.jal_ui_error_icon_sync;
                showTryAgain = true;
                break;
            case GLOBAL_NO_ANCHOR:
                if (listener != null) listener.setPairErrorMode();
                messageRes = R.string.pair_global_no_anchor;
                imageRes = R.drawable.jal_ui_error_icon_sync;
                break;
            case GLOBAL_RESOLVE_ERROR:
                if (listener != null) listener.setPairErrorMode();
                messageRes = R.string.pair_global_localization_error;
                imageRes = R.drawable.jal_ui_error_icon_sync;
                showTryAgain = true;
                break;
            case SYNCED:
                if (listener != null) listener.setPairSuccessMode();
                messageRes = R.string.pair_synced;
                imageRes = R.drawable.jal_ui_check;
                delayedStateTransition = true;
                break;
            case FINISHED:
                hide();
                if (listener != null) listener.onPairViewClosed();
                return;
            case CONNECTION_LOST:
                messageRes = R.string.pair_lost_connection;
                imageRes = R.drawable.jal_ui_error_icon_partner;
                showOk = true;
                break;
            case OFFLINE:
                messageRes = R.string.pair_no_data_connection_title;
                secondaryMessageRes = R.string.pair_no_data_connection_body;
                imageRes = R.drawable.jal_ui_error_icon_partner;
                break;
            case UNKNOWN_ERROR:
                messageRes = R.string.pair_unknown_error;
                imageRes = R.drawable.jal_ui_error_icon_unknown;
                break;
        }

        fullBackground.setVisibility(View.VISIBLE);

        String message = getContext().getString(messageRes);
        if (error != null && BuildConfig.DEBUG) {
            message += "\n" + error;
        }
        primaryMessageTextView.setText(message);
        primaryMessageTextView.setBackground(null);

        String contentDescription;
        if (contentDescriptionRes >= 0)
            contentDescription = getContext().getString(contentDescriptionRes);
        else contentDescription = message;
        primaryMessageTextView.setContentDescription(contentDescription);

        announceForAccessibility(contentDescription);

        if (secondaryMessageRes >= 0) {
            secondaryMessageTextView.setText(secondaryMessageRes);
            secondaryMessageTextView.setVisibility(View.VISIBLE);
        } else {
            secondaryMessageTextView.setText(null);
            secondaryMessageTextView.setVisibility(View.GONE);
        }

        removeLottieAnimationView();

        if (animation != null) {
            imageView.setVisibility(View.GONE);
            imageView.setImageBitmap(null);

            setupLottieAnimationView(imageRes, animation);
        } else if (imageRes > 0) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageResource(imageRes);
        } else {
            imageView.setVisibility(View.GONE);
            imageView.setImageBitmap(null);
        }

        readyButton.setVisibility(showReadyButton ? View.VISIBLE : View.GONE);
        tryAgainButton.setVisibility(showTryAgain ? View.VISIBLE : View.GONE);
        okButton.setVisibility(showOk ? View.VISIBLE : View.GONE);

        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        progressBar.setMax((int) COUNTDOWN_DURATION);
        if (showProgressComplete) {
            progressCountDownTimer.cancel();
            progressBar.setProgress((int) COUNTDOWN_DURATION);
        } else if (showProgress && !showingProgress) {
            progressCountDownTimer.start();
        } else if (!showingProgress) {
            progressCountDownTimer.cancel();
        }

        if (delayedStateTransition) {
            handler.postDelayed(delayStateRunnable, DELAY_STATE_TRANSITION);
        }

    }

    private void setupLottieAnimationView(int imageRes, Animation animation) {
        addNewLottieAnimationView(animation);
        animationImageView.setAnimation(imageRes);
        if (animation.looping) {
            animationImageView.setRepeatCount(INFINITE);
            animationImageView.setRepeatMode(RESTART);
        } else {
            animationImageView.setRepeatCount(0);
        }
        if (animation.listener != null) {
            animationImageView.addAnimatorListener(animation.listener);
        }
        animationImageView.playAnimation();
    }

    private void removeLottieAnimationView() {
        if (animationImageView != null) {
            animationImageView.removeAllAnimatorListeners();
            animationImageView.cancelAnimation();
            animationImageView.setImageBitmap(null);
            messageContainer.removeView(animationImageView);
            animationImageView = null;
        }
    }

    private LottieAnimationView addNewLottieAnimationView(Animation animation) {
        animationImageView = new LottieAnimationView(getContext());
        animationImageView.setId(R.id.animation_view);
//        animationImageView.enableMergePathsForKitKatAndAbove(true);
        messageContainer.addView(animationImageView);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(messageContainer);
        constraintSet.constrainWidth(R.id.animation_view, animation.width);
        constraintSet.constrainHeight(R.id.animation_view, animation.height);
        constraintSet.connect(R.id.animation_view, ConstraintSet.BOTTOM, R.id.message,
                ConstraintSet.TOP,
                getResources().getDimensionPixelSize(R.dimen.pair_animation_margin_bottom));
        constraintSet.connect(R.id.animation_view, ConstraintSet.LEFT, ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT);
        constraintSet.connect(R.id.animation_view, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT);

        constraintSet.applyTo(messageContainer);

        return animationImageView;
    }

    public void show() {
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        setVisibility(View.GONE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onErrorDisplaying() {
        messageContainer.setVisibility(View.GONE);
    }

    @Override
    public void onErrorRemoved() {
        messageContainer.setVisibility(View.VISIBLE);
    }

    public interface Listener {

        void onPairCanceled();

        void onPairViewClosed();

        void onReadyToSetAnchor();

        void onReadyResolveAnchor();

        void setAnchorResolvingMode();

        void setPairErrorMode();

        void setPairSuccessMode();

        void attemptPartnerDiscovery();
    }
}

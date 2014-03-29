/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.RotarySelector;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.media.AudioManager;
import android.os.SystemProperties;

import com.android.internal.telephony.IccCard;

import java.util.Date;
import java.io.File;
import java.text.SimpleDateFormat;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen, KeyguardUpdateMonitor.InfoCallback,
        KeyguardUpdateMonitor.SimStateCallback, KeyguardUpdateMonitor.ConfigurationChangeCallback,
        RotarySelector.OnDialTriggerListener {

    private static final boolean DBG = false;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";

    private Status mStatus = Status.Normal;

    private final LockPatternUtils mLockPatternUtils;
    private final KeyguardUpdateMonitor mUpdateMonitor;
    private final KeyguardScreenCallback mCallback;

    private TextView mCarrier;
    private RotarySelector mRotary;
    private TextView mTime;
    private TextView mDate;
    private TextView mStatus1;
    private TextView mStatus2;
    private TextView mScreenLocked;
    private Button mEmergencyCallButton;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    private String mNextAlarm = null;
    private Drawable mAlarmIcon = null;
    private String mCharging = null;
    private Drawable mChargingIcon = null;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private java.text.DateFormat mDateFormat;
    private java.text.DateFormat mTimeFormat;
    private boolean mCreatedInPortrait;
    private boolean mEnableMenuKeyInLockScreen;

    /**
     * The status of this lock screen.
     */
    enum Status {
        /**
         * Normal case (sim card present, it's not locked)
         */
        Normal(true),

        /**
         * The sim card is 'network locked'.
         */
        NetworkLocked(true),

        /**
         * The sim card is missing.
         */
        SimMissing(false),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so we don't let
         * them get past the screen.
         */
        SimMissingLocked(false),

        /**
         * The sim card is PUK locked, meaning they've entered the wrong sim unlock code too many
         * times.
         */
        SimPukLocked(false),

        /**
         * The sim card is locked.
         */
        SimLocked(true);

        private final boolean mShowStatusLines;

        Status(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean showStatusLines() {
            return mShowStatusLines;
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isMonkey = SystemProperties.getBoolean("ro.monkey", false);
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        return !configDisabled || isMonkey || fileOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreatedInPortrait = updateMonitor.isInPortrait();

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (mCreatedInPortrait) {
            inflater.inflate(R.layout.keyguard_screen_rotary_unlock, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_rotary_unlock_land, this, true);
        }

        mCarrier = (TextView) findViewById(R.id.carrier);
        mTime = (TextView) findViewById(R.id.time);
        mDate = (TextView) findViewById(R.id.date);
        mStatus1 = (TextView) findViewById(R.id.status1);
        mStatus2 = (TextView) findViewById(R.id.status2);

        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);
        mScreenLocked = (TextView) findViewById(R.id.screenLocked);
        mRotary = (RotarySelector) findViewById(R.id.rotary);
        mEmergencyCallButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.takeEmergencyCallAction();
            }
        });

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        updateMonitor.registerInfoCallback(this);
        updateMonitor.registerSimStateCallback(this);
        updateMonitor.registerConfigurationChangeCallback(this);

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;

        mRotary.setOnDialTriggerListener(this);
        mRotary.setLeftHandleResource(R.drawable.ic_jog_dial_unlock);
        mRotary.setRightHandleResource(mSilentMode ?
                R.drawable.ic_jog_dial_sound_off :
                R.drawable.ic_jog_dial_sound_on);

        resetStatusInfo(updateMonitor);
    }

    private void resetStatusInfo(KeyguardUpdateMonitor updateMonitor) {
        mShowingBatteryInfo = updateMonitor.shouldShowBatteryInfo();
        mPluggedIn = updateMonitor.isDevicePluggedIn();
        mBatteryLevel = updateMonitor.getBatteryLevel();

        mStatus = getCurrentStatus(updateMonitor.getSimState());
        updateLayout(mStatus);

        refreshBatteryStringAndIcon();
        refreshAlarmDisplay();

        mTimeFormat = DateFormat.getTimeFormat(getContext());
        mDateFormat = getLockScreenDateFormat();
        refreshTimeAndDateDisplay();
        updateStatusLines();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen) {
            mCallback.goToUnlockScreen();
        }
        return false;
    }

    /** {@inheritDoc} */
    public void onDialTrigger(View v, int whichHandle) {
        if (whichHandle == RotarySelector.OnDialTriggerListener.LEFT_HANDLE) {
            mCallback.goToUnlockScreen();
        } else if (whichHandle == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
            // toggle silent mode
            mSilentMode = !mSilentMode;
            mAudioManager.setRingerMode(mSilentMode ? AudioManager.RINGER_MODE_SILENT
                        : AudioManager.RINGER_MODE_NORMAL);
            final int handleIcon = mSilentMode ?
                    R.drawable.ic_jog_dial_sound_off :
                    R.drawable.ic_jog_dial_sound_on;
            final int toastIcon = mSilentMode ?
                    R.drawable.ic_lock_ringer_off :
                    R.drawable.ic_lock_ringer_on;
            mRotary.setRightHandleResource(handleIcon);
            String message = mSilentMode ?
                    getContext().getString(R.string.global_action_silent_mode_on_status) :
                    getContext().getString(R.string.global_action_silent_mode_off_status);
            toastMessage(mScreenLocked, message, toastIcon);
            mCallback.pokeWakelock();
        }
    }

    /** {@inheritDoc} */
    public void onGrabbedStateChange(View v, int grabbedState) {
        // TODO: Update onscreen hint text based on the new state.
    }

    /**
     * Displays a message in a text view and then removes it.
     * @param textView The text view.
     * @param text The text.
     * @param iconResourceId The left hand icon.
     */
    private void toastMessage(final TextView textView, final String text, final int iconResourceId) {
        if (mPendingR1 != null) {
            textView.removeCallbacks(mPendingR1);
            mPendingR1 = null;
        }
        if (mPendingR2 != null) {
            textView.removeCallbacks(mPendingR2);
            mPendingR2 = null;
        }

        mPendingR1 = new Runnable() {
            public void run() {
                textView.setText(text);
                textView.setCompoundDrawablesWithIntrinsicBounds(iconResourceId, 0, 0, 0);
                textView.setCompoundDrawablePadding(4);
            }
        };
        textView.postDelayed(mPendingR1, 0);
        mPendingR2 = new Runnable() {
            public void run() {
                textView.setText("");
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        };
        textView.postDelayed(mPendingR2, 3500);
    }
    private Runnable mPendingR1;
    private Runnable mPendingR2;


    private void refreshAlarmDisplay() {
        mNextAlarm = mLockPatternUtils.getNextAlarm();
        if (mNextAlarm != null) {
            mAlarmIcon = getContext().getResources().getDrawable(R.drawable.ic_lock_idle_alarm);
        }
        updateStatusLines();
    }

    /** {@inheritDoc} */
    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
            int batteryLevel) {
        if (DBG) Log.d(TAG, "onRefreshBatteryInfo(" + showBatteryInfo + ", " + pluggedIn + ")");
        mShowingBatteryInfo = showBatteryInfo;
        mPluggedIn = pluggedIn;
        mBatteryLevel = batteryLevel;

        refreshBatteryStringAndIcon();
        updateStatusLines();
    }

    private void refreshBatteryStringAndIcon() {
        if (!mShowingBatteryInfo) {
            mCharging = null;
            return;
        }

        if (mChargingIcon == null) {
            mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_charging);
        }

        if (mPluggedIn) {
            if (mBatteryLevel >= 100) {
                mCharging = getContext().getString(R.string.lockscreen_charged);
            } else {
                mCharging = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
            }
        } else {
            mCharging = getContext().getString(R.string.lockscreen_low_battery);
        }
    }

    /** {@inheritDoc} */
    public void onTimeChanged() {
        refreshTimeAndDateDisplay();
    }

    private void refreshTimeAndDateDisplay() {
        Date now = new Date();
        mTime.setText(mTimeFormat.format(now));
        mDate.setText(mDateFormat.format(now));
    }

    /**
     * @return A localized format like "Fri, Sep 18, 2009"
     */
    private java.text.DateFormat getLockScreenDateFormat() {
        SimpleDateFormat adjusted = null;
        try {
            // this call gives us the localized order
            final SimpleDateFormat dateFormat = (SimpleDateFormat)
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL);
            adjusted = new SimpleDateFormat(dateFormat.toPattern()
                    .replace("MMMM", "MMM")    // we want "Sep", not "September"
                    .replace("EEEE", "EEE"));  // we want "Fri", no "Friday"
        } catch (ClassCastException e) {
            // in case the library implementation changes and this throws a class cast exception
            // or anything else that is funky
            Log.e("LockScreen", "couldn't finnagle our custom date format :(", e);
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
        }
        return adjusted;
    }

    private void updateStatusLines() {
        if (!mStatus.showStatusLines()
                || (mCharging == null && mNextAlarm == null)) {
            mStatus1.setVisibility(View.INVISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);
        } else if (mCharging != null && mNextAlarm == null) {
            // charging only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
        } else if (mNextAlarm != null && mCharging == null) {
            // next alarm only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mNextAlarm);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        } else if (mCharging != null && mNextAlarm != null) {
            // both charging and next alarm
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.VISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
            mStatus2.setText(mNextAlarm);
            mStatus2.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        }
    }

    /** {@inheritDoc} */
    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
        if (DBG) Log.d(TAG, "onRefreshCarrierInfo(" + plmn + ", " + spn + ")");
        updateLayout(mStatus);
    }

    private void putEmergencyBelow(int viewId) {
        final RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mEmergencyCallButton.getLayoutParams();
        layoutParams.addRule(RelativeLayout.BELOW, viewId);
        mEmergencyCallButton.setLayoutParams(layoutParams);
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    private Status getCurrentStatus(IccCard.State simState) {
        boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && simState == IccCard.State.ABSENT);
        if (missingAndNotProvisioned) {
            return Status.SimMissingLocked;
        }

        switch (simState) {
            case ABSENT:
                return Status.SimMissing;
            case NETWORK_LOCKED:
                return Status.SimMissingLocked;
            case NOT_READY:
                return Status.SimMissing;
            case PIN_REQUIRED:
                return Status.SimLocked;
            case PUK_REQUIRED:
                return Status.SimPukLocked;
            case READY:
                return Status.Normal;
            case UNKNOWN:
                return Status.SimMissing;
        }
        return Status.SimMissing;
    }

    /**
     * Update the layout to match the current status.
     */
    private void updateLayout(Status status) {
        switch (status) {
            case Normal:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                mUpdateMonitor.getTelephonySpn()));
//                mScreenLocked.setText(R.string.lockscreen_screen_locked);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mRotary.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.GONE);
                break;
            case NetworkLocked:
                //  text
                mCarrier.setText(R.string.lockscreen_network_locked_message);
                mScreenLocked.setText(R.string.lockscreen_instructions_when_pattern_disabled);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mRotary.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.GONE);
                break;
            case SimMissing:
                // text
                mCarrier.setText(R.string.lockscreen_missing_sim_message_short);
                mScreenLocked.setText(R.string.lockscreen_instructions_when_pattern_disabled);

                // layout
                mScreenLocked.setVisibility(View.INVISIBLE);
                mRotary.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                putEmergencyBelow(R.id.divider);
                break;
            case SimMissingLocked:
                // text
                mCarrier.setText(R.string.lockscreen_missing_sim_message_short);
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mRotary.setVisibility(View.GONE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                putEmergencyBelow(R.id.screenLocked);
                break;
            case SimLocked:
                // text
                mCarrier.setText(R.string.lockscreen_sim_locked_message);

                // layout
                mScreenLocked.setVisibility(View.INVISIBLE);
                mRotary.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.GONE);
                break;
            case SimPukLocked:
                // text
                mCarrier.setText(R.string.lockscreen_sim_puk_locked_message);
                mScreenLocked.setText(R.string.lockscreen_sim_puk_locked_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mRotary.setVisibility(View.GONE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                putEmergencyBelow(R.id.screenLocked);
                break;
        }
    }

    static CharSequence getCarrierString(CharSequence telephonyPlmn, CharSequence telephonySpn) {
        if (telephonyPlmn != null && telephonySpn == null) {
            return telephonyPlmn;
        } else if (telephonyPlmn != null && telephonySpn != null) {
            return telephonyPlmn + "\n" + telephonySpn;
        } else if (telephonyPlmn == null && telephonySpn != null) {
            return telephonySpn;
        } else {
            return "";
        }
    }

    public void onSimStateChanged(IccCard.State simState) {
        if (DBG) Log.d(TAG, "onSimStateChanged(" + simState + ")");
        mStatus = getCurrentStatus(simState);
        updateLayout(mStatus);
        updateStatusLines();
    }


    public void onOrientationChange(boolean inPortrait) {
        if (inPortrait != mCreatedInPortrait) {
            mCallback.recreateMe();
        }
    }

    public void onKeyboardChange(boolean isKeyboardOpen) {
        if (mUpdateMonitor.isKeyguardBypassEnabled() && isKeyboardOpen) {
            mCallback.goToUnlockScreen();
        }
    }


    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }
    
    /** {@inheritDoc} */
    public void onPause() {

    }

    /** {@inheritDoc} */
    public void onResume() {
        resetStatusInfo(mUpdateMonitor);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this);
    }
}
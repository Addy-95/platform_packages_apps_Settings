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

package com.android.settings;

import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.provider.Telephony.Intents.SPN_STRINGS_UPDATED_ACTION;

import com.android.internal.telephony.TelephonyIntents;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.VolumePreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Special preference type that allows configuration of both the ring volume and
 * notification volume.
 */
public class RingerVolumePreference extends VolumePreference implements
        CheckBox.OnCheckedChangeListener, OnClickListener {
    private static final String TAG = "RingerVolumePreference";
    private static final int MSG_RINGER_MODE_CHANGED = 101;

    private CheckBox mNotificationsUseRingVolumeCheckbox;
    private SeekBarVolumizer [] mSeekBarVolumizer;
    private boolean mIgnoreVolumeKeys;

    // These arrays must all match in length and order
    private static final int[] SEEKBAR_ID = new int[] {
        R.id.notification_volume_seekbar,
        R.id.media_volume_seekbar,
        R.id.alarm_volume_seekbar
    };

    private static final int[] NEED_VOICE_CAPABILITY_ID = new int[] {
        R.id.ringtone_label,
        com.android.internal.R.id.seekbar,
        R.id.same_notification_volume
    };

    private static final int[] SEEKBAR_TYPE = new int[] {
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM
    };

    private static final int[] CHECKBOX_VIEW_ID = new int[] {
        R.id.notification_mute_button,
        R.id.volume_mute_button,
        R.id.alarm_mute_button
    };

    private static final int[] SEEKBAR_MUTED_RES_ID = new int[] {
        com.android.internal.R.drawable.ic_audio_notification_mute,
        com.android.internal.R.drawable.ic_audio_vol_mute,
        com.android.internal.R.drawable.ic_audio_alarm_mute
    };

    private static final int[] SEEKBAR_UNMUTED_RES_ID = new int[] {
        com.android.internal.R.drawable.ic_audio_notification,
        com.android.internal.R.drawable.ic_audio_vol,
        com.android.internal.R.drawable.ic_audio_alarm
    };

    private ImageView[] mCheckBoxes = new ImageView[SEEKBAR_MUTED_RES_ID.length];
    private SeekBar[] mSeekBars = new SeekBar[SEEKBAR_ID.length];

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            updateSlidersAndMutedStates();
        }
    };

    @Override
    public void createActionButtons() {
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(null);
    }

    private void updateSlidersAndMutedStates() {
        for (int i = 0; i < SEEKBAR_TYPE.length; i++) {
            int streamType = SEEKBAR_TYPE[i];
            boolean muted = mAudioManager.isStreamMute(streamType);

            if (mCheckBoxes[i] != null) {
                mCheckBoxes[i].setImageResource(
                        muted ? SEEKBAR_MUTED_RES_ID[i] : SEEKBAR_UNMUTED_RES_ID[i]);
            }
            if (mSeekBars[i] != null) {
                mSeekBars[i].setEnabled(!muted);
                final int volume = muted ? mAudioManager.getLastAudibleStreamVolume(streamType)
                        : mAudioManager.getStreamVolume(streamType);
                mSeekBars[i].setProgress(volume);
            }
        }
    }

    private BroadcastReceiver mRingModeChangedReceiver;
    private AudioManager mAudioManager;

    //private SeekBarVolumizer mNotificationSeekBarVolumizer;
    //private TextView mNotificationVolumeTitle;

    public RingerVolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // The always visible seekbar is for ring volume
        setStreamType(AudioManager.STREAM_RING);

        setDialogLayoutResource(R.layout.preference_dialog_ringervolume);
        //setDialogIcon(R.drawable.ic_settings_sound);

        mSeekBarVolumizer = new SeekBarVolumizer[SEEKBAR_ID.length];
        mIgnoreVolumeKeys = !Utils.isVoiceCapable(context);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            SeekBar seekBar = (SeekBar) view.findViewById(SEEKBAR_ID[i]);
            mSeekBars[i] = seekBar;
            if (SEEKBAR_TYPE[i] == AudioManager.STREAM_MUSIC) {
                mSeekBarVolumizer[i] = new SeekBarVolumizer(getContext(), seekBar,
                        SEEKBAR_TYPE[i], getMediaVolumeUri(getContext()));
            } else {
                mSeekBarVolumizer[i] = new SeekBarVolumizer(getContext(), seekBar,
                        SEEKBAR_TYPE[i]);
            }
        }

        //mNotificationVolumeTitle = (TextView) view.findViewById(R.id.notification_volume_title);
        mNotificationsUseRingVolumeCheckbox =
                (CheckBox) view.findViewById(R.id.same_notification_volume);
        mNotificationsUseRingVolumeCheckbox.setOnCheckedChangeListener(this);
        mNotificationsUseRingVolumeCheckbox.setChecked(
                Utils.isVoiceCapable(getContext())
                && Settings.System.getInt(
                        getContext().getContentResolver(),
                        Settings.System.NOTIFICATIONS_USE_RING_VOLUME, 1) == 1);
        setNotificationVolumeVisibility(!mNotificationsUseRingVolumeCheckbox.isChecked());
        disableSettingsThatNeedVoice(view);

        // Register callbacks for mute/unmute buttons
        for (int i = 0; i < mCheckBoxes.length; i++) {
            ImageView checkbox = (ImageView) view.findViewById(CHECKBOX_VIEW_ID[i]);
            checkbox.setOnClickListener(this);
            mCheckBoxes[i] = checkbox;
        }

        // Load initial states from AudioManager
        updateSlidersAndMutedStates();

        // Listen for updates from AudioManager
        if (mRingModeChangedReceiver == null) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mRingModeChangedReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                                intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
                    }
                }
            };
            getContext().registerReceiver(mRingModeChangedReceiver, filter);
        }
    }

    private Uri getMediaVolumeUri(Context context) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + context.getPackageName()
                + "/" + R.raw.media_volume);
    }

    private void disableSettingsThatNeedVoice(View parent) {
        final boolean voiceCapable = Utils.isVoiceCapable(getContext());
        if (!voiceCapable) {
            for (int id : NEED_VOICE_CAPABILITY_ID) {
                parent.findViewById(id).setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            for (SeekBarVolumizer vol : mSeekBarVolumizer) {
                if (vol != null) vol.revertVolume();
            }
        }
        cleanup();
    }

    @Override
    public void onActivityStop() {
        super.onActivityStop();
        cleanup();
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setNotificationVolumeVisibility(!isChecked);

        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.NOTIFICATIONS_USE_RING_VOLUME, isChecked ? 1 : 0);

        if (isChecked) {
            // The user wants the notification to be same as ring, so do a
            // one-time sync right now
            mAudioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION,
                    mAudioManager.getStreamVolume(AudioManager.STREAM_RING), 0);
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean isdown = (event.getAction() == KeyEvent.ACTION_DOWN);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                if (mIgnoreVolumeKeys) {
                    return true;
                } else {
                    return super.onKey(v, keyCode, event);
                }
            default:
                return false;
        }
    }

    @Override
    protected void onSampleStarting(SeekBarVolumizer volumizer) {
        super.onSampleStarting(volumizer);
        for (SeekBarVolumizer vol : mSeekBarVolumizer) {
            if (vol != null && vol != volumizer) vol.stopSample();
        }
    }

    private void setNotificationVolumeVisibility(boolean visible) {
        if (mSeekBarVolumizer[0] != null) {
            mSeekBarVolumizer[0].getSeekBar().setVisibility(
                    visible ? View.VISIBLE : View.GONE);
        }
        // mNotificationVolumeTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void cleanup() {
        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            if (mSeekBarVolumizer[i] != null) {
                Dialog dialog = getDialog();
                if (dialog != null && dialog.isShowing()) {
                    // Stopped while dialog was showing, revert changes
                    mSeekBarVolumizer[i].revertVolume();
                }
                mSeekBarVolumizer[i].stop();
                mSeekBarVolumizer[i] = null;
            }
        }
        if (mRingModeChangedReceiver != null) {
            getContext().unregisterReceiver(mRingModeChangedReceiver);
            mRingModeChangedReceiver = null;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        VolumeStore[] volumeStore = myState.getVolumeStore(SEEKBAR_ID.length);
        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            SeekBarVolumizer vol = mSeekBarVolumizer[i];
            if (vol != null) {
                vol.onSaveInstanceState(volumeStore[i]);
            }
        }
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        VolumeStore[] volumeStore = myState.getVolumeStore(SEEKBAR_ID.length);
        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            SeekBarVolumizer vol = mSeekBarVolumizer[i];
            if (vol != null) {
                vol.onRestoreInstanceState(volumeStore[i]);
            }
        }
    }

    private static class SavedState extends BaseSavedState {
        VolumeStore [] mVolumeStore;

        public SavedState(Parcel source) {
            super(source);
            mVolumeStore = new VolumeStore[SEEKBAR_ID.length];
            for (int i = 0; i < SEEKBAR_ID.length; i++) {
                mVolumeStore[i] = new VolumeStore();
                mVolumeStore[i].volume = source.readInt();
                mVolumeStore[i].originalVolume = source.readInt();
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            for (int i = 0; i < SEEKBAR_ID.length; i++) {
                dest.writeInt(mVolumeStore[i].volume);
                dest.writeInt(mVolumeStore[i].originalVolume);
            }
        }

        VolumeStore[] getVolumeStore(int count) {
            if (mVolumeStore == null || mVolumeStore.length != count) {
                mVolumeStore = new VolumeStore[count];
                for (int i = 0; i < count; i++) {
                    mVolumeStore[i] = new VolumeStore();
                }
            }
            return mVolumeStore;
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public void onClick(View v) {
        // Touching any of the mute buttons causes us to get the state from the system and toggle it
        switch(mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
            case AudioManager.RINGER_MODE_SILENT:
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            break;
        }
    }
}

/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.wireguard.android.widget;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.Switch;

public class ToggleSwitch extends Switch {
    private boolean hasPendingStateChange;
    private boolean isRestoringState;
    private OnBeforeCheckedChangeListener listener;

    public ToggleSwitch(final Context context) {
        super(context);
    }

    public ToggleSwitch(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleSwitch(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ToggleSwitch(final Context context, final AttributeSet attrs, final int defStyleAttr,
                        final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        isRestoringState = true;
        super.onRestoreInstanceState(state);
        isRestoringState = false;

    }

    @Override
    public void setChecked(final boolean checked) {
        if (isRestoringState || listener == null) {
            super.setChecked(checked);
            return;
        }
        if (hasPendingStateChange)
            return;
        hasPendingStateChange = true;
        setEnabled(false);
        listener.onBeforeCheckedChanged(this, checked);
    }

    public void setCheckedInternal(final boolean checked) {
        if (hasPendingStateChange) {
            setEnabled(true);
            hasPendingStateChange = false;
        }
        super.setChecked(checked);
    }

    public void setOnBeforeCheckedChangeListener(final OnBeforeCheckedChangeListener listener) {
        this.listener = listener;
    }

    public interface OnBeforeCheckedChangeListener {
        void onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked);
    }
}

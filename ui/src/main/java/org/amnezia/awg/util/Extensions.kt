/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import org.amnezia.awg.Application
import org.amnezia.awg.activity.SettingsActivity
import kotlinx.coroutines.CoroutineScope

fun Context.resolveAttribute(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

val Any.applicationScope: CoroutineScope
    get() = Application.getCoroutineScope()

val Preference.activity: SettingsActivity
    get() = context as? SettingsActivity
        ?: throw IllegalStateException("Failed to resolve SettingsActivity")

val Preference.lifecycleScope: CoroutineScope
    get() = activity.lifecycleScope

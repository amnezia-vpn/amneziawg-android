/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.vpn.util

import android.content.RestrictionsManager
import androidx.core.content.getSystemService
import org.amnezia.vpn.Application

object AdminKnobs {
    private val restrictions: RestrictionsManager? = Application.get().getSystemService()
    val disableConfigExport: Boolean
        get() = restrictions?.applicationRestrictions?.getBoolean("disable_config_export", false)
            ?: false
}

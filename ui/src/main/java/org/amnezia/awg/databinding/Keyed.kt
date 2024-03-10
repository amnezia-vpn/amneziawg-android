/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.databinding

/**
 * Interface for objects that have a identifying key of the given type.
 */
interface Keyed<K> {
    val key: K
}

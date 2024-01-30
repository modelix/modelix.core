/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.util

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
enum class SyncLockType {
    MPS_WRITE,
    MPS_READ,
    MODELIX_WRITE,
    MODELIX_READ,
    CUSTOM,
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SnycLockTypeComparator : Comparator<SyncLockType> {

    /**
     * Order of locks is important, because MPS executes the action on a separate thread, where the Modelix transactions might not be available.
     * Lock priority order: MPS_WRITE > MPS_READ > MODELIX_WRITE > MODELIX_READ > CUSTOM
     */
    override fun compare(p0: SyncLockType, p1: SyncLockType) =
        if (p0 == SyncLockType.MPS_WRITE) {
            if (p0 == p1) {
                0
            } else {
                -1
            }
        } else if (p0 == SyncLockType.MPS_READ) {
            if (p1 == SyncLockType.MPS_WRITE) {
                1
            } else if (p0 == p1) {
                0
            } else {
                -1
            }
        } else if (p0 == SyncLockType.MODELIX_WRITE) {
            if (p1 == SyncLockType.MPS_READ || p1 == SyncLockType.MPS_WRITE) {
                1
            } else if (p0 == p1) {
                0
            } else {
                -1
            }
        } else if (p0 == SyncLockType.MODELIX_READ) {
            if (p1 == SyncLockType.CUSTOM) {
                -1
            } else if (p0 == p1) {
                0
            } else {
                1
            }
        } else if (p0 == SyncLockType.CUSTOM) {
            if (p0 == p1) {
                0
            } else {
                1
            }
        } else {
            0
        }
}

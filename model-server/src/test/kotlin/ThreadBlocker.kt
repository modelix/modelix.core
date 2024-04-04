import org.slf4j.LoggerFactory

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

class ThreadBlocker {

    companion object {
        private val LOG = LoggerFactory.getLogger(ThreadBlocker::class.java)
    }

    private var reachedPointInTime = 0

    @Synchronized
    fun reachPointInTime(pointInTime: Int) {
        reachedPointInTime = pointInTime
        LOG.debug("Reached point in time {}", reachedPointInTime)
    }

    fun sleepUntilPointInTime(pointInTime: Int) {
        LOG.debug("Waiting for point in time {}", pointInTime)
        sleepUntil {
            reachedPointInTime >= pointInTime
        }
    }
}

fun sleepUntil(
    checkIntervalMilliseconds: Long = 10,
    timeoutMilliseconds: Long = 1000,
    condition: () -> Boolean,
) {
    check(checkIntervalMilliseconds > 0) {
        "checkIntervalMilliseconds must be positive."
    }
    check(timeoutMilliseconds > 0) {
        "timeoutMilliseconds must be positive."
    }
    var remainingDelays = timeoutMilliseconds / checkIntervalMilliseconds
    while (!condition()) {
        if (remainingDelays == 0L) {
            error("Waited too long.")
        }
        Thread.sleep(checkIntervalMilliseconds)
        remainingDelays--
    }
}

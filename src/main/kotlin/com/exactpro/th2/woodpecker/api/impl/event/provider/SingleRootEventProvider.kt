/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.woodpecker.api.impl.event.provider

import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.woodpecker.api.impl.event.EventGeneratorSettings

class SingleRootEventProvider(settings: EventGeneratorSettings) : EventProvider(settings) {
    private val initialTree = createEventTree()
    private val eventQueue = enqueueEvents(initialTree)
    private val leafIds = collectLeafIds(initialTree)
    private var batchId: EventID = toEventID("")

    //for the first batch we want EventID to be empty, so we can create root event
    //the first batch will also fully create initial event tree
    //after that each new batch will be attached to one of the leaf nodes of the initial event tree
    override fun getParentEventIdForBatch(): EventID {
        if (firstBatch) {
            return batchId
        }
        batchId = leafIds.random()
        return batchId
    }

    //for the first batch we want to create initial event tree, so we get parent ids form a predefined set of ids
    //if batch is bigger thant initial event tree, the remaining events will be attached to random leaves
    //for the all consecutive batches we are attaching all events to the same random leaf node of the initial event tree
    override fun nextEvent(): Event {
        return if (firstBatch) {
            if (eventQueue.isNotEmpty()) eventQueue.remove() else eventWithParentId(leafIds.random())
        } else {
            eventWithParentId(batchId)
        }
    }
}
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

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.woodpecker.api.impl.event.EventGeneratorSettings

class SingleRootEventBatchProvider(settings: EventGeneratorSettings) : EventBatchProvider(settings) {
    private val initialTree = createEventTree()
    private val eventQueue = enqueueEvents(initialTree)
    private val leafIds = collectLeafIds(initialTree)
    private var firstBatch = true

    //for the first batch we want to create initial event tree, so we get parent ids form a predefined set of ids
    //if batch is bigger thant initial event tree, the remaining events will be attached to random leaves
    //for the all consecutive batches we are attaching all events to the same random leaf node of the initial event tree
    override fun nextBatch(batchSize: Int): EventBatch {
        if (firstBatch) {
            return firstBatch(batchSize)
        }
        val batchId = leafIds.random()
        return EventBatch.newBuilder().apply {
            repeat(batchSize) {
                addEvents(eventWithParentId(batchId))
            }
        }.setParentEventId(batchId).build()
    }

    @Synchronized
    private fun firstBatch(batchSize: Int): EventBatch {
        firstBatch = false
        val eventQueueLength = eventQueue.size
        return EventBatch.newBuilder().apply {
            addAllEvents(eventQueue)
            repeat(batchSize - eventQueueLength) {
                addEvents(eventWithParentId(leafIds.random()))
            }
        }.setParentEventId(toEventID("")).build()
    }
}
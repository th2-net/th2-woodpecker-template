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

import com.exactpro.th2.common.event.bean.builder.MessageBuilder
import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.woodpecker.api.impl.event.util.DataGenerator
import com.exactpro.th2.woodpecker.api.impl.event.EventGeneratorSettings
import com.exactpro.th2.woodpecker.api.impl.event.EventTreeNode
import java.util.*

abstract class EventBatchProvider(val settings: EventGeneratorSettings) {
    private val statusGenerator: () -> com.exactpro.th2.common.event.Event.Status = { DataGenerator.generateStatus(settings.failureRate) }
    private val descriptionGenerator: () -> String = { DataGenerator.generateStringSizeOf(settings.descriptionLength) }
    private val bodyMessageGenerator: () -> String = { DataGenerator.generateStringSizeOf(settings.bodyDataLength) }
    private val nameGenerator: () -> String = { DataGenerator.generateIdString() }

    private val _bookName = settings.bookName
    private val _scope = settings.scope

    abstract fun nextBatch(batchSize: Int): EventBatch

    fun eventWithParentId(parentID: EventID): Event =
        com.exactpro.th2.common.event.Event.start()
            .name(nameGenerator())
            .bodyData(MessageBuilder().text(bodyMessageGenerator()).build())
            .description(descriptionGenerator())
            .type("woodpecker")
            .status(statusGenerator())
            .endTimestamp()
            .toProto(parentID)

    fun enqueueEvents(eventTree: EventTreeNode): Queue<Event> {
        val eventCollector: Queue<Event> = LinkedList()
        EventTreeNode.traverseBreadthFirst(eventTree) { eventCollector.add(it.event) }
        return eventCollector
    }

    fun collectLeafIds(eventTree: EventTreeNode): List<EventID> {
        val eventIdCollector: MutableList<EventID> = ArrayList()
        EventTreeNode.findLeaves(eventTree) { eventIdCollector.add(it.event.id) }
        return eventIdCollector
    }

    fun createEventTree(): EventTreeNode {
        val rootNode = EventTreeNode(eventWithParentId(toEventID("")))
        repeat(settings.treeDepth) { EventTreeNode.growTree(rootNode, settings.childCount, this) }
        return rootNode
    }

    fun toEventID(id: String): EventID = EventID.newBuilder().apply {
        this.id = id
        this.bookName = _bookName
        this.scope = _scope
    }.build()
}
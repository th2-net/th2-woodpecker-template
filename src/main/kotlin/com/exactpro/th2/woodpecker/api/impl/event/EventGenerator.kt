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

package com.exactpro.th2.woodpecker.api.impl.event

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.Event.start
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.woodpecker.api.event.IEventGenerator
import com.exactpro.th2.woodpecker.api.impl.GeneratorSettings
import mu.KotlinLogging
import java.util.LinkedList
import java.util.Queue

class EventGenerator(
    settings: EventGeneratorSettings
) : IEventGenerator<GeneratorSettings> {

    private var context = Context(settings)

    override fun onStart(settings: GeneratorSettings?) {
        settings?.let {
            context = Context(settings.eventGeneratorSettings)
            logger.info { "Reset generator settings" }
        }
    }

    override fun onStop() {
        logger.info { "EventGenerator::onStop" }
    }

    override fun onNext(size: Int): EventBatch {
        return EventBatch.newBuilder().apply {
            repeat(size) {
                addEvents(
                    start()
                        .name(context.nameGenerator())
                        .description(context.descriptionGenerator())
                        .type("loader")
                        .status(context.statusGenerator())
                        .endTimestamp()
                        .toProto("ID")
                )
            }
        }.build()
    }

    fun collectEventTreeIds(eventTree: EventTreeNode): Queue<String> {
        val eventIdCollector: Queue<String> = LinkedList()
        EventTreeNode.traverseBreadthFirst(eventTree) { eventIdCollector.add(it.parentId) }
        EventTreeNode.traverseBreadthFirst(eventTree) { println("id: ${it.id} parent: ${it.parentId}") }
        return eventIdCollector
    }

    fun collectLeafIds(eventTree: EventTreeNode): List<String> {
        val eventIdCollector: MutableList<String> = ArrayList()
        EventTreeNode.findLeaves(eventTree) { eventIdCollector.add(it.id) }
        EventTreeNode.findLeaves(eventTree) { println("id: ${it.id}") }
        return eventIdCollector
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }
}

private class Context(
    settings: EventGeneratorSettings
) {
    val generator = Generator()
    val statusGenerator: () -> Event.Status = { generator.generateStatus(settings.failureRate) }
    val descriptionGenerator: () -> String = { generator.generateStringSizeOf(settings.descriptionLength) }
    val nameGenerator: () -> String = generator::generateIdString

    fun getBatchId(): String{
     return ""
    }

    fun getParentId(): String{
        return ""
    }
}
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

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.woodpecker.api.event.IEventGenerator
import com.exactpro.th2.woodpecker.api.impl.GeneratorSettings
import com.exactpro.th2.woodpecker.api.impl.event.provider.EventProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.MixedEventProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.MultiRootEventProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.SingleRootEventProvider
import mu.KotlinLogging
import kotlin.math.pow

class EventGenerator(
    val settings: EventGeneratorSettings
) : IEventGenerator<GeneratorSettings> {
    private val logger = KotlinLogging.logger { }
    private var context = Context(settings)
    private val treeSize = calculateTreeSize()

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
        if (treeSize > size) {
            throw RuntimeException("batch size can't be less than initial event tree size")
        }
        val batchId = context.getParentEventIdForBatch() //batch id needs to be called before getting event ids
        val eventBatch = EventBatch.newBuilder().apply {
            repeat(size) {
                addEvents(
                    context.nextEvent()
                )
            }
        }.setParentEventId(batchId).build()
        context.finishedBatch()
        return eventBatch
    }

    private fun calculateTreeSize(): Double {
        val childCount = settings.childCount.toDouble()
        val depth = settings.treeDepth
        return ((childCount.pow(depth + 1)) - 1) / (childCount - 1)
    }
}

private class Context(
    settings: EventGeneratorSettings
) {
    private val eventProvider: EventProvider = when (settings.generationMode) {
        GenerationMode.SINGLE_ROOT -> SingleRootEventProvider(settings)
        GenerationMode.MULTI_ROOT -> MultiRootEventProvider(settings)
        GenerationMode.MIXED -> MixedEventProvider(settings)
    }

    fun finishedBatch(){
        eventProvider.finishedBatch()
    }
    fun nextEvent() = eventProvider.nextEvent()
    fun getParentEventIdForBatch() = eventProvider.getParentEventIdForBatch()
}
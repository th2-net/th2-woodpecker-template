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
import com.exactpro.th2.woodpecker.api.impl.event.provider.EventBatchProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.MixedEventBatchProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.MultiRootEventBatchProvider
import com.exactpro.th2.woodpecker.api.impl.event.provider.SingleRootEventBatchProvider
import com.exactpro.th2.woodpecker.api.impl.raw.RawMessageGenerator
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

class EventGenerator(
    val settings: EventGeneratorSettings
) : IEventGenerator<GeneratorSettings> {
    private val defaultContext = Context(settings)

    private val activeContext = AtomicReference(defaultContext)
    private val treeSize = calculateTreeSize()

    override fun onStart(settings: GeneratorSettings?) {
        settings?.let {
            activeContext.set(Context(settings.eventGeneratorSettings))
            logger.info { "Updated event generator settings" }
        }
    }

    override fun onStop() {
        activeContext.getAndSet(defaultContext).also {previous ->
            if (previous !== defaultContext) {
                logger.info { "Reverted event generator settings to default" }
            }
        }
    }

    override fun onNext(size: Int): EventBatch {
        if (treeSize > size) {
            throw RuntimeException("batch size can't be less than initial event tree size")
        }
        return activeContext.get().nextBatch(size)
    }

    private fun calculateTreeSize(): Double {
        val childCount = settings.childCount.toDouble()
        val depth = settings.treeDepth
        return ((childCount.pow(depth + 1)) - 1) / (childCount - 1)
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }
}

private class Context(
    settings: EventGeneratorSettings
) {
    private val eventBatchProvider: EventBatchProvider = when (settings.generationMode) {
        GenerationMode.SINGLE_ROOT -> SingleRootEventBatchProvider(settings)
        GenerationMode.MULTI_ROOT -> MultiRootEventBatchProvider(settings)
        GenerationMode.MIXED -> MixedEventBatchProvider(settings)
    }

    fun nextBatch(batchSize: Int) = eventBatchProvider.nextBatch(batchSize)
}
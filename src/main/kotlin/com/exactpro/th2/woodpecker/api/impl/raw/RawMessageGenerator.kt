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

package com.exactpro.th2.woodpecker.api.impl.raw

import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Direction.FIRST
import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction.INCOMING
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction.OUTGOING
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import io.netty.buffer.Unpooled
import io.prometheus.client.Counter
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils.isNotBlank
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction as TransportDirection
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup as TransportMessageGroup

class RawMessageGenerator(
    private val settings: RawMessageGeneratorSettings
) : IMessageGenerator<RawMessageGeneratorSettings> {

    private val defaultContext = Context(settings)

    private val activeContext = AtomicReference(defaultContext)
    override fun onStart(settings: RawMessageGeneratorSettings?) {
        settings?.let {
            activeContext.set(Context(settings))
            K_LOGGER.info { "Updated generator settings" }
        }
    }

    override fun onStop() {
        activeContext.getAndSet(defaultContext).also {previous ->
            if (previous !== defaultContext) {
                K_LOGGER.info { "Reverted generator settings to default" }
            }
        }
    }

    override fun onNext(size: Int): MessageGroupBatch = MessageGroupBatch.newBuilder().apply {
        val context = activeContext.get()
        val sessionGroup = context.sessionGroups.random()
        repeat(size) {
            val sessionAlias = sessionGroup.aliases.random()
            val direction = context.dataGenerator.directions.random()
            addGroups(context.builder.apply {
                getMessagesBuilder(0).rawMessageBuilder.run {
                    metadataBuilder.apply {
                        idBuilder.apply {
                            connectionIdBuilder.apply {
                                this.sessionGroup = sessionGroup.name
                                this.sessionAlias = sessionAlias.name
                            }
                            bookName = settings.bookName
                            timestamp = Instant.now().toTimestamp()
                            sequence = sessionAlias.next(direction)
                        }

                    }
                    this.direction = direction
                    this.body = context.dataGenerator.nextByteString(direction)
                }
            }.build())
        }

    }.build()

    override fun onNextTransport(size: Int): GroupBatch {
        val context = activeContext.get()
        val group = context.sessionGroups.random()
        return GroupBatch(
            settings.bookName,
            group.name,
            generateSequence {
                val alias = group.aliases.random()
                val direction = context.dataGenerator.directions.random()
                TransportMessageGroup(
                    mutableListOf(
                        RawMessage(
                            MessageId(
                                alias.name,
                                direction.transport,
                                alias.next(direction),
                                timestamp = Instant.now()
                            ),
                            protocol = settings.protocol ?: "",
                            body = Unpooled.wrappedBuffer(context.dataGenerator.nextByteArray(direction))
                        )
                    )
                )
            }.take(size).toMutableList()
        )
    }

    companion object {
        internal val RANDOM = Random()
        private val K_LOGGER = KotlinLogging.logger {  }

        val Direction.transport: TransportDirection
            get() = when(this) {
                FIRST -> INCOMING
                SECOND -> OUTGOING
                else -> error("Unsupported $this direction")
            }
    }
}

class Context(
    settings: RawMessageGeneratorSettings
) {
    val sessionGroups = (1..settings.sessionGroupNumber).map { num ->
        val group = "${settings.sessionGroupPrefix}_$num"
        SessionGroup(
            group,
            (1..settings.sessionAliasNumber).map { SessionAlias(group, "${settings.sessionAliasPrefix}_${num}_$it") }
        )
    }

    val builder: MessageGroup.Builder = MessageGroup.newBuilder().apply {
        addMessagesBuilder().rawMessageBuilder.apply {
            metadataBuilder.apply {
                if (isNotBlank(settings.protocol)) {
                    protocol = settings.protocol
                }
            }
        }
    }

    val dataGenerator: IDataGenerator = settings.random ?: settings.oneOf ?: error("Neither for data generators is specified")

    init {
        K_LOGGER.info { "Prepared session groups: $sessionGroups" }
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {  }
    }
}

class SessionGroup(
    val name: String,
    val aliases: List<SessionAlias>
) {
    override fun toString(): String {
        return "SessionGroup(name='$name', aliases=$aliases)"
    }
}

class SessionAlias(
    group: String,
    val name: String
) {
    private val protoSequences: Map<Direction, AtomicLong>
    private val transportSequences: Map<TransportDirection, AtomicLong>
    private val counter = EnumMap<Direction, Counter.Child>(Direction::class.java).apply {
        put(FIRST, GENERATED_MESSAGES_TOTAL.labels(group, name, FIRST.name, "TRANSPORT_RAW"))
        put(SECOND, GENERATED_MESSAGES_TOTAL.labels(group, name, SECOND.name, "TRANSPORT_RAW"))
    }
    init {
        protoSequences = EnumMap<Direction, AtomicLong>(Direction::class.java).apply {
            Direction.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
        transportSequences = EnumMap<TransportDirection, AtomicLong>(TransportDirection::class.java).apply {
            TransportDirection.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
    }

    fun next(direction: Direction): Long = protoSequences[direction]?.incrementAndGet().also {
        counter[direction]?.inc()
    } ?: error("Sequence for the $direction direction isn't found")
    fun next(direction: TransportDirection): Long = transportSequences[direction]?.incrementAndGet() ?: error("Sequence for the $direction direction isn't found")
    override fun toString(): String {
        return "SessionAlias(name='$name', sequences=$protoSequences)"
    }

    companion object {
        private val GENERATED_MESSAGES_TOTAL: Counter = Counter.build()
            .name("th2_woodpecker_generated_messages_total")
            .labelNames("session_group", "session_alias", "direction", "th2_type")
            .help("Total number of consuming particular gRPC method")
            .register()
    }
}
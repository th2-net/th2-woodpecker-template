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

package com.exactpro.th2.woodpecker.api.impl

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
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import com.exactpro.th2.woodpecker.api.impl.RawMessageGenerator.Companion.RANDOM
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils.isNotBlank
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction as TransportDirection
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup as TransportMessageGroup

class RawMessageGenerator(
    private val settings: RawMessageGeneratorSettings
) : IMessageGenerator<RawMessageGeneratorSettings> {

    private val sessionGroups = (1..settings.sessionGroupNumber).map { num ->
        SessionGroup(
            "${settings.sessionGroupPrefix}_$num",
            (1..settings.sessionAliasNumber).map { SessionAlias("${settings.sessionAliasPrefix}_${num}_$it") }
        )
    }

    private val builder = MessageGroup.newBuilder().apply {
        addMessagesBuilder().rawMessageBuilder.apply {
            metadataBuilder.apply {
                if (isNotBlank(settings.protocol)) {
                    protocol = settings.protocol
                }
            }
        }
    }

    private val dataGenerator: IDataGenerator = settings.random ?: settings.oneOf ?: error("Neither for data generators is specified")

    init {
        K_LOGGER.info { "Prepared session groups: $sessionGroups" }
    }

    override fun onNext(size: Int): MessageGroupBatch = MessageGroupBatch.newBuilder().apply {
        val sessionGroup = sessionGroups.random()
        repeat(size) {
            val sessionAlias = sessionGroup.aliases.random()
            val direction = dataGenerator.directions.random()
            addGroups(builder.apply {
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
                    this.body = dataGenerator.nextByteString(direction)
                }
            }.build())
        }

    }.build()

    override fun onNextDemo(size: Int): GroupBatch {
        val group = sessionGroups.random()
        return GroupBatch(
            settings.bookName,
            group.name,
            generateSequence {
                val alias = group.aliases.random()
                val direction = dataGenerator.directions.random()
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
                            body = Unpooled.wrappedBuffer(dataGenerator.nextByteArray(direction))
                        )
                    )
                )
            }.take(size).toMutableList()
        )
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {  }
        internal val RANDOM = Random()

        val Direction.transport: TransportDirection
            get() = when(this) {
                FIRST -> INCOMING
                SECOND -> OUTGOING
                else -> error("Unsupported $this direction")
            }
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
    val name: String
) {
    private val sequences: Map<Direction, AtomicLong>
    private val demoSequences: Map<TransportDirection, AtomicLong>
    init {
        sequences = EnumMap<Direction, AtomicLong>(Direction::class.java).apply {
            Direction.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
        demoSequences = EnumMap<TransportDirection, AtomicLong>(TransportDirection::class.java).apply {
            TransportDirection.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
    }

    fun next(direction: Direction): Long = sequences[direction]?.incrementAndGet() ?: error("Sequence for the $direction direction isn't found")
    fun next(direction: TransportDirection): Long = demoSequences[direction]?.incrementAndGet() ?: error("Sequence for the $direction direction isn't found")
    override fun toString(): String {
        return "SessionAlias(name='$name', sequences=$sequences)"
    }

}

class RawMessageGeneratorSettings(
    val bookName: String,
    val sessionAliasPrefix: String = "session",
    val sessionAliasNumber: Int = 20,
    val sessionGroupPrefix: String = "group",
    val sessionGroupNumber: Int = 20,

    val protocol: String? = "protocol",

    val random: RandomGenerator? = null,
    val oneOf: OneOfGenerator? = null,
): IMessageGeneratorSettings

internal interface IDataGenerator {
    val directions: Set<Direction>
        get() = DIRECTIONS

    fun nextByteString(direction: Direction): ByteString
    fun nextByteArray(direction: Direction): ByteArray

    companion object {
        val DIRECTIONS = setOf(FIRST, SECOND)
    }
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class RandomGenerator(
    private val messageSize: Int = 256
): IDataGenerator {
    override fun nextByteString(direction: Direction): ByteString = UnsafeByteOperations.unsafeWrap(nextByteArray(direction))
    override fun nextByteArray(direction: Direction): ByteArray = ByteArray(messageSize).apply(RANDOM::nextBytes)
}

class MessageExamples(
    messages: List<String> = listOf(
        "8=FIXT.1.1\u00019=5\u000135=D\u000110=111\u0001"
    ),
    base64s: List<String> = listOf(
        Base64.getEncoder().encodeToString("8=FIXT.1.1\u00019=5\u000135=D\u000110=111\u0001".toByteArray())
    )
) {
    init {
        require(messages.isNotEmpty() || base64s.isNotEmpty()) {
            "'messages' or 'base64s' options should be filled"
        }
    }

    val byteStrings: List<ByteString> = base64s.asSequence()
        .map(Base64.getDecoder()::decode)
        .plus(messages.asSequence()
            .map(String::toByteArray))
        .map(UnsafeByteOperations::unsafeWrap)
        .toList()

    val byteArrays: List<ByteArray> = base64s.asSequence()
        .map(Base64.getDecoder()::decode)
        .plus(messages.asSequence()
            .map(String::toByteArray))
        .toList()
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class OneOfGenerator(
    private val directionToExamples: Map<Direction, MessageExamples>
): IDataGenerator {
    override fun nextByteString(direction: Direction): ByteString =
        directionToExamples[direction]?.byteStrings?.random() ?: error("$direction direction is unsupported")
    override fun nextByteArray(direction: Direction): ByteArray =
        directionToExamples[direction]?.byteArrays?.random() ?: error("$direction direction is unsupported")
}
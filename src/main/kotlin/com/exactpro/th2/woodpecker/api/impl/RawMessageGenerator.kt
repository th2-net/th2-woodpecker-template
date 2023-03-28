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
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoDirection
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoDirection.INCOMING
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoDirection.OUTGOING
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoGroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoMessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoMessageId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.demo.DemoRawMessage
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import com.exactpro.th2.woodpecker.api.impl.RawMessageGenerator.Companion.RANDOM
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils.isNotBlank
import java.nio.charset.Charset
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong

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
            val direction = DIRECTIONS.random()
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
                    this.body = dataGenerator.nextByteString()
                }
            }.build())
        }

    }.build()

    override fun onNextDemo(size: Int): DemoGroupBatch {
        val group = sessionGroups.random()
        return DemoGroupBatch(
            settings.bookName,
            group.name,
            generateSequence {
                val alias = group.aliases.random()
                val direction = DEMO_DIRECTIONS.random()
                DemoMessageGroup(
                    listOf(
                        DemoRawMessage(
                            DemoMessageId(
                                settings.bookName,
                                group.name,
                                alias.name,
                                direction,
                                alias.next(direction),
                                timestamp = Instant.now()
                            ),
                            protocol = settings.protocol ?: "",
                            body = dataGenerator.nextByteArray()
                        )
                    )
                )
            }.take(size).toList()
        )
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {  }
        private val DIRECTIONS = listOf(FIRST, SECOND)
        private val DEMO_DIRECTIONS = listOf(INCOMING, OUTGOING)
        internal val RANDOM = Random()
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
    private val demoSequences: Map<DemoDirection, AtomicLong>
    init {
        sequences = EnumMap<Direction, AtomicLong>(Direction::class.java).apply {
            Direction.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
        demoSequences = EnumMap<DemoDirection, AtomicLong>(DemoDirection::class.java).apply {
            DemoDirection.values().forEach {
                put(it, AtomicLong(System.currentTimeMillis() * 1_000_000L))
            }
        }
    }

    fun next(direction: Direction): Long = sequences[direction]?.incrementAndGet() ?: error("Sequence for the $direction direction isn't found")
    fun next(direction: DemoDirection): Long = demoSequences[direction]?.incrementAndGet() ?: error("Sequence for the $direction direction isn't found")
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
    fun nextByteString(): ByteString
    fun nextByteArray(): ByteArray
}

class RandomGenerator(
    private val messageSize: Int = 256
): IDataGenerator {
    override fun nextByteString(): ByteString = UnsafeByteOperations.unsafeWrap(nextByteArray())
    override fun nextByteArray(): ByteArray = ByteArray(messageSize).apply(RANDOM::nextBytes)
}

class OneOfGenerator(
    @Suppress("MemberVisibilityCanBePrivate") // This is setting option
    val messages: List<String> = listOf(
        "8=FIXT.1.1\u00019=5\u000135=D\u000110=111\u0001"
    )
): IDataGenerator {
    private val byteStrings: List<ByteString> = messages.run {
        map { UnsafeByteOperations.unsafeWrap(it.toByteArray(Charset.defaultCharset())) }
    }
    private val byteArrays: List<ByteArray> = messages.run {
        map { it.toByteArray(Charset.defaultCharset()) }
    }
    init {
        require(messages.isNotEmpty()) {
            "'messages' option can not be empty"
        }
    }
    override fun nextByteString(): ByteString = byteStrings.random()
    override fun nextByteArray(): ByteArray = byteArrays.random()
}
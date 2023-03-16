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

import com.exactpro.th2.common.grpc.Direction.FIRST
import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import com.exactpro.th2.woodpecker.api.impl.RawMessageGenerator.Companion.RANDOM
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import org.apache.commons.lang3.StringUtils.isNotBlank
import java.nio.charset.Charset
import java.time.Instant
import java.util.*

class RawMessageGenerator(settings: RawMessageGeneratorSettings) : IMessageGenerator<RawMessageGeneratorSettings> {
    private val sessionGroups = (1..settings.sessionGroupNumber).map { num ->
        SessionGroup(
            "${settings.sessionGroupPrefix}_$num",
            (1..settings.sessionAliasNumber).map { "${settings.sessionAliasPrefix}_${num}_$it" }
        )
    }

    private val builder = MessageGroup.newBuilder().apply {
        addMessagesBuilder().rawMessageBuilder.apply {
            metadataBuilder.apply {
                idBuilder.apply {

                    sequence = System.currentTimeMillis() * 1_000_000L
                }
                if (isNotBlank(settings.protocol)) {
                    protocol = settings.protocol
                }
            }
        }
    }

    private val dataGenerator: IDataGenerator = settings.random ?: settings.oneOf ?: error("Neither for data generators is specified")

    override fun onNext(): MessageGroup = builder.apply {
        val sessionGroup = sessionGroups.random()

        getMessagesBuilder(0).rawMessageBuilder.run {
            metadataBuilder.apply {
                idBuilder.apply {
                    connectionIdBuilder.apply {
                        this.sessionGroup = sessionGroup.name
                        this.sessionAlias = sessionGroup.aliases.random()
                    }
                    timestamp = Instant.now().toTimestamp()
                    sequence += 1
                }
                direction = DIRECTIONS.random()
            }

            body = dataGenerator.next()
        }
    }.build()
    companion object {
        internal val RANDOM = Random()
        private val DIRECTIONS = listOf(FIRST, SECOND)
    }
}

class SessionGroup(
    val name: String,
    val aliases: List<String>
)

class RawMessageGeneratorSettings(
    val sessionAliasPrefix: String = "session",
    val sessionAliasNumber: Int = 1,
    val sessionGroupPrefix: String = "group",
    val sessionGroupNumber: Int = 1,

    val protocol: String? = "protocol",

    val random: RandomGenerator? = null,
    val oneOf: OneOfGenerator? = null,
): IMessageGeneratorSettings

internal interface IDataGenerator {
    fun next(): ByteString
}

class RandomGenerator(
    private val messageSize: Int = 256
): IDataGenerator {
    override fun next(): ByteString = UnsafeByteOperations.unsafeWrap(ByteArray(messageSize).apply(RANDOM::nextBytes))
}

class OneOfGenerator(
    val messages: List<String> = listOf(
        "8=FIXT.1.1\u00019=5\u000135=D\u000110=111\u0001"
    )
): IDataGenerator {
    private val arrays: List<ByteString> = messages.run {
        require(isNotEmpty()) {
            "'messages' option can not be empty"
        }
        map { UnsafeByteOperations.unsafeWrap(it.toByteArray(Charset.defaultCharset())) }
    }
    override fun next(): ByteString = arrays.random()
}
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
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import java.util.*

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

interface IDataGenerator {
    val directions: Set<Direction>
        get() = DIRECTIONS

    fun nextByteString(direction: Direction): ByteString
    fun nextByteArray(direction: Direction): ByteArray

    companion object {
        val DIRECTIONS = setOf(Direction.FIRST, Direction.SECOND)
    }
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class RandomGenerator(
    private val messageSize: Int = 256
): IDataGenerator {
    override fun nextByteString(direction: Direction): ByteString = UnsafeByteOperations.unsafeWrap(nextByteArray(direction))
    override fun nextByteArray(direction: Direction): ByteArray = ByteArray(messageSize).apply(RawMessageGenerator.RANDOM::nextBytes)
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
    override val directions: Set<Direction> = directionToExamples.keys
    override fun nextByteString(direction: Direction): ByteString =
        directionToExamples[direction]?.byteStrings?.random() ?: error("$direction direction is unsupported")
    override fun nextByteArray(direction: Direction): ByteArray =
        directionToExamples[direction]?.byteArrays?.random() ?: error("$direction direction is unsupported")
}
/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import java.time.Instant

class MessageGenerator(settings: MessageGeneratorSettings) : IMessageGenerator {
    private val builder = MessageGroup.newBuilder().apply {
        addMessagesBuilder().message = settings.fields.toProtoBuilder().apply {
            messageType = settings.messageType
            sessionAlias = settings.sessionAlias
            direction = SECOND
            sequence = System.currentTimeMillis() * 1_000_000L

            metadataBuilder.apply {
                protocol = settings.protocol
                putAllProperties(settings.properties)
            }
        }.build()
    }

    override fun onNext(): MessageGroup = builder.apply {
        getMessagesBuilder(0).messageBuilder.run {
            metadataBuilder.timestamp = Instant.now().toTimestamp()
            sequence += 1
        }
    }.build()
}

class MessageGeneratorSettings : IMessageGeneratorSettings {
    val messageType: String = "type"
    val protocol: String = "protocol"
    val sessionAlias: String = "session"
    val properties: Map<String, String> = mapOf()
    val fields: Map<String, Any?> = mapOf()
}
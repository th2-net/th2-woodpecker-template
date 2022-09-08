/*
 * Copyright 2021-2022 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.getMessage
import com.exactpro.th2.common.value.toValue
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import com.exactpro.th2.woodpecker.api.impl.MessageTypes.EXECUTION_REPORT_TYPE
import com.exactpro.th2.woodpecker.api.impl.MessageTypes.NEW_ORDER_SINGLE_TYPE
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class MessageGenerator(settings: GeneratorSettings) : IMessageGenerator<GeneratorSettings> {

    private val generators: () -> Message.Builder

    init {
        check(settings.templates.keys.containsAll(MessageTypes.values().toList())) {
            "Could please describe structure in messages setting for ${
                MessageTypes.values().toSet() - settings.templates.keys
            } message types"
        }

        generators = settings.templates.asSequence()
            .sortedBy { it.key }
            .map { entry ->
                val messageSettings = entry.value
                val messageType = entry.key
                messageSettings.fields.toProtoBuilder().apply {
                    metadataBuilder.apply {
                        this.messageType = messageType.type
                        idBuilder.apply {
                            this.direction = messageSettings.direction
                            connectionIdBuilder.apply {
                                sessionAlias = settings.sessionAlias
                            }
                        }
                    }
                }
            }.toList()
            .toGenerator()
    }

    @Volatile
    private var clOrdId: Value = Value.getDefaultInstance()

    override fun onNext(): MessageGroup = MessageGroup.newBuilder().apply {
        addMessages(AnyMessage.newBuilder().apply {
            message = generators.invoke().apply {
                metadataBuilder.apply {
                    idBuilder.apply {
                        sequence = SEQUENCE_COUNTER.incrementAndGet()
                    }
                    timestampBuilder.apply {
                        Instant.now().also { now ->
                            seconds = now.epochSecond
                            nanos = now.nano
                        }
                    }
                }

                get(HEADER_FIELD)?.getMessage()?.toBuilder() ?: Message.newBuilder().also { headerBuilder ->
                    headerBuilder.putFields(SENDING_TIME_FIELD, currentDateTime().toValue())
                }

                when(messageType) {
                    NEW_ORDER_SINGLE_TYPE.type -> {
                        clOrdId = "CL${SEQUENCE_COUNTER.get()}".toValue()
                        putFields(CL_ORD_ID_FIELD, clOrdId)
                        putFields(NO_PARTY_IDS_FIELD, generateNoPartyIDs(NEW_ORDER_SINGLE_TYPE))
                    }
                    EXECUTION_REPORT_TYPE.type -> {
                        putFields(CL_ORD_ID_FIELD, clOrdId)
                        putFields(ORD_ID_FIELD, clOrdId)
                        putFields(TRANSACT_TIME_FIELD, currentDateTime().toValue())
                        putFields(NO_PARTY_IDS_FIELD, generateNoPartyIDs(EXECUTION_REPORT_TYPE))
                    }
                    else -> error("Unsupported message type $messageType")
                }
            }.build()
        })
    }.build()

    private fun generateParty(id: String, source: String, role: String): Value {
        return Message.newBuilder().apply {
            putFields(PARTY_ID_FIELD, id.toValue())
            putFields(PARTY_ID_SOURCE_FIELD, source.toValue())
            putFields(PARTY_ROLE_FIELD, role.toValue())
        }.toValue()
    }

    private fun generateNoPartyIDs(messageType: MessageTypes): Value {
        val noPartyIDs = ListValue.newBuilder().add(generateParty(TRADER, "D", "76"))

        if (messageType == NEW_ORDER_SINGLE_TYPE) {
            noPartyIDs.add(generateParty("0", "P", "3"))
                .add(generateParty("0", "P", "122"))
                .add(generateParty("3", "P", "12"))
        }

        return noPartyIDs.toValue()
    }

    companion object {
        private val SEQUENCE_COUNTER = AtomicLong(System.nanoTime())

        const val PARTY_ID_FIELD = "PartyID"
        const val PARTY_ID_SOURCE_FIELD = "PartyIDSource"
        const val PARTY_ROLE_FIELD = "PartyRole"

        const val NO_PARTY_IDS_FIELD = "NoPartyIDs"
        const val TRANSACT_TIME_FIELD = "TransactTime"
        const val SENDING_TIME_FIELD = "SendingTime"
        const val CL_ORD_ID_FIELD = "ClOrdID"
        const val ORD_ID_FIELD = "OrderID"

        const val HEADER_FIELD = "header"

        const val TRADER = "trader"

        fun currentDateTime(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC))

        fun <T> List<T>.toGenerator(): () -> T {
            var next = -1
            return {
                if(next == lastIndex) next = 0 else next++
                get(next)
            }
        }
    }
}
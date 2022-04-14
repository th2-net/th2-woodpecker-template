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
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.message.set
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.toValue
import com.exactpro.th2.woodpecker.api.IMessageGenerator
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID.randomUUID

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

    private val securityIdSource = (('1'..'9') + ('A'..'L')).toGenerator()
    private val ordType = (('1'..'9') + ('A'..'Q')).toGenerator()
    private val accountType = ('1'..'8').toList().toGenerator()
    private val orderCapacity = listOf('A', 'G', 'I', 'P', 'R', 'W').toGenerator()
    private val side = (('1'..'9') + ('A'..'G')).toGenerator()

    private val transactTimeValue: LocalDateTime?
    private val transactTimeSecondsShift: Long?

    init {
        val generators = settings.generators

        val genTransactTime = generators["TransactTime"] as? Map<*, *>
        transactTimeValue = genTransactTime?.get("value")?.toString()?.run(LocalDateTime::parse)
        transactTimeSecondsShift = genTransactTime?.get("current")?.toString()?.toLong()
    }

    private fun getClOrdId() = randomUUID()
    private fun getSecurityId() = randomUUID()
    private fun getSecurityIdSource() = securityIdSource.invoke()
    private fun getOrderQty() = (1..10).random()
    private fun getPrice() = (1..10).random()
    private fun getOrdType() = ordType.invoke()
    private fun getAccountType() = accountType.invoke()
    private fun getOrderCapacity() = orderCapacity.invoke()
    private fun getSide() = side.invoke()

    private fun getParty(id: String, source: String, role: String): Value {
        return Message.newBuilder().putFields("PartyID", id.toValue()).putFields("PartyIDSource", source.toValue())
            .putFields("PartyRole", role.toValue()).toValue()
    }

    private fun generateListNoPartyID(): ListValue.Builder {
        return ListValue.newBuilder()
            .add(getParty("id", "D", "76"))
            .add(getParty("0", "P", "3"))
            .add(getParty("0", "P", "122"))
            .add(getParty("3", "P", "12"))
    }

    private fun getTransactTime() = when {
        transactTimeValue != null -> transactTimeValue
        transactTimeSecondsShift != null -> LocalDateTime.now().plusSeconds(transactTimeSecondsShift)
        else -> LocalDateTime.now()
    }

    override fun onNext(): MessageGroup = builder.apply {
        getMessagesBuilder(0).messageBuilder.run {
            set("SecurityID", getSecurityId())
            set("SecurityIDSource", getSecurityIdSource())
            set("OrdType", getOrdType())
            set("AccountType", getAccountType())
            set("OrderCapacity", getOrderCapacity())
            set("OrderQty", getOrderQty())
            set("Price", getPrice())
            set("ClOrdID", getClOrdId())
            set("SecondaryClOrdID", getClOrdId())
            set("Side", getSide())
            // set("TimeInForce", 0)
            set("TransactTime", getTransactTime())
            set("TradingParty", generateListNoPartyID())
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
    val generators: Map<String, Any?> = mapOf()
    val fields: Map<String, Any?> = mapOf()
}

private fun <T> List<T>.toGenerator(): () -> T {
    var next = -1
    return {
        if(next == lastIndex) next = 0 else next++
        get(next)
    }
}
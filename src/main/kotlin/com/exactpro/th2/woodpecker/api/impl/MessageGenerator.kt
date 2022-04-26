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
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.hasField
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
import kotlin.random.Random

class MessageGenerator(settings: MessageGeneratorSettings) : IMessageGenerator {
    private val builder = MessageGroup.newBuilder().apply {
        addMessagesBuilder().message = settings.fields.toProtoBuilder().apply {
            direction = SECOND
            sequence = System.currentTimeMillis() * 1_000_000L

            metadataBuilder.apply {
                protocol = settings.protocol
                putAllProperties(settings.properties)
            }
        }.build()
    }

    private val messageTypeGenerator: () -> String
    private val newOrderSingleType = "NewOrderSingle"
    private val securityIDGenerator: () -> String
    private val securityIdSource = listOf('8').toGeneratorRnd() // ('1'..'9') + ('A'..'L')
    private val traderGenerator: () -> Trader
    private val ordType = listOf('2').toGeneratorRnd() // ('1'..'9') + ('A'..'Q')
    private val accountType = listOf('1').toGeneratorRnd() // ('1'..'8')
    private val orderCapacity = listOf('A').toGeneratorRnd() // listOf('A', 'G', 'I', 'P', 'R', 'W')
    private val side = listOf('1', '2').toGeneratorRnd() // ('1'..'9') + ('A'..'G')
    private val quantity: Int?
    private val price: Int?
    private val transactTimeValue: LocalDateTime?
    private val transactTimeSecondsShift: Long?

    private var orderIDCache: MutableList<String> = mutableListOf()

    init {
        val generators = settings.generators

        messageTypeGenerator = ((generators["MessageType"] as? List<*>)?.filterIsInstance<String>() ?: listOf(newOrderSingleType)).toGeneratorRnd()

        securityIDGenerator = ((generators["SecurityID"] as? List<*>)?.filterIsInstance<String>()
            ?: listOf(randomUUID().toString()))
            .toGeneratorRnd()

        traderGenerator = arrayListOf<Trader>().run {
            (generators["Traders"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.forEach { item -> item
                    .asSequence()
                    .filter { (key, value) -> key is String && value is String }
                    .associate { (key, value) -> key as String to value as String }
                    .let {
                        if (it.containsKey("Trader") && it.containsKey("SessionAlias")) {
                            add(Trader(it["Trader"]!!, it["SessionAlias"]!!))
                        }
                    }
                } ?: add(Trader("woodpecker", "woodpecker_session_alias"))

            toGeneratorRnd()
        }

        quantity = generators["OrderQty"] as? Int
        price = generators["Price"] as? Int

        val transactTime = generators["TransactTime"] as? Map<*, *>
        transactTimeValue = transactTime?.get("value")?.toString()?.run(LocalDateTime::parse)
        transactTimeSecondsShift = transactTime?.get("current")?.toString()?.toLong()
    }

    private fun getMessageType() = messageTypeGenerator.invoke()
    private fun getClientOrderID() = randomUUID().toString()
    private fun getTrader() = traderGenerator.invoke()
    private fun getSecurityId() = securityIDGenerator.invoke()
    private fun getSecurityIDSource() = securityIdSource.invoke()
    private fun getOrderQuantity() = when {
        quantity != null -> quantity
        else -> (10..100).random()
    }
    private fun getPrice() = when {
        price != null -> price
        else -> (52..57).random()
    }
    private fun getOrderType() = ordType.invoke()
    private fun getAccountType() = accountType.invoke()
    private fun getOrderCapacity() = orderCapacity.invoke()
    private fun getSide() = side.invoke()

    private fun getParty(id: String, source: String, role: String): Value {
        return Message.newBuilder()
            .putFields("PartyID", id.toValue())
            .putFields("PartyIDSource", source.toValue())
            .putFields("PartyRole", role.toValue()).toValue()
    }

    private fun generateNoPartyIDs(messageType: String, trader: String): Message.Builder {
        val noPartyIDs = ListValue.newBuilder().add(getParty(trader, "D", "76"))

        if (messageType == newOrderSingleType)
            noPartyIDs.add(getParty("0", "P", "3"))
                .add(getParty("0", "P", "122"))
                .add(getParty("3", "P", "12"))

        return Message.newBuilder().putFields("NoPartyIDs", noPartyIDs.toValue())
    }

    private fun getTransactTime() = when {
        transactTimeValue != null -> transactTimeValue
        transactTimeSecondsShift != null -> LocalDateTime.now().plusSeconds(transactTimeSecondsShift)
        else -> LocalDateTime.now()
    }

    override fun onStart() {
        orderIDCache.clear()
    }

    override fun onResponse(message: MessageGroup) {
        val incoming = message.getMessages(0).message
        if (incoming.messageType == "ExecutionReport" && incoming.hasField("OrderID"))
            orderIDCache.add(incoming["OrderID"]!!.simpleValue)
    }

    override fun onNext(): MessageGroup = builder.apply {
        var type = getMessageType()
        var id: String? = null

        if (orderIDCache.isEmpty())
            type = newOrderSingleType

        if (type != newOrderSingleType)
            id = orderIDCache.popRandom()

        val trader = getTrader()

        getMessagesBuilder(0).messageBuilder.run {
            set("SecurityID", getSecurityId())
            set("SecurityIDSource", getSecurityIDSource())
            set("OrdType", getOrderType())
            set("AccountType", getAccountType())
            set("OrderCapacity", getOrderCapacity())
            set("OrderQty", getOrderQuantity())
            set("Price", getPrice())
            set("ClOrdID", getClientOrderID())
            if (type != newOrderSingleType) set("OrderID", id)
            set("Side", getSide())
            set("TransactTime", getTransactTime())
            set("TradingParty", generateNoPartyIDs(type, trader.name))
            messageType = type
            sessionAlias = trader.sessionAlias
            metadataBuilder.timestamp = Instant.now().toTimestamp()
            sequence += 1
        }
    }.build()
}

class MessageGeneratorSettings : IMessageGeneratorSettings {
    val messageType: List<String> = listOf("type")
    val protocol: String = "protocol"
    val properties: Map<String, String> = mapOf()
    val generators: Map<String, Any?> = mapOf()
    val fields: Map<String, Any?> = mapOf()
}

private data class Trader(val name: String, val sessionAlias: String)

private fun <T> MutableList<T>.popRandom(): T = removeAt(Random.nextInt(0, size))

private fun <T> List<T>.toGenerator(): () -> T {
    var next = -1
    return {
        if(next == lastIndex) next = 0 else next++
        get(next)
    }
}

private fun <T> List<T>.toGeneratorRnd(): () -> T {
    return { get(Random.nextInt(0, size)) }
}

private fun Map<*, *>.toGeneratorMap(): Map<String, () -> String> = asSequence()
    .filter { (key, value) -> key is String && value is List<*> }
    .associate { (key, value) -> key as String to (value as List<*>).filterIsInstance<String>().toGenerator() }
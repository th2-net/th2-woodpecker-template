package com.exactpro.th2.woodpecker.api.impl.event

import com.exactpro.th2.common.event.Event
import java.util.*

class Generator {
    private val random: Random = Random()

    fun generateStatus(failureRate: Int): Event.Status {
        return if (random.nextInt(1000) > failureRate) Event.Status.PASSED else Event.Status.FAILED
    }

    fun generateStringSizeOf(length: Int): String {
        return ByteArray(length).apply(random::nextBytes).toString()
    }

    fun generateIdString(): String {
        return UUID.randomUUID().toString()
    }
    fun createEventTree(treeDepth: Int, childCount: Int): EventTreeNode {
        val rootNode = EventTreeNode(generateIdString(), null)
        repeat(treeDepth) { EventTreeNode.growTree(rootNode, childCount) }
        return rootNode
    }
}
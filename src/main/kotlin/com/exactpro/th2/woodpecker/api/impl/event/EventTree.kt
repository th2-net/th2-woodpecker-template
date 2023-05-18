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

package com.exactpro.th2.woodpecker.api.impl.event

import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.woodpecker.api.impl.event.provider.EventProvider

class EventTreeNode(
    val event: Event,
    val children: MutableList<EventTreeNode> = mutableListOf()
) {
    fun addChildren(childCount: Int, eventProvider: EventProvider) {
        repeat(childCount) {
            children.add(
                EventTreeNode(eventProvider.eventWithParentId(this.event.id))
            )
        }
    }

    companion object {
        fun growTree(node: EventTreeNode, childCount: Int, eventProvider: EventProvider) {
            if (node.children.isEmpty()) {
                node.addChildren(childCount, eventProvider)
            } else {
                node.children.forEach {
                    growTree(it, childCount, eventProvider)
                }
            }
        }

        fun traverseBreadthFirst(
            rootNode: EventTreeNode,
            action: (EventTreeNode) -> Unit
        ) {
            val queue = ArrayDeque<EventTreeNode>()
            queue.addFirst(rootNode)

            while (queue.isNotEmpty()) {
                val currentNode = queue.removeFirst()

                action.invoke(currentNode)

                for (childNode in currentNode.children) {
                    queue.addLast(childNode)
                }
            }
        }

        fun findLeaves(
            node: EventTreeNode,
            action: (EventTreeNode) -> Unit
        ) {
            if (node.children.isEmpty()) {
                action.invoke(node)
            } else {
                node.children.forEach {
                    findLeaves(it, action)
                }
            }
        }
    }
}
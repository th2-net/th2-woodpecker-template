package com.exactpro.th2.woodpecker.api.impl.event

class EventTreeNode(
    val id: String,
    val parentId: String?,
    val children: MutableList<EventTreeNode> = mutableListOf()
) {
    fun generateChildren(childCount: Int) {
        repeat(childCount) {
            children.add(
                EventTreeNode(randomId(), this.id)
            )
        }
    }

    companion object {
        val randomId: () -> String = { Generator().generateIdString() }

        fun growTree(node: EventTreeNode, childCount: Int) {
            if (node.children.isEmpty()) {
                node.generateChildren(childCount)
            } else {
                node.children.forEach {
                    growTree(it, childCount)
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
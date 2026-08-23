package com.fourerk.codexpet.task

/**
 * Normalizes a full NotificationListenerService snapshot.
 *
 * Android/ChatGPT may expose both group summaries and children. A summary remains useful when it is
 * the only visible member, but showing it beside real children creates duplicates and inaccurate
 * chat navigation. Logical duplicates are also collapsed to their newest notification.
 */
object NotificationSetReducer {
    fun reduce(tasks: List<CodexTask>): List<CodexTask> {
        if (tasks.isEmpty()) return emptyList()

        val byGroup = tasks
            .filter { !it.groupKey.isNullOrBlank() }
            .groupBy { it.groupKey }

        val withoutRedundantSummaries = tasks.filter { task ->
            if (!task.isGroupSummary || task.groupKey.isNullOrBlank()) return@filter true
            val siblings = byGroup[task.groupKey].orEmpty()
            siblings.none { sibling ->
                sibling.sourceNotificationKey != task.sourceNotificationKey &&
                    !sibling.isGroupSummary &&
                    sibling.isDisplayTask()
            }
        }

        return withoutRedundantSummaries
            .groupBy(CodexTask::id)
            .values
            .mapNotNull { duplicates ->
                duplicates.maxWithOrNull(
                    compareBy<CodexTask> { !it.isGroupSummary }
                        .thenBy { it.updatedAt },
                )
            }
            .sortedByDescending(CodexTask::updatedAt)
    }
}

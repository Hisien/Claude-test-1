package com.scrollmate.core.rules

import com.scrollmate.core.model.RuleId
import java.time.LocalDate

/**
 * Idempotency keys: local date + rule id + threshold (+ optional qualifier such as a package
 * or a repeat counter). A reminder with an existing key is never sent again, so restarts and
 * time-zone changes cannot produce duplicates.
 */
object NotificationKeys {

    fun key(date: LocalDate, ruleId: RuleId, threshold: Int? = null, qualifier: String? = null): String =
        buildString {
            append(date.toString())
            append('|').append(ruleId.name)
            append('|').append(threshold?.toString() ?: "-")
            if (qualifier != null) append('|').append(qualifier)
        }
}

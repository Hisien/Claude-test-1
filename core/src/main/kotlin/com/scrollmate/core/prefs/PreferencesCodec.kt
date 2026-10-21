package com.scrollmate.core.prefs

import com.scrollmate.core.model.AppLimit
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.TimeWindow
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalTime

/**
 * Small text codec for the structured parts of the preferences, so they fit into plain
 * DataStore string keys without a serialization library. Malformed parts are skipped, never thrown.
 */
object PreferencesCodec {

    private const val CHARSET = "UTF-8"

    fun encodeThresholds(values: List<Int>): String = values.joinToString(",")

    /** Returns null for a missing value (use defaults); an empty string means "no thresholds". */
    fun decodeThresholds(raw: String?): List<Int>? {
        if (raw == null) return null
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    fun encodeAppLimits(limits: List<AppLimit>): String =
        limits.joinToString(";") { "${enc(it.packageName)}=${it.limit.toMinutes()}" }

    fun decodeAppLimits(raw: String?): List<AppLimit> =
        raw.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { part ->
            val idx = part.lastIndexOf('=')
            if (idx <= 0) return@mapNotNull null
            val minutes = part.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
            if (minutes <= 0) return@mapNotNull null
            AppLimit(dec(part.substring(0, idx)), Duration.ofMinutes(minutes))
        }

    fun encodeTimeWindows(windows: List<TimeWindow>): String =
        windows.joinToString(";") {
            listOf(enc(it.id), enc(it.label), it.start.toString(), it.end.toString(), if (it.enabled) "1" else "0")
                .joinToString("|")
        }

    fun decodeTimeWindows(raw: String?): List<TimeWindow> =
        raw.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { part ->
            val f = part.split('|')
            if (f.size != 5) return@mapNotNull null
            try {
                TimeWindow(dec(f[0]), dec(f[1]), LocalTime.parse(f[2]), LocalTime.parse(f[3]), f[4] == "1")
            } catch (e: Exception) {
                null
            }
        }

    fun encodeRuleIds(ids: Set<RuleId>): String = ids.joinToString(",") { it.name }

    fun decodeRuleIds(raw: String?): Set<RuleId> =
        raw.orEmpty().split(',').mapNotNull { name -> RuleId.values().firstOrNull { it.name == name.trim() } }.toSet()

    fun encodeStringSet(values: Set<String>): String = values.joinToString(";") { enc(it) }

    fun decodeStringSet(raw: String?): Set<String> =
        raw.orEmpty().split(';').filter { it.isNotBlank() }.map { dec(it) }.toSet()

    private fun enc(s: String): String = URLEncoder.encode(s, CHARSET)
    private fun dec(s: String): String = URLDecoder.decode(s, CHARSET)
}

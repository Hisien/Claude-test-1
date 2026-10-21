package com.scrollmate.core.demo

import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import java.time.Duration
import java.time.LocalDate

/**
 * Deterministic demo data set: 7 days, 5 fictional apps. The same "today" always produces the
 * same numbers, so a presentation is reproducible. Package names use a "demo." prefix so they
 * can never be confused with installed apps.
 */
object DemoData {

    data class DemoApp(val packageName: String, val label: String)

    val APPS: List<DemoApp> = listOf(
        DemoApp("demo.shortvideo", "Kurzvideos"),
        DemoApp("demo.socialfeed", "Social Feed"),
        DemoApp("demo.photofeed", "Foto-Feed"),
        DemoApp("demo.news", "Nachrichten"),
        DemoApp("demo.messenger", "Messenger"),
    )

    /** Minutes per app, index 0 = six days ago, index 6 = today. */
    private val MINUTES: List<IntArray> = listOf(
        intArrayOf(52, 34, 18, 12, 21), // 137
        intArrayOf(38, 30, 15, 10, 19), // 112
        intArrayOf(61, 41, 22, 14, 25), // 163
        intArrayOf(29, 22, 12, 9, 17), //  89
        intArrayOf(44, 28, 16, 13, 20), // 121
        intArrayOf(33, 25, 11, 8, 18), //   95
        intArrayOf(31, 22, 13, 7, 14), //   87 (today, ~73 % of the default 120 min)
    )

    const val DAYS = 7

    fun entries(today: LocalDate): List<AppUsageEntry> =
        MINUTES.flatMapIndexed { dayIndex, perApp ->
            val date = today.minusDays((DAYS - 1 - dayIndex).toLong())
            APPS.mapIndexed { appIndex, app ->
                AppUsageEntry(app.packageName, date, Duration.ofMinutes(perApp[appIndex].toLong()), UsageSource.DEMO)
            }
        }

    fun labelFor(packageName: String): String? = APPS.firstOrNull { it.packageName == packageName }?.label

    fun isDemoPackage(packageName: String): Boolean = packageName.startsWith("demo.")
}

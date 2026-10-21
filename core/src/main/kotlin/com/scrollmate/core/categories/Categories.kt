package com.scrollmate.core.categories

import java.time.Duration

enum class AppCategory { SOCIAL, ENTERTAINMENT, WORK_LEARNING, NEWS, OTHER }

/** Platform category hint (Android: ApplicationInfo.category), mapped in :app. */
enum class SystemCategoryHint { SOCIAL, VIDEO, AUDIO, GAME, IMAGE, NEWS, PRODUCTIVITY, MAPS, NONE }

data class CategoryShare(
    val category: AppCategory,
    val duration: Duration,
    /** Share of the total, 0.0–1.0. */
    val fraction: Double,
    val packages: List<String>,
) {
    val percent: Int get() = Math.round(fraction * 100).toInt()
}

/**
 * Assigns apps to categories: first a list of well-known apps, then the category the app
 * declares itself, otherwise OTHER.
 */
object CategoryClassifier {

    private val KNOWN: Map<String, AppCategory> = buildMap {
        listOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.whatsapp",
            "com.facebook.katana", "com.facebook.orca", "com.snapchat.android", "com.twitter.android",
            "com.reddit.frontpage", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord",
            "com.pinterest", "com.linkedin.android", "com.bereal.ft", "com.tumblr",
            "demo.socialfeed", "demo.photofeed", "demo.messenger",
        ).forEach { put(it, AppCategory.SOCIAL) }
        listOf(
            "com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music", "tv.twitch.android.app",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.google.android.apps.youtube.music",
            "demo.shortvideo",
        ).forEach { put(it, AppCategory.ENTERTAINMENT) }
        listOf(
            "notion.id", "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.calendar",
            "com.microsoft.teams", "com.Slack", "com.google.android.apps.docs", "com.google.android.keep",
            "com.duolingo", "com.microsoft.office.word", "com.todoist", "com.google.android.apps.classroom",
        ).forEach { put(it, AppCategory.WORK_LEARNING) }
        listOf(
            "de.spiegel.android.app.spon", "de.tagesschau", "flipboard.app", "com.google.android.apps.magazines",
            "com.nytimes.android", "bbc.mobile.news.ww", "de.zeit.online.android", "de.cellular.focus",
            "demo.news",
        ).forEach { put(it, AppCategory.NEWS) }
    }

    fun classify(packageName: String, hint: SystemCategoryHint? = null): AppCategory {
        KNOWN[packageName]?.let { return it }
        return when (hint) {
            SystemCategoryHint.SOCIAL -> AppCategory.SOCIAL
            SystemCategoryHint.VIDEO, SystemCategoryHint.AUDIO, SystemCategoryHint.GAME, SystemCategoryHint.IMAGE ->
                AppCategory.ENTERTAINMENT
            SystemCategoryHint.PRODUCTIVITY -> AppCategory.WORK_LEARNING
            SystemCategoryHint.NEWS -> AppCategory.NEWS
            SystemCategoryHint.MAPS, SystemCategoryHint.NONE, null -> AppCategory.OTHER
        }
    }

    /** Sums usage per category, largest first; categories without usage are left out. */
    fun breakdown(
        perApp: Map<String, Duration>,
        hints: Map<String, SystemCategoryHint> = emptyMap(),
    ): List<CategoryShare> {
        val total = perApp.values.fold(Duration.ZERO) { a, d -> a.plus(d) }
        if (total.isZero) return emptyList()
        return perApp.entries
            .filter { !it.value.isZero && !it.value.isNegative }
            .groupBy { classify(it.key, hints[it.key]) }
            .map { (cat, entries) ->
                val sum = entries.fold(Duration.ZERO) { a, e -> a.plus(e.value) }
                CategoryShare(cat, sum, sum.toMillis().toDouble() / total.toMillis(), entries.sortedByDescending { it.value }.map { it.key })
            }
            .sortedByDescending { it.duration }
    }
}

package com.scrollmate.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.scrollmate.core.categories.SystemCategoryHint
import com.scrollmate.core.demo.DemoData

data class LauncherApp(val packageName: String, val label: String)

/**
 * Lists apps with a launcher entry. Works with Android 11+ package visibility thanks to the
 * <queries> entry in the manifest; QUERY_ALL_PACKAGES is not needed.
 */
class InstalledApps(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager
    private val labelCache = HashMap<String, String>()

    fun launcherApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryActivities(intent)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { LauncherApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** ScrollMate itself and home-screen apps never count as scrolling time. */
    fun excludedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return queryActivities(home).map { it.activityInfo.packageName }.toSet() + context.packageName
    }

    fun label(packageName: String): String {
        DemoData.labelFor(packageName)?.let { return it }
        return labelCache.getOrPut(packageName) {
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }

    private val hintCache = HashMap<String, SystemCategoryHint>()

    /** The category an app declares for itself (Android 8+), used for the category analysis. */
    fun categoryHint(packageName: String): SystemCategoryHint = hintCache.getOrPut(packageName) {
        val category = try {
            pm.getApplicationInfo(packageName, 0).category
        } catch (e: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> SystemCategoryHint.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO -> SystemCategoryHint.VIDEO
            ApplicationInfo.CATEGORY_AUDIO -> SystemCategoryHint.AUDIO
            ApplicationInfo.CATEGORY_GAME -> SystemCategoryHint.GAME
            ApplicationInfo.CATEGORY_IMAGE -> SystemCategoryHint.IMAGE
            ApplicationInfo.CATEGORY_NEWS -> SystemCategoryHint.NEWS
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> SystemCategoryHint.PRODUCTIVITY
            ApplicationInfo.CATEGORY_MAPS -> SystemCategoryHint.MAPS
            else -> SystemCategoryHint.NONE
        }
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(intent: Intent) =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
}

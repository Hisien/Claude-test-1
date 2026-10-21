package com.scrollmate.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.DailyGoal
import com.scrollmate.core.model.ReminderSettings
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.prefs.PreferencesCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Duration
import java.time.LocalTime

/** User preferences in Jetpack DataStore. All writes go through [UserPreferences.sanitized]. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { read(it) }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { stored ->
            val next = transform(read(stored)).sanitized()
            write(stored, next)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun read(p: Preferences): UserPreferences {
        val defaults = UserPreferences()
        val defaultReminders = defaults.reminders
        val checkInMinute = p[K.CHECK_IN_MINUTE]
        return UserPreferences(
            onboardingCompleted = p[K.ONBOARDING_DONE] ?: false,
            goal = DailyGoal(
                dailyLimit = p[K.DAILY_LIMIT_MIN]?.let { Duration.ofMinutes(it) } ?: defaults.goal.dailyLimit,
                appLimits = PreferencesCodec.decodeAppLimits(p[K.APP_LIMITS]),
                personalGoal = p[K.PERSONAL_GOAL] ?: "",
            ),
            style = p[K.STYLE]?.let { name -> CoachingStyle.values().firstOrNull { it.name == name } } ?: defaults.style,
            reminders = ReminderSettings(
                thresholdsPercent = PreferencesCodec.decodeThresholds(p[K.THRESHOLDS]) ?: defaultReminders.thresholdsPercent,
                checkInTime = when {
                    checkInMinute == null -> defaultReminders.checkInTime
                    checkInMinute < 0 -> null
                    else -> LocalTime.of(checkInMinute / 60, checkInMinute % 60)
                },
                maxRemindersPerDay = p[K.MAX_REMINDERS] ?: defaultReminders.maxRemindersPerDay,
                strictRepeatInterval = p[K.STRICT_REPEAT_MIN]?.let { Duration.ofMinutes(it) }
                    ?: defaultReminders.strictRepeatInterval,
                disabledRules = PreferencesCodec.decodeRuleIds(p[K.DISABLED_RULES]),
            ),
            timeWindows = PreferencesCodec.decodeTimeWindows(p[K.TIME_WINDOWS]),
            focusDuration = p[K.FOCUS_MIN]?.let { Duration.ofMinutes(it) } ?: defaults.focusDuration,
            demoMode = p[K.DEMO_MODE] ?: false,
            selectedApps = PreferencesCodec.decodeStringSet(p[K.SELECTED_APPS]),
        ).sanitized()
    }

    private fun write(p: MutablePreferences, v: UserPreferences) {
        p[K.ONBOARDING_DONE] = v.onboardingCompleted
        p[K.DAILY_LIMIT_MIN] = v.goal.dailyLimit.toMinutes()
        p[K.APP_LIMITS] = PreferencesCodec.encodeAppLimits(v.goal.appLimits)
        p[K.PERSONAL_GOAL] = v.goal.personalGoal
        p[K.STYLE] = v.style.name
        p[K.THRESHOLDS] = PreferencesCodec.encodeThresholds(v.reminders.thresholdsPercent)
        p[K.CHECK_IN_MINUTE] = v.reminders.checkInTime?.let { it.hour * 60 + it.minute } ?: -1
        p[K.MAX_REMINDERS] = v.reminders.maxRemindersPerDay
        p[K.STRICT_REPEAT_MIN] = v.reminders.strictRepeatInterval.toMinutes()
        p[K.DISABLED_RULES] = PreferencesCodec.encodeRuleIds(v.reminders.disabledRules)
        p[K.TIME_WINDOWS] = PreferencesCodec.encodeTimeWindows(v.timeWindows)
        p[K.FOCUS_MIN] = v.focusDuration.toMinutes()
        p[K.DEMO_MODE] = v.demoMode
        p[K.SELECTED_APPS] = PreferencesCodec.encodeStringSet(v.selectedApps)
    }

    private object K {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val DAILY_LIMIT_MIN = longPreferencesKey("daily_limit_min")
        val APP_LIMITS = stringPreferencesKey("app_limits")
        val PERSONAL_GOAL = stringPreferencesKey("personal_goal")
        val STYLE = stringPreferencesKey("coaching_style")
        val THRESHOLDS = stringPreferencesKey("thresholds")
        val CHECK_IN_MINUTE = intPreferencesKey("check_in_minute")
        val MAX_REMINDERS = intPreferencesKey("max_reminders")
        val STRICT_REPEAT_MIN = longPreferencesKey("strict_repeat_min")
        val DISABLED_RULES = stringPreferencesKey("disabled_rules")
        val TIME_WINDOWS = stringPreferencesKey("time_windows")
        val FOCUS_MIN = longPreferencesKey("focus_min")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SELECTED_APPS = stringPreferencesKey("selected_apps")
    }

    companion object {
        fun create(context: Context): SettingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") }),
        )
    }
}

package com.scrollmate.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scrollmate.core.model.AppLimit
import com.scrollmate.core.model.CoachVoice
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.ThemeMode
import com.scrollmate.core.model.WindDownSettings
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.TimeWindow
import com.scrollmate.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Plain JVM test (no emulator): settings written through DataStore must survive a "restart",
 * simulated by cancelling the first DataStore's scope and opening the same file again.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class SettingsPersistenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun open(file: File): Pair<SettingsRepository, Job> {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        return SettingsRepository(store) to job
    }

    @Test
    fun freshInstallHasDefaults() = runTest {
        val (repo, job) = open(File(tmp.root, "fresh.preferences_pb"))
        val prefs = repo.current()
        assertFalse(prefs.onboardingCompleted)
        assertEquals(UserPreferences().sanitized(), prefs)
        job.cancelAndJoin()
    }

    @Test
    fun settingsSurviveRestart() = runTest {
        val file = File(tmp.root, "settings.preferences_pb")
        val expected = UserPreferences(
            onboardingCompleted = true,
            style = CoachingStyle.STRICT,
            demoMode = true,
            focusDuration = Duration.ofMinutes(25),
            selectedApps = setOf("com.example.feed", "com.example.video"),
            voice = CoachVoice.NEUTRAL,
            themeMode = ThemeMode.DARK,
            windDown = WindDownSettings(
                enabled = true,
                bedtime = LocalTime.of(23, 15),
                wakeTime = LocalTime.of(7, 0),
                leadMinutes = 45,
                blockedApps = setOf("com.instagram.android"),
                pausedForDate = LocalDate.of(2026, 10, 21),
            ),
        ).let { p ->
            p.copy(
                goal = p.goal.copy(
                    dailyLimit = Duration.ofMinutes(75),
                    personalGoal = "Abends weniger scrollen",
                    appLimits = listOf(AppLimit("com.example.feed", Duration.ofMinutes(30))),
                ),
                reminders = p.reminders.copy(
                    thresholdsPercent = listOf(50, 100),
                    checkInTime = LocalTime.of(21, 30),
                    maxRemindersPerDay = 3,
                    strictRepeatInterval = Duration.ofMinutes(40),
                    disabledRules = setOf(RuleId.THRESHOLD_APP),
                ),
                timeWindows = listOf(TimeWindow("w1", "Schlafenszeit", LocalTime.of(22, 0), LocalTime.of(7, 0))),
            )
        }.sanitized()

        val (first, firstJob) = open(file)
        first.update { expected }
        assertEquals(expected, first.current())
        firstJob.cancelAndJoin() // simulated process death

        val (second, secondJob) = open(file)
        assertEquals(expected, second.current())
        secondJob.cancelAndJoin()
    }

    @Test
    fun disabledCheckInIsPersistedAsNull() = runTest {
        val file = File(tmp.root, "checkin.preferences_pb")
        val (first, job1) = open(file)
        first.update { it.copy(reminders = it.reminders.copy(checkInTime = null)) }
        job1.cancelAndJoin()
        val (second, job2) = open(file)
        assertEquals(null, second.current().reminders.checkInTime)
        job2.cancelAndJoin()
    }
}

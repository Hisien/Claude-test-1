package com.scrollmate.core.datasource

import com.scrollmate.core.model.DataSourceState
import com.scrollmate.core.model.UsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataSourceResolverTest {

    @Test
    fun noPermissionNoDemoMeansPermissionMissing() {
        assertEquals(DataSourceState.PERMISSION_MISSING, DataSourceResolver.resolve(demoMode = false, usageAccessGranted = false))
    }

    @Test
    fun grantingPermissionSwitchesToLive() {
        var state = DataSourceResolver.resolve(false, false)
        assertEquals(DataSourceState.PERMISSION_MISSING, state)
        state = DataSourceResolver.resolve(false, true)
        assertEquals(DataSourceState.LIVE, state)
    }

    @Test
    fun revokingPermissionInSystemSettingsFallsBackToPermissionMissing() {
        assertEquals(DataSourceState.LIVE, DataSourceResolver.resolve(false, true))
        // Next onResume re-check reports the permission as revoked.
        assertEquals(DataSourceState.PERMISSION_MISSING, DataSourceResolver.resolve(false, false))
    }

    @Test
    fun demoModeWinsRegardlessOfPermissionAndCanBeLeftAgain() {
        assertEquals(DataSourceState.DEMO, DataSourceResolver.resolve(true, false))
        assertEquals(DataSourceState.DEMO, DataSourceResolver.resolve(true, true))
        assertEquals(DataSourceState.LIVE, DataSourceResolver.resolve(false, true))
        assertEquals(DataSourceState.PERMISSION_MISSING, DataSourceResolver.resolve(false, false))
    }

    @Test
    fun visibleSourceNeverMixesLiveAndDemo() {
        assertEquals(UsageSource.LIVE, DataSourceResolver.visibleSource(DataSourceState.LIVE))
        assertEquals(UsageSource.DEMO, DataSourceResolver.visibleSource(DataSourceState.DEMO))
        assertNull(DataSourceResolver.visibleSource(DataSourceState.PERMISSION_MISSING))
    }
}

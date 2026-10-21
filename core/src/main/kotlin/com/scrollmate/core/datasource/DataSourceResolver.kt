package com.scrollmate.core.datasource

import com.scrollmate.core.model.DataSourceState
import com.scrollmate.core.model.UsageSource

/**
 * Decides which data the app shows. Demo mode always wins (it is an explicit user choice);
 * otherwise live data is shown only while usage access is granted. The permission must be
 * re-checked on every resume because the user can revoke it in system settings at any time.
 */
object DataSourceResolver {

    fun resolve(demoMode: Boolean, usageAccessGranted: Boolean): DataSourceState = when {
        demoMode -> DataSourceState.DEMO
        usageAccessGranted -> DataSourceState.LIVE
        else -> DataSourceState.PERMISSION_MISSING
    }

    /** The usage source whose entries may be displayed in the given state, or null if none. */
    fun visibleSource(state: DataSourceState): UsageSource? = when (state) {
        DataSourceState.LIVE -> UsageSource.LIVE
        DataSourceState.DEMO -> UsageSource.DEMO
        DataSourceState.PERMISSION_MISSING -> null
    }
}

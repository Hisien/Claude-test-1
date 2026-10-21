package com.scrollmate.core.restriction

import com.scrollmate.core.model.RestrictionStatus

/**
 * Platform seam for a possible future, real restriction of other apps (for example via
 * FamilyControls/ManagedSettings on iOS). The MVP deliberately ships only [NoOpRestrictionService].
 */
interface AppRestrictionService {
    fun status(): RestrictionStatus
}

/** MVP implementation: other apps are never restricted, and the app says so openly. */
class NoOpRestrictionService : AppRestrictionService {
    override fun status(): RestrictionStatus = RestrictionStatus.UNAVAILABLE
}

package com.dsh.mobile.ui.navigation

/** A successful connection opens sessions only when explicitly requested, once. */
class ConnectionNavigation {
    private var requestedProfile: String? = null

    fun request(profileId: String) { requestedProfile = profileId }
    fun cancel() { requestedProfile = null }

    fun consume(profileId: String?, connected: Boolean, paired: Boolean): Boolean {
        if (!connected || !paired || profileId == null || requestedProfile != profileId) return false
        requestedProfile = null
        return true
    }
}

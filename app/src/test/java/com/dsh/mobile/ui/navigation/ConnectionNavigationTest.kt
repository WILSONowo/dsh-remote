package com.dsh.mobile.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class ConnectionNavigationTest {
    @Test fun pairingMustFinishBeforeOpeningSessions() {
        val navigation = ConnectionNavigation()
        navigation.request("a")
        assertFalse(navigation.consume("a", false, true))
        assertFalse(navigation.consume("a", true, false))
        assertTrue(navigation.consume("a", true, true))
        assertFalse(navigation.consume("a", true, true))
    }

    @Test fun returningToConnectionsOrSettingsDoesNotRedirectOnReconnect() {
        val navigation = ConnectionNavigation()
        navigation.request("a")
        navigation.cancel()
        assertFalse(navigation.consume("a", true, true))
    }

    @Test fun oldComputerCompletionCannotConsumeNewComputerRequest() {
        val navigation = ConnectionNavigation()
        navigation.request("a")
        navigation.request("b")
        assertFalse(navigation.consume("a", true, true))
        assertTrue(navigation.consume("b", true, true))
    }
}

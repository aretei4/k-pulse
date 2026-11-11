package com.kahga.kpulse.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gate between a Remote Config typo and every installed app pointing
 * somewhere useless. A rejected value leaves the app on the URL it already had.
 */
class AgentUrlTest {

    /** Stands in for the URL compiled into a build that has since gone stale. */
    private val FAILED = "https://direco.co.in/kpulse/agent"

    @Test
    fun `accepts an https url`() {
        assertEquals("https://direco.co.in/kpulse/agent", AgentUrl.usable("https://direco.co.in/kpulse/agent"))
    }

    @Test
    fun `trims whitespace around a pasted value`() {
        assertEquals("https://direco.co.in/kpulse/agent", AgentUrl.usable("  https://direco.co.in/kpulse/agent  "))
    }

    @Test
    fun `keeps the query string, so a demo flag survives`() {
        assertEquals(
            "https://direco.co.in/kpulse/agent?demo=1",
            AgentUrl.usable("https://direco.co.in/kpulse/agent?demo=1")
        )
    }

    @Test
    fun `refuses an empty or blank value`() {
        assertNull(AgentUrl.usable(null))
        assertNull(AgentUrl.usable(""))
        assertNull(AgentUrl.usable("   "))
    }

    @Test
    fun `refuses plain http, which would downgrade every install`() {
        assertNull(AgentUrl.usable("http://direco.co.in/kpulse/agent"))
    }

    @Test
    fun `refuses a value with no host`() {
        assertNull(AgentUrl.usable("https:///kpulse/agent"))
        assertNull(AgentUrl.usable("/kpulse/agent"))
    }

    @Test
    fun `refuses something that is not a url at all`() {
        assertNull(AgentUrl.usable("agent url goes here"))
        assertNull(AgentUrl.usable("javascript:alert(1)"))
    }

    @Test
    fun `rescue offers a different usable url after one fails`() {
        assertEquals(
            "https://kpulse.example.in/kpulse/agent",
            AgentUrl.rescueCandidate("https://kpulse.example.in/kpulse/agent", FAILED)
        )
    }

    @Test
    fun `rescue refuses the url that just failed, which would only fail again`() {
        assertNull(AgentUrl.rescueCandidate(FAILED, FAILED))
        assertNull(AgentUrl.rescueCandidate("  $FAILED  ", FAILED))
    }

    @Test
    fun `rescue holds a bad value to the same rule as any other`() {
        assertNull(AgentUrl.rescueCandidate(null, FAILED))
        assertNull(AgentUrl.rescueCandidate("", FAILED))
        assertNull(AgentUrl.rescueCandidate("http://kpulse.example.in/kpulse/agent", FAILED))
        assertNull(AgentUrl.rescueCandidate("the new address", FAILED))
    }

    @Test
    fun `reads the host for the in-app navigation check`() {
        assertEquals("direco.co.in", AgentUrl.hostOf("https://direco.co.in/kpulse/agent"))
        assertNull(AgentUrl.hostOf("not a url"))
    }
}

package com.kahga.kpulse.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gate between a Remote Config typo and every installed app pointing
 * somewhere useless. A rejected value leaves the app on the URL it already had.
 */
class AgentUrlTest {

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
    fun `reads the host for the in-app navigation check`() {
        assertEquals("direco.co.in", AgentUrl.hostOf("https://direco.co.in/kpulse/agent"))
        assertNull(AgentUrl.hostOf("not a url"))
    }
}

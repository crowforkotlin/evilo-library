package crow.evilo.plugin.api

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginCompatibilityTest {
    private val host = HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY))

    @Test
    fun everyDeclaredCapabilityIsPresentOnTheHost() {
        assertNull(hostIncompatibility(host.capabilities, host))
    }

    @Test
    fun missingCapabilityIsNamedInTheFailure() {
        val reason = hostIncompatibility(setOf(HOST_STORAGE_CAPABILITY), host)
        assertNotNull(reason)
        assertTrue(reason.contains(HOST_STORAGE_CAPABILITY))
    }
}

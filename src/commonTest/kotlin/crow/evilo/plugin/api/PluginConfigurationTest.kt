package crow.evilo.plugin.api

import crow.evilo.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginConfigurationTest {
    private val enabled = PluginFilterField("enabled", PluginLabel("Enabled"), control = PluginFilterControl.TOGGLE,
        defaultValue = PluginFilterValue(toggle = true))
    private val choices = PluginFilterField("category", PluginLabel("Category"), control = PluginFilterControl.CHOICE,
        options = listOf(PluginFilterOption("one", PluginLabel("One"))), defaultValue = PluginFilterValue(choiceIds = listOf("one")),
        dependencies = listOf(PluginFilterDependency("enabled", PluginFilterValue(toggle = true))))
    private val schema = PluginConfiguration(groups = listOf(PluginFilterGroup("filters", PluginLabel("Filters"), listOf(enabled, choices))))

    @Test
    fun localControlsRequireExplicitNonSecretMetadataBindings() {
        val local = enabled.copy(scope = PluginFilterScope.LOCAL)
        val unbound = PluginConfiguration(groups = listOf(PluginFilterGroup("local", PluginLabel("Local"), listOf(local))))
        assertEquals(emptyMap(), resolvePluginFilters(unbound, emptyMap()))
        val bound = unbound.copy(groups = listOf(unbound.groups.first().copy(fields = listOf(local.copy(localMetadataKey = "provider.completed")))))
        assertEquals(mapOf("enabled" to PluginFilterValue(toggle = true)), resolvePluginFilters(bound, emptyMap(), PluginFilterScope.LOCAL))
        assertEquals(emptyMap(), resolvePluginFilters(bound, emptyMap(), PluginFilterScope.PROVIDER))
        assertFailsWith<IllegalArgumentException> {
            validateConfiguration(unbound.copy(groups = listOf(unbound.groups.first().copy(fields = listOf(local.copy(required = true))))))
        }
        assertFailsWith<IllegalArgumentException> {
            validateConfiguration(unbound.copy(groups = listOf(unbound.groups.first().copy(fields = listOf(local.copy(localMetadataKey = "provider.token"))))))
        }
    }

    @Test
    fun defaultsRoundTripAndDependenciesOmitDisabledValues() {
        val defaults = resolvePluginFilters(schema, emptyMap())
        assertEquals(defaults, PluginJson.decodeFromString<Map<String, PluginFilterValue>>(PluginJson.encodeToString(defaults)))
        val disabled = resolvePluginFilters(schema, mapOf("enabled" to PluginFilterValue(toggle = false)))
        assertFalse("category" in disabled)
        assertEquals(listOf("one"), defaults.getValue("category").choiceIds)
    }

    @Test
    fun rejectsUnknownChoicesMixedValuesCyclesAndOversizedSchemas() {
        assertFailsWith<IllegalArgumentException> { resolvePluginFilters(schema, mapOf("category" to PluginFilterValue(choiceIds = listOf("other")))) }
        assertFailsWith<IllegalArgumentException> { resolvePluginFilters(schema, mapOf("enabled" to PluginFilterValue(toggle = true, text = "secret"))) }
        assertFailsWith<IllegalArgumentException> { validateConfiguration(schema.copy(groups = listOf(schema.groups.first().copy(fields = listOf(choices, enabled))))) }
        assertFailsWith<IllegalArgumentException> { validateConfiguration(schema.copy(groups = List(9) { schema.groups.first() })) }
    }

    @Test
    fun unknownOptionalControlIsOmittedAndRequiredControlFails() {
        val field = PluginJson.decodeFromString<PluginFilterField>("""{"id":"future","label":{"fallback":"Future"},"control":"FUTURE"}""")
        val configuration = PluginConfiguration(groups = listOf(PluginFilterGroup("future", PluginLabel("Future"), listOf(field))))
        assertEquals(emptyMap(), resolvePluginFilters(configuration, emptyMap()))
        assertFailsWith<IllegalArgumentException> { validateConfiguration(configuration.copy(groups = listOf(configuration.groups.first().copy(fields = listOf(field.copy(required = true)))))) }
    }

    @Test
    fun unknownOptionalAccessActionIsUnsupportedAndRequiredUnsupportedAccessFails() {
        val action = PluginJson.decodeFromString<PluginAccessAction>("""{"id":"future","label":{"fallback":"Future"},"requirementId":"account","kind":"FUTURE"}""")
        assertEquals(PluginAccessActionKind.UNSUPPORTED, action.kind)
        val configuration = PluginConfiguration(access = listOf(PluginAccessRequirement("account", PluginLabel("Account"),
            PluginAccessKind.LOGIN, required = true, actionId = "future")), actions = listOf(action))
        val descriptor = PluginDescriptor("example", "Example", "1.0.0", 1, setOf(MediaKind.MANGA), listOf("en"),
            configuration = configuration, protocol = ProtocolRequirement(requiredCapabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_ACCESS_CAPABILITY)))
        assertFailsWith<IllegalArgumentException> { validatePluginDescriptorConfiguration(descriptor) }
    }

    @Test
    fun minorAndCapabilitiesNegotiateWithoutRejectingOptionalFeatures() {
        val oldHost = HostDescriptor("old", 1, protocolMinor = 0, capabilities = setOf(HOST_MEDIA_CAPABILITY))
        assertNull(ProtocolRequirement(optionalCapabilities = setOf(HOST_FILTER_CAPABILITY)).incompatibility(oldHost))
        assertNotNull(ProtocolRequirement(requiredCapabilities = setOf(HOST_FILTER_CAPABILITY)).incompatibility(oldHost))
        assertNotNull(ProtocolRequirement(minimumMinor = 1).incompatibility(oldHost))
        val old = PluginJson.decodeFromString<PluginDescriptor>("""{"id":"legacy","name":"Legacy","version":"1.0.0","versionCode":1,"kinds":["manga"],"languages":["en"]}""")
        validatePluginDescriptorConfiguration(old)
        assertEquals(PluginConfiguration(), old.configuration)
        assertEquals(emptyMap(), PluginOperation(PluginOperationKind.SEARCH, MediaKind.MANGA).filters)
    }

    @Test
    fun rangeAndTextAreBoundedAndSchemaChangesPruneInvalidNonSecretSelections() {
        val range = PluginFilterField("size", PluginLabel("Size"), control = PluginFilterControl.RANGE, min = 0.0, max = 4.0, step = 2.0)
        assertFailsWith<IllegalArgumentException> { validatePluginFilterValue(range, PluginFilterValue(number = 1.0)) }
        val text = PluginFilterField("text", PluginLabel("Text"), control = PluginFilterControl.TEXT, maxLength = 2)
        assertFailsWith<IllegalArgumentException> { validatePluginFilterValue(text, PluginFilterValue(text = "long")) }
        assertEquals(resolvePluginFilters(schema, emptyMap()), prunePluginFilters(schema,
            mapOf("category" to PluginFilterValue(choiceIds = listOf("removed")), "removed-field" to PluginFilterValue(text = "discard"))))
    }
}

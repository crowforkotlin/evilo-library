package crow.evilo.plugin.api

import kotlinx.serialization.Serializable

@Serializable
data class ProtocolRequirement(
    val major: Int = 1,
    val minimumMinor: Int = 0,
    val requiredCapabilities: Set<String> = setOf(HOST_MEDIA_CAPABILITY),
    val optionalCapabilities: Set<String> = emptySet(),
) {
    fun incompatibility(host: HostDescriptor): String? = when {
        major != host.protocolMajor -> "Unsupported plugin protocol major."
        minimumMinor > host.protocolMinor -> "Plugin requires a newer host protocol minor."
        else -> hostIncompatibility(requiredCapabilities, host)
    }
}

fun validateProtocolRequirement(protocol: ProtocolRequirement) {
    val capabilities = protocol.requiredCapabilities + protocol.optionalCapabilities
    require(protocol.major > 0 && protocol.minimumMinor >= 0 && capabilities.size <= 32 &&
        protocol.requiredCapabilities.intersect(protocol.optionalCapabilities).isEmpty() &&
        capabilities.all { it.length <= 128 && Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+").matches(it) }) { "Invalid protocol requirements." }
}

fun validatePluginDescriptorConfiguration(descriptor: PluginDescriptor) {
    validateProtocolRequirement(descriptor.protocol)
    validateConfiguration(descriptor.configuration)
    val declared = descriptor.protocol.requiredCapabilities + descriptor.protocol.optionalCapabilities
    require(descriptor.configuration.groups.isEmpty() || HOST_FILTER_CAPABILITY in declared) { "Filters require an explicit capability declaration." }
    require(descriptor.configuration.actions.none { it.kind == PluginAccessActionKind.SESSION_TOKEN } || HOST_ACCESS_CAPABILITY in declared) { "Session token actions require an explicit capability declaration." }
    require(descriptor.configuration.groups.flatMap { it.fields }.none { it.required } || HOST_FILTER_CAPABILITY in descriptor.protocol.requiredCapabilities) { "Required filters require host filter support." }
    require(descriptor.configuration.access.none { it.required } || HOST_ACCESS_CAPABILITY in descriptor.protocol.requiredCapabilities) { "Required access requires host credential support." }
    descriptor.configuration.access.filter { it.required }.forEach { requirement ->
        require(descriptor.configuration.actions.any { it.id == requirement.actionId && it.requirementId == requirement.id &&
            it.kind == PluginAccessActionKind.SESSION_TOKEN }) { "Unsupported required access action." }
    }
}

/**
 * Reports why this host cannot run a plugin declaring [required] capabilities.
 * Returns null when every requirement is satisfied.
 */
fun hostIncompatibility(required: Set<String>, host: HostDescriptor): String? {
    val missing = required - host.capabilities
    return if (missing.isEmpty()) null else "Missing host capabilities: ${missing.sorted().joinToString()}."
}

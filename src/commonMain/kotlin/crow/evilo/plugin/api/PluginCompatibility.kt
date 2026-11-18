package crow.evilo.plugin.api

/**
 * Reports why this host cannot run a plugin declaring [required] capabilities.
 * Returns null when every requirement is satisfied.
 */
fun hostIncompatibility(required: Set<String>, host: HostDescriptor): String? {
    val missing = required - host.capabilities
    return if (missing.isEmpty()) null else "Missing host capabilities: ${missing.sorted().joinToString()}."
}

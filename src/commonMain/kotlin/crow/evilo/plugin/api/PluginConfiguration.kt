package crow.evilo.plugin.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.round

const val HOST_FILTER_CAPABILITY = "configuration.filters.v1"
const val HOST_ACCESS_CAPABILITY = "access.session-token.v1"
// First host source version implementing these contracts; older hosts ignore additive JSON fields.
const val CONFIGURATION_MINIMUM_HOST_VERSION_CODE = 2L

@Serializable
data class PluginLabel(val fallback: String, val localized: Map<String, String> = emptyMap())

@Serializable
enum class PluginFilterScope { LOCAL, PROVIDER }

@Serializable(with = PluginFilterControlSerializer::class)
enum class PluginFilterControl { CHOICE, TOGGLE, RANGE, TEXT, UNKNOWN }

object PluginFilterControlSerializer : KSerializer<PluginFilterControl> {
    override val descriptor = PrimitiveSerialDescriptor("PluginFilterControl", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PluginFilterControl) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): PluginFilterControl {
        val name = decoder.decodeString()
        return PluginFilterControl.entries.firstOrNull { it.name == name } ?: PluginFilterControl.UNKNOWN
    }
}

/** Exactly one value channel is populated. This model must never hold account secrets. */
@Serializable
data class PluginFilterValue(
    val choiceIds: List<String> = emptyList(),
    val toggle: Boolean? = null,
    val number: Double? = null,
    val text: String? = null,
)

@Serializable
data class PluginFilterOption(val id: String, val label: PluginLabel)

/** A bounded equality condition, not an expression language. All conditions must match. */
@Serializable
data class PluginFilterDependency(val fieldId: String, val value: PluginFilterValue)

@Serializable
data class PluginFilterField(
    val id: String,
    val label: PluginLabel,
    val scope: PluginFilterScope = PluginFilterScope.PROVIDER,
    val control: PluginFilterControl,
    val required: Boolean = false,
    val help: PluginLabel? = null,
    val defaultValue: PluginFilterValue? = null,
    val options: List<PluginFilterOption> = emptyList(),
    val multiple: Boolean = false,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val maxLength: Int = 256,
    val dependencies: List<PluginFilterDependency> = emptyList(),
    val localMetadataKey: String? = null,
)

@Serializable
data class PluginFilterGroup(val id: String, val label: PluginLabel, val fields: List<PluginFilterField>, val help: PluginLabel? = null)

@Serializable
enum class PluginAccessKind { LOGIN, TOKEN, CHALLENGE }

@Serializable(with = PluginAccessActionKindSerializer::class)
enum class PluginAccessActionKind { SESSION_TOKEN, UNSUPPORTED }

object PluginAccessActionKindSerializer : KSerializer<PluginAccessActionKind> {
    override val descriptor = PrimitiveSerialDescriptor("PluginAccessActionKind", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PluginAccessActionKind) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): PluginAccessActionKind {
        val name = decoder.decodeString()
        return PluginAccessActionKind.entries.firstOrNull { it.name == name } ?: PluginAccessActionKind.UNSUPPORTED
    }
}

@Serializable
data class PluginCredentialGrant(
    val origin: String,
    val methods: Set<String> = setOf("GET"),
    val pathPrefix: String,
    val headerName: String = "Authorization",
    val valuePrefix: String = "Bearer ",
)

@Serializable
data class PluginAccessRequirement(
    val id: String,
    val label: PluginLabel,
    val kind: PluginAccessKind,
    val required: Boolean = false,
    val actionId: String? = null,
)

/** Grants are shown for explicit host approval. Declaring them never authorizes insertion. */
@Serializable
data class PluginAccessAction(
    val id: String,
    val label: PluginLabel,
    val requirementId: String,
    val kind: PluginAccessActionKind = PluginAccessActionKind.UNSUPPORTED,
    val help: PluginLabel? = null,
    val grants: List<PluginCredentialGrant> = emptyList(),
)

@Serializable
data class PluginConfiguration(
    val groups: List<PluginFilterGroup> = emptyList(),
    val access: List<PluginAccessRequirement> = emptyList(),
    val actions: List<PluginAccessAction> = emptyList(),
)

/** Opaque identifier only. It is valid solely in the host session that issued it. */
@Serializable
data class PluginCredentialReference(val value: String)

@Serializable
enum class PluginAccessStatus { MISSING, READY, EXPIRED, UNSUPPORTED, CHALLENGE }

@Serializable
data class PluginAccessState(
    val requirementId: String,
    val status: PluginAccessStatus,
    val message: PluginLabel? = null,
    val actionId: String? = null,
)

private val CONFIGURATION_ID = Regex("[a-z][a-z0-9_.-]{0,63}")

fun validatePluginLabel(label: PluginLabel) {
    require(label.fallback.isNotBlank() && label.fallback.length <= 512 && label.localized.size <= 16) { "Invalid configuration label." }
    require((label.localized.values + label.fallback).all { it.length <= 512 && it.none { c -> c < ' ' && c != '\n' } }) { "Invalid configuration label text." }
    require(label.localized.keys.all { Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*").matches(it) }) { "Invalid configuration language." }
}

fun validateConfiguration(configuration: PluginConfiguration) {
    require(PluginJson.encodeToString(configuration).encodeToByteArray().size <= 12 * 1024) { "Configuration exceeds 12 KiB." }
    require(configuration.groups.size <= 8 && configuration.access.size <= 16 && configuration.actions.size <= 16) { "Too many configuration sections." }
    val fields = configuration.groups.flatMap { it.fields }
    require(fields.size <= 32 && fields.distinctBy { it.id }.size == fields.size) { "Invalid configuration field count or IDs." }
    require(configuration.groups.distinctBy { it.id }.size == configuration.groups.size) { "Duplicate configuration group IDs." }
    val earlier = mutableMapOf<String, PluginFilterField>()
    configuration.groups.forEach { group ->
        require(CONFIGURATION_ID.matches(group.id) && group.fields.size <= 16) { "Invalid configuration group." }
        validatePluginLabel(group.label)
        group.help?.let(::validatePluginLabel)
        group.fields.forEach { field ->
            require(CONFIGURATION_ID.matches(field.id) && field.options.size <= 64 && field.dependencies.size <= 8 &&
                field.id.split('.', '_', '-').none { it in setOf("token", "secret", "credential", "password", "authorization", "cookie") }) { "Invalid or credential-shaped configuration field." }
            validatePluginLabel(field.label)
            field.help?.let(::validatePluginLabel)
            require(field.control != PluginFilterControl.UNKNOWN || !field.required) { "Unsupported required filter control." }
            require(field.scope != PluginFilterScope.LOCAL || !field.required || field.localMetadataKey != null) { "Required local filters need an explicit metadata binding." }
            field.localMetadataKey?.let { key ->
                require(field.scope == PluginFilterScope.LOCAL && CONFIGURATION_ID.matches(key) &&
                    key.split('.', '_', '-').none { it in setOf("token", "secret", "credential", "authorization", "cookie", "account") }) {
                    "Local metadata bindings require a bounded non-secret extension key."
                }
            }
            require(field.options.distinctBy { it.id }.size == field.options.size) { "Duplicate configuration options." }
            field.options.forEach { require(CONFIGURATION_ID.matches(it.id)); validatePluginLabel(it.label) }
            require(field.control in setOf(PluginFilterControl.CHOICE, PluginFilterControl.UNKNOWN) || field.options.isEmpty()) { "Only choices may declare options." }
            require(field.control in setOf(PluginFilterControl.CHOICE, PluginFilterControl.UNKNOWN) || !field.multiple) { "Only choices may be multiple." }
            require(field.min.isFinite() && field.max.isFinite() && field.step.isFinite() && field.min <= field.max && field.step > 0 &&
                field.maxLength in 1..1024) { "Invalid filter constraints." }
            field.defaultValue?.let { validatePluginFilterValue(field, it) }
            require(field.control == PluginFilterControl.UNKNOWN || !field.required || field.defaultValue != null) { "Required filters need valid defaults." }
            field.dependencies.forEach { dependency ->
                val parent = requireNotNull(earlier[dependency.fieldId]) { "Dependencies must reference earlier fields, without cycles." }
                require(parent.scope == field.scope && parent.control != PluginFilterControl.UNKNOWN &&
                    (parent.scope != PluginFilterScope.LOCAL || parent.localMetadataKey != null)) { "Invalid dependency scope or control." }
                validatePluginFilterValue(parent, dependency.value)
            }
            earlier[field.id] = field
        }
    }
    require(configuration.access.distinctBy { it.id }.size == configuration.access.size &&
        configuration.actions.distinctBy { it.id }.size == configuration.actions.size) { "Duplicate access IDs." }
    configuration.access.forEach { requirement ->
        require(CONFIGURATION_ID.matches(requirement.id)); validatePluginLabel(requirement.label)
        requirement.actionId?.let { actionId -> require(configuration.actions.any { it.id == actionId && it.requirementId == requirement.id }) { "Missing access action." } }
    }
    configuration.actions.forEach { action ->
        require(CONFIGURATION_ID.matches(action.id) && configuration.access.any { it.id == action.requirementId }) { "Invalid access action." }
        validatePluginLabel(action.label); action.help?.let(::validatePluginLabel)
        require(action.grants.size <= 8 && (action.kind != PluginAccessActionKind.SESSION_TOKEN || action.grants.isNotEmpty())) { "Invalid credential grant count." }
        action.grants.forEach { grant ->
            require(Regex("https://[A-Za-z0-9.-]+(?::443)?").matches(grant.origin)) { "Credential origins must be exact HTTPS origins." }
            require(grant.pathPrefix.startsWith('/') && grant.pathPrefix.endsWith('/') && grant.pathPrefix.length <= 512 &&
                Regex("/[A-Za-z0-9_/-]*").matches(grant.pathPrefix) && "//" !in grant.pathPrefix) { "Credential grants require bounded path prefixes." }
            require(grant.methods.isNotEmpty() && grant.methods.size <= 4 && grant.methods.all { it in setOf("GET", "POST", "PUT", "DELETE") }) { "Invalid credential methods." }
            val header = grant.headerName.lowercase()
            require(header == "authorization" || (Regex("x-[a-z0-9-]{1,48}").matches(header) &&
                !header.startsWith("x-forwarded-") && header !in setOf("x-requested-with", "x-http-method-override"))) { "Invalid credential header." }
            require(grant.valuePrefix.length <= 32 && grant.valuePrefix.all { it in ' '..'~' }) { "Invalid credential prefix." }
        }
    }
}

fun validatePluginFilterValue(field: PluginFilterField, value: PluginFilterValue) {
    require(value.choiceIds.size <= 64 && value.choiceIds.distinct().size == value.choiceIds.size) { "Invalid selected choices." }
    val channels = listOf(value.choiceIds.isNotEmpty(), value.toggle != null, value.number != null, value.text != null).count { it }
    require(channels <= 1) { "Filter values cannot mix control types." }
    when (field.control) {
        PluginFilterControl.CHOICE -> require(value.toggle == null && value.number == null && value.text == null &&
            (field.multiple || value.choiceIds.size <= 1) && (!field.required || value.choiceIds.isNotEmpty()) &&
            value.choiceIds.all { id -> field.options.any { it.id == id } }) { "Invalid choice value." }
        PluginFilterControl.TOGGLE -> require(channels == 1 && value.toggle != null) { "Invalid toggle value." }
        PluginFilterControl.RANGE -> {
            val number = requireNotNull(value.number) { "A range requires a number." }
            val steps = (number - field.min) / field.step
            require(channels == 1 && number.isFinite() && number in field.min..field.max && abs(steps - round(steps)) < 0.000001) { "Invalid range value." }
        }
        PluginFilterControl.TEXT -> require(channels == 1 && value.text != null && value.text.length <= field.maxLength &&
            value.text.none { it < ' ' || it == '\u007f' } && (!field.required || value.text.isNotBlank())) { "Invalid text value." }
        PluginFilterControl.UNKNOWN -> require(!field.required) { "Unsupported required filter." }
    }
}

fun isPluginFilterEnabled(field: PluginFilterField, values: Map<String, PluginFilterValue>): Boolean =
    field.dependencies.all { values[it.fieldId] == it.value }

/** Defaults are materialized; disabled and unknown optional controls are omitted, never sent. */
fun resolvePluginFilters(configuration: PluginConfiguration, values: Map<String, PluginFilterValue>, scope: PluginFilterScope? = null): Map<String, PluginFilterValue> {
    validateConfiguration(configuration)
    val fields = configuration.groups.flatMap { it.fields }
    require(values.size <= 32 && values.keys.all { id -> fields.any { it.id == id } }) { "Unknown filter field." }
    values.forEach { (id, value) ->
        val field = fields.first { it.id == id }
        if (field.control != PluginFilterControl.UNKNOWN) validatePluginFilterValue(field, value)
    }
    val resolved = linkedMapOf<String, PluginFilterValue>()
    fields.forEach { field ->
        if (field.control != PluginFilterControl.UNKNOWN &&
            (field.scope != PluginFilterScope.LOCAL || field.localMetadataKey != null) && isPluginFilterEnabled(field, resolved)) {
            val value = values[field.id] ?: field.defaultValue
            if (value != null) { validatePluginFilterValue(field, value); resolved[field.id] = value.copy(choiceIds = value.choiceIds.toList()) }
            else require(!field.required) { "Missing required filter value." }
        }
    }
    require(PluginJson.encodeToString(resolved).encodeToByteArray().size <= 8 * 1024) { "Filter selection exceeds 8 KiB." }
    return if (scope == null) resolved.toMap() else resolved.filterKeys { id -> fields.first { it.id == id }.scope == scope }
}

/** Retain only non-secret values that still satisfy a replaced schema. */
fun prunePluginFilters(configuration: PluginConfiguration, values: Map<String, PluginFilterValue>): Map<String, PluginFilterValue> {
    validateConfiguration(configuration)
    val retained = configuration.groups.flatMap { it.fields }.mapNotNull { field ->
        values[field.id]?.takeIf { value -> runCatching { validatePluginFilterValue(field, value) }.isSuccess }?.let { field.id to it }
    }.toMap()
    return resolvePluginFilters(configuration, retained)
}

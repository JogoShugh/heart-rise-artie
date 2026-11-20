package heart

/**
 * The two dialects (SPEC §6.1). HAL Schema Forms is the reference dialect,
 * defined for every rung. HAL-FORMS is the L0 on-ramp (0.5 decision). JSON is
 * no dialect: plain JSON, with no profile, read as data only.
 */
enum class Dialect(val mediaType: String, val profile: String, val maxRung: Rung, val label: String) {
    HAL_SCHEMA_FORMS(HAL_JSON, HAL_SCHEMA_FORMS_PROFILE, Rung.L2, "hal-schema-forms"),
    HAL_FORMS("application/prs.hal-forms+json", "https://w3id.org/heart-rise/hal-forms", Rung.L0, "hal-forms"),
    JSON("application/json", "", Rung.L0, "json"),
    ;

    /** Whether it is a HEART dialect, one that names a profile and carries controls. */
    val heart: Boolean get() = profile.isNotEmpty()

    companion object {
        /** "hal-schema-forms", "hal-forms" or "json". */
        fun named(name: String): Dialect = entries.firstOrNull { it.label == name } ?: error("unknown dialect $name")
    }
}

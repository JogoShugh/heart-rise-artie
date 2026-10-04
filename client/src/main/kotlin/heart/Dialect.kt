package heart

/**
 * The two dialects (SPEC §6.1). HAL Schema Forms is the reference dialect,
 * defined for every rung. HAL-FORMS is the L0 on-ramp (0.5 decision).
 */
enum class Dialect(val mediaType: String, val profile: String, val maxRung: Rung) {
    HAL_SCHEMA_FORMS(HAL_JSON, HAL_SCHEMA_FORMS_PROFILE, Rung.L2),
    HAL_FORMS("application/prs.hal-forms+json", "https://github.com/jogoshugh/heart-rise/hal-forms", Rung.L0),
    ;

    companion object {
        /** "hal-schema-forms" or "hal-forms", the last segment of the profile URI. */
        fun named(name: String): Dialect =
            entries.firstOrNull { it.profile.endsWith("/$name") } ?: error("unknown dialect $name")
    }
}

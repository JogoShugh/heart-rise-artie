package steps

data class AffordanceFields(val href: String, val hash: String)
data class StateEntry(val key: String, val value: String)
data class ManifestEntry(val rel: String, val present: Boolean)

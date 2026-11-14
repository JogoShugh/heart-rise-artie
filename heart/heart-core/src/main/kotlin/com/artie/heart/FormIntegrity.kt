package com.artie.heart

/**
 * A form's own integrity (HAL-SCHEMA-FORMS-HR Rule 10): over what the form does, the members it has of these,
 * in canonical form: its target (a URI template where it is one), its template pointers, its method, its
 * content type, and its schema's integrity. Not its title or description, so a retitled form keeps it. A
 * resource's forms and the bookmark's alike, so a replaying client can pin every form it uses.
 */
internal object FormIntegrity {
    fun of(
        target: String,
        method: String,
        schemaIntegrity: String,
        pointers: Map<String, String>? = null,
        contentType: String? = null,
    ): String {
        val members = buildMap<String, Any> {
            put("target", target)
            pointers?.let { put("hr:templatePointers", it) }
            put("method", method)
            contentType?.let { put("contentType", it) }
            put("schema", schemaIntegrity)
        }
        return ContentAddress.integrity(ContentAddress.canonical(members))
    }

    /** The integrity of a schema written inline in a form: of its canonical text. */
    fun ofSchema(schema: Any?): String = ContentAddress.integrity(ContentAddress.canonical(schema))
}

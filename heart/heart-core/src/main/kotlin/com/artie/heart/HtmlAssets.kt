package com.artie.heart

/**
 * The files a HEART page loads, served by the server itself at [PATH], never from a CDN: htmx 2.0.4 (Zero-Clause
 * BSD), Ajv 8.17.1's JSON Schema 2020-12 build (MIT), heart-forms.js and heart.css. Each is published under a
 * name that holds its content's address, so it is immutable and cached for a year; a release that changes one
 * changes its name.
 */
object HtmlAssets {
    const val PATH = "/heart/assets/"
    const val CACHE_CONTROL = "public, max-age=31536000, immutable"

    /** One file: its published name, media type and bytes. */
    class Asset(val name: String, val mediaType: String, val text: String)

    private val files = listOf(
        "htmx-2.0.4.min.js" to "text/javascript; charset=utf-8",
        "ajv2020-8.17.1.min.js" to "text/javascript; charset=utf-8",
        "heart-forms.js" to "text/javascript; charset=utf-8",
        "heart.css" to "text/css; charset=utf-8",
    )

    private val byFile: Map<String, Asset> by lazy {
        files.associate { (file, type) ->
            val text = checkNotNull(HtmlAssets::class.java.getResource("/heart/assets/$file")) { "no asset $file" }
                .readText()
            val dot = file.lastIndexOf('.')
            val name = "${file.substring(0, dot)}.${ContentAddress.address(text).take(HASH)}${file.substring(dot)}"
            file to Asset(name, type, text)
        }
    }

    /** Every asset, by its published name, for `/heart/assets/{name}`. */
    val byName: Map<String, Asset> by lazy { byFile.values.associateBy { it.name } }

    /**
     * Every published name, in order: part of what each page's validator names, so a release that changes a
     * script changes the validator, and a browser revalidating a page is never told 304 for the old script.
     */
    val names: List<String> by lazy { byName.keys.sorted() }

    /** Where the asset first named [file] is served. */
    fun href(file: String): String = PATH + byFile.getValue(file).name

    private const val HASH = 12
}

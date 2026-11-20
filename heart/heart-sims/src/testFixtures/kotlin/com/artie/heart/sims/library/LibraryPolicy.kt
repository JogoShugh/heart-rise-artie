package com.artie.heart.sims.library

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * The library's rules, as its policy file says them: a Java properties file kept with the library's code, so a
 * commit can change them and what a build deploys is what that commit says. A rule the file leaves out keeps its
 * default.
 */
data class LibraryPolicy(
    /** How far a renewal moves a loan's due date: `renewal.weeks`. */
    val renewalWeeks: Long = DEFAULT_RENEWAL_WEEKS,
) {
    companion object {
        const val DEFAULT_RENEWAL_WEEKS = 3L

        /** The policy [text] says, a properties file's lines. */
        fun of(text: String): LibraryPolicy {
            val read = Properties().apply { load(text.reader()) }
            return LibraryPolicy(
                renewalWeeks = read.getProperty("renewal.weeks")?.trim()?.toLong() ?: DEFAULT_RENEWAL_WEEKS,
            )
        }

        /** The policy in the file at [path]. */
        fun read(path: Path): LibraryPolicy = of(Files.readString(path))
    }
}

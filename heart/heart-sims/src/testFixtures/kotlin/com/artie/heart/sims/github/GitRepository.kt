package com.artie.heart.sims.github

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * A real git repository on this machine, where the small GitHub keeps a repository's pull requests: pull request N
 * is branch `pr-N`, and merging it makes a real commit on main, by the git command line.
 */
class GitRepository(val path: Path) {
    /** Each pull request branch, `pr-N`, by its number. */
    fun pullBranches(): Map<Long, String> =
        git("for-each-ref", "--format=%(refname:short)", "refs/heads/pr-*").lines().filter { it.isNotBlank() }
            .associateBy { it.removePrefix("pr-").toLong() }

    /** The commit at the tip of [ref]. */
    fun head(ref: String): String = git("rev-parse", ref).trim()

    /** The subject of the commit at the tip of [ref]. */
    fun subject(ref: String): String = git("log", "-1", "--format=%s", ref).trim()

    /** Merges [branch] into main by [method] (squash, merge or rebase), with [title]; the new main commit. */
    fun merge(branch: String, method: String, title: String): String {
        git("checkout", "-q", "main")
        when (method) {
            "squash" -> {
                git("merge", "--squash", "-q", branch)
                git("commit", "-q", "-m", title)
            }
            "rebase" -> git("rebase", "-q", "main", branch).also { git("checkout", "-q", "main"); git("merge", "-q", "--ff-only", branch) }
            else -> git("merge", "-q", "--no-ff", "-m", title, branch)
        }
        return head("main")
    }

    /** The text of [file] in [ref]. */
    fun show(ref: String, file: String): String = git("show", "$ref:$file")

    private fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("git", "-C", path.toString()) + args).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor(TIMEOUT, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${args.joinToString(" ")}: $out" }
        return out
    }

    private companion object {
        const val TIMEOUT = 30L
    }
}

package com.artie.heart

/**
 * A refusal as a browser shows it (spec/PLAN-HTML-DIALECT.md): the RFC 9457 problem's title, its detail, and each
 * violation at the field its JSON Pointer names, as an HTML fragment. heart-forms.js puts it in the dialog that
 * asked, and each violation beside its field. Every word is written as text.
 */
object HtmlProblem {
    const val MEDIA_TYPE = HtmlView.MEDIA_TYPE

    /** [problem], a problem details object, as HTML. */
    fun of(problem: Map<*, *>): String {
        val detail = problem["detail"]?.let { "<p>${Html.text(it)}</p>" }.orEmpty()
        val violations = (problem["violations"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val list = if (violations.isEmpty()) "" else "<ul>" + violations.joinToString("") {
            "<li${Html.attr("data-pointer", it["pointer"])}>${Html.text(it["message"])}</li>"
        } + "</ul>"
        return "<div class=\"problem\" role=\"alert\"><strong>${Html.text(problem["title"] ?: "Refused")}</strong>" +
            "$detail$list</div>\n"
    }

    /** [problem], the JSON text of a problem details object, as HTML. */
    fun of(problem: String): String = of(JsonTrees.plain(problem) as? Map<*, *> ?: emptyMap<String, Any?>())

    /**
     * The page a browser shows a person once they dismiss its own prompt for a credential: [problem], the JSON
     * text of the 401, headed for [realm]. Its link asks for the same resource again, so the browser prompts again.
     */
    fun signIn(problem: String, realm: String): String {
        val detail = (JsonTrees.plain(problem) as? Map<*, *>)?.get("detail")
        val title = "Sign in to $realm"
        return HtmlView.document(
            title, "",
            "<h1>${Html.text(title)}</h1>" + (detail?.let { "<p>${Html.text(it)}</p>" }.orEmpty()) +
                "<p class=\"hint\">Your browser asks for a user name and a password; a token can be the password.</p>" +
                "<p><a class=\"sign-in\" href=\"\">Sign in</a></p>",
        )
    }
}

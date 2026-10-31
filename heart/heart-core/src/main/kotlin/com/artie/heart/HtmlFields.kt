package com.artie.heart

/**
 * One field of an act's dialog, from its JSON Schema: its control, the constraints a browser checks before
 * sending (the server still decides), its label and its hint. An integer or a number is a number box; a boolean a
 * checkbox; an `enum` a choice; an array of texts a list a person can add to; a text a text box, or a text area
 * when its member is named for long text. The label is the schema's title, else the name titled
 * ([FieldTitle]); the hint its description.
 */
internal object HtmlFields {
    /** The members that hold long text by convention, whatever the service. */
    private val LONG_TEXT = setOf("body", "content", "description", "text", "comment")

    fun of(formId: String, name: String, schema: Map<*, *>, required: Boolean): String {
        val id = "$formId-${Html.token(name)}"
        val label = schema["title"]?.toString() ?: FieldTitle.of(name)
        val hint = schema["description"]?.let { "<p class=\"hint\" id=\"$id-hint\">${Html.text(it)}</p>" }.orEmpty()
        val described = if (hint.isEmpty()) "" else Html.attr("aria-describedby", "$id-hint")
        val control = control(id, name, schema, required) + described
        return if (type(schema) == "boolean") {
            "<div class=\"field\"><label class=\"check\"><input type=\"checkbox\"$control value=\"true\">" +
                " ${Html.text(label)}</label>$hint</div>"
        } else {
            "<div class=\"field\"><label for=\"${Html.attr(id)}\">${Html.text(label)}" +
                (if (required) "<span class=\"required\" aria-hidden=\"true\">*</span>" else "") +
                "</label>${element(name, schema, control)}$hint</div>"
        }
    }

    /** The control's element, around its attributes. */
    private fun element(name: String, schema: Map<*, *>, control: String): String {
        val choices = schema["enum"] as? List<*>
        return when {
            choices != null -> "<select$control>" + choices.joinToString("") { choice ->
                val selected = if (choice == schema["default"]) " selected" else ""
                "<option value=\"${Html.attr(choice)}\"$selected>${Html.text(choice)}</option>"
            } + "</select>"
            type(schema) == "array" -> "<div class=\"list\" data-list><input type=\"text\"$control></div>" +
                "<button type=\"button\" class=\"add\" data-add>Add another</button>"
            type(schema) in NUMBERS -> "<input type=\"number\"$control${value(schema)}>"
            type(schema) == "string" && name in LONG_TEXT ->
                "<textarea$control>${Html.text(schema["default"] ?: "")}</textarea>"
            else -> "<input type=\"text\"$control${value(schema)}>"
        }
    }

    /** The control's attributes: its id, name, whether it is required, and what its schema bounds. */
    private fun control(id: String, name: String, schema: Map<*, *>, required: Boolean): String = buildString {
        append(Html.attr("id", id)).append(Html.attr("name", name))
        if (required && type(schema) != "boolean") append(" required")
        append(Html.attr("min", schema["minimum"])).append(Html.attr("max", schema["maximum"]))
        when (type(schema)) {
            "integer" -> append(" step=\"1\"")
            "number" -> append(" step=\"any\"")
        }
        append(Html.attr("minlength", schema["minLength"])).append(Html.attr("maxlength", schema["maxLength"]))
        append(Html.attr("pattern", schema["pattern"]))
        append(Html.attr("placeholder", (schema["examples"] as? List<*>)?.firstOrNull()))
    }

    private fun value(schema: Map<*, *>): String = Html.attr("value", schema["default"])

    /** The schema's type: the first named, when it names several (`["string", "null"]`). */
    private fun type(schema: Map<*, *>): String? = when (val type = schema["type"]) {
        is List<*> -> type.firstOrNull { it != "null" }?.toString()
        else -> type?.toString()
    }

    private val NUMBERS = setOf("integer", "number")
}

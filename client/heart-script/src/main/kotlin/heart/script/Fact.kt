package heart.script

/** One fact a check tests: a member, named as `state[...]` names it, and a matcher. */
class Fact(val member: String, val matcher: Matcher)

infix fun String.eq(value: Any?) = Fact(this, equalTo(value))

infix fun String.isNot(value: Any?) = Fact(this, notEqualTo(value))

infix fun String.above(n: Number) = Fact(this, heart.script.above(n))

infix fun String.below(n: Number) = Fact(this, heart.script.below(n))

infix fun String.has(item: Any?) = Fact(this, contains(item))

infix fun String.oneOf(values: Collection<Any?>) = Fact(this, heart.script.oneOf(values))

infix fun String.matching(pattern: Regex) = Fact(this, heart.script.matching(pattern))

package dev.altany.mila.match

/**
 * Reads the intent out of what was said.
 *
 * People don't switch modes before speaking — they say "κάλεσε τον Δημήτρη"
 * and expect a call, whichever button happens to be selected. When the phrase
 * opens with a verb that settles it, that wins over the selected mode; the
 * verb and any article after it are stripped so only the name or destination
 * is left to match.
 */
object SpokenCommand {

    enum class Intent { NAVIGATE, CALL }

    data class Parsed(
        /** Null when nothing in the phrase settles it — fall back to the selected mode. */
        val intent: Intent?,
        val query: String,
    )

    private val callVerbs = setOf(
        "καλεσε", "καλεσε_με", "παρε", "τηλεφωνησε", "τηλεφωνο", "κλησε", "κληση",
    )

    private val navVerbs = setOf(
        "πηγαινε", "πηγαινε_με", "πλοηγηση", "πλοηγησε", "οδηγησε", "παμε", "πλοηγησου",
    )

    /** Articles and pronouns that trail the verb and carry no meaning for us. */
    private val fillers = setOf(
        "το", "τον", "την", "τη", "στο", "στον", "στη", "στην", "σε",
        "μου", "με", "προς", "στα", "στις", "τους", "τις",
    )

    /**
     * Picks the reading that actually carries a command.
     *
     * Greek command verbs are easy to mishear — "κάλεσε" comes back as
     * "θάλασσα" — so if any alternative opens with a verb we trust that one
     * over the recognizer's top pick. Otherwise the best transcription stands.
     */
    fun parseBest(alternatives: List<String>): Parsed {
        alternatives.forEach { candidate ->
            val parsed = parse(candidate)
            if (parsed.intent != null) return parsed
        }
        return parse(alternatives.firstOrNull().orEmpty())
    }

    fun parse(spoken: String): Parsed {
        val words = spoken.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return Parsed(null, spoken.trim())

        val first = GreekText.normalize(words[0])
        val intent = when (first) {
            in callVerbs -> Intent.CALL
            in navVerbs -> Intent.NAVIGATE
            else -> null
        } ?: return Parsed(null, spoken.trim())

        var rest = words.drop(1)
        while (rest.isNotEmpty() && GreekText.normalize(rest[0]) in fillers) {
            rest = rest.drop(1)
        }

        // "κάλεσε" with nothing after it isn't a command we can act on; keep the
        // original text so the caller can report what was actually heard.
        val query = rest.joinToString(" ")
        return if (query.isBlank()) Parsed(null, spoken.trim()) else Parsed(intent, query)
    }
}

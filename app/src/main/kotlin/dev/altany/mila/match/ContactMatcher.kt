package dev.altany.mila.match

/** A contact as far as matching cares: a display name and a number to dial. */
data class Contact(
    val name: String,
    val number: String,
)

data class ScoredContact(
    val contact: Contact,
    val score: Double,
)

/**
 * Matches a spoken name against the phone's contacts.
 *
 * Both sides go through [GreekText.canonicalKey], so "Γιώργος" and "Giorgos"
 * reduce to the same key. Names are also compared token by token, because
 * people say "Μαρία" for a contact saved as "Μαρία Παπαδοπούλου", and say
 * "Γιώργος Νικολάου" when they have three contacts called Γιώργος.
 */
object ContactMatcher {

    /** Below this, a candidate is not worth offering at all. */
    const val MIN_SCORE = 0.62

    /** At or above this, a clear leader can be dialled without asking. */
    const val CONFIDENT_SCORE = 0.86

    /** A leader must beat the runner-up by this much to skip the pick list. */
    const val LEAD_MARGIN = 0.10

    /** How many options the car screen offers when the match is unclear. */
    const val MAX_CANDIDATES = 5

    sealed interface Result {
        /** One clear winner — dial it. */
        data class Single(val contact: Contact, val score: Double) : Result

        /** Several plausible names — let the driver pick. */
        data class Choice(val candidates: List<ScoredContact>) : Result

        /** Nothing close enough. */
        data object None : Result
    }

    fun match(spoken: String, contacts: List<Contact>): Result {
        val queryVariants = GreekText.variants(GreekText.canonicalKey(spoken))
        if (queryVariants.all { it.isBlank() }) return Result.None

        val scored = contacts
            .map { ScoredContact(it, score(queryVariants, it.name)) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) return Result.None

        val best = scored[0]
        val runnerUp = scored.getOrNull(1)
        val clearLeader = runnerUp == null || best.score - runnerUp.score >= LEAD_MARGIN

        return if (best.score >= CONFIDENT_SCORE && clearLeader) {
            Result.Single(best.contact, best.score)
        } else {
            Result.Choice(scored.take(MAX_CANDIDATES))
        }
    }

    /** Best similarity between any query variant and any reading of the name. */
    fun score(queryVariants: List<String>, contactName: String): Double {
        val nameVariants = GreekText.variants(GreekText.canonicalKey(contactName))
        var best = 0.0
        for (q in queryVariants) {
            if (q.isBlank()) continue
            for (n in nameVariants) {
                if (n.isBlank()) continue
                best = maxOf(best, pairScore(q, n))
                if (best == 1.0) return 1.0
            }
        }
        return best
    }

    private fun pairScore(query: String, name: String): Double {
        val whole = GreekText.similarity(query, name)

        val queryTokens = query.split(' ').filter { it.isNotBlank() }
        val nameTokens = name.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty() || nameTokens.isEmpty()) return whole

        // Every spoken token has to find a home in the contact's name; a
        // first-name-only query then scores on how well that one name matches,
        // not on how much of the full name it failed to cover.
        val available = nameTokens.toMutableList()
        var total = 0.0
        for (qt in queryTokens) {
            val bestIndex = available.indices.maxByOrNull {
                GreekText.similarity(qt, available[it])
            }
            if (bestIndex == null) {
                total += 0.0
            } else {
                total += GreekText.similarity(qt, available[bestIndex])
                available.removeAt(bestIndex)
            }
        }
        val tokenScore = total / queryTokens.size

        // Naming only part of a longer contact is normal, but a one-word query
        // against a long full name is weaker evidence than an exact hit.
        val coverage = queryTokens.size.toDouble() / nameTokens.size
        val partialPenalty = if (coverage < 1.0) 0.04 else 0.0

        return maxOf(whole, tokenScore - partialPenalty)
    }
}

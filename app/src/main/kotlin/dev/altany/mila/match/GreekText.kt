package dev.altany.mila.match

import java.text.Normalizer

/**
 * Normalization and Greek→Latin transliteration so a spoken Greek name can
 * match a contact saved in Greek, in Greeklish, or in plain Latin.
 *
 * Both sides are reduced to a canonical Latin "phonetic key": Greek text via
 * a fixed transliteration (η/ι/υ/ει/οι all become i, ω/ο become o, μπ→b, …),
 * Latin text via the common Greeklish conventions (ch→h, ph→f, w→o, x→ks).
 * Genuinely ambiguous Greeklish letters (h can be the sound h or the letter η,
 * b can be μπ or β, u can be ου or υ) expand into a small set of variant keys,
 * and matching takes the best-scoring variant.
 */
object GreekText {

    /** Lowercase, strip accents/diacritics, unify final sigma. */
    fun normalize(input: String): String {
        val lower = input.lowercase()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
        return stripped.replace('ς', 'σ').trim()
    }

    private val greekDigraphs = listOf(
        "αι" to "e",
        "ει" to "i",
        "οι" to "i",
        "υι" to "i",
        "ου" to "u",
        "μπ" to "b",
        "ντ" to "d",
        "γκ" to "g",
        "γγ" to "g",
        "τσ" to "ts",
        "τζ" to "tz",
    )

    private val greekLetters = mapOf(
        'α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e",
        'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k",
        'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "ks", 'ο' to "o",
        'π' to "p", 'ρ' to "r", 'σ' to "s", 'τ' to "t", 'υ' to "i",
        'φ' to "f", 'χ' to "h", 'ψ' to "ps", 'ω' to "o",
    )

    private val latinDigraphs = listOf(
        "ch" to "h",
        "ph" to "f",
        // Greeklish vowel clusters, mirroring the Greek digraphs above.
        "ou" to "u",
        "oy" to "u",
        // These can also be two separate vowels ("Mixail" = Μιχαήλ), so they
        // stay ambiguous rather than collapsing outright.
        "ai" to "E",
        "ei" to "I",
        "oi" to "J",
    )

    /**
     * Canonical phonetic key. Works on mixed Greek/Latin input. Ambiguous
     * Greeklish letters are emitted as uppercase markers for [variants].
     */
    fun canonicalKey(input: String): String {
        val text = normalize(input)
        val out = StringBuilder()
        var i = 0
        outer@ while (i < text.length) {
            val c = text[i]
            if (c in 'α'..'ω') {
                for ((digraph, repl) in greekDigraphs) {
                    if (text.startsWith(digraph, i)) {
                        out.append(repl)
                        i += digraph.length
                        continue@outer
                    }
                }
                out.append(greekLetters[c] ?: c)
                i++
            } else if (c in 'a'..'z') {
                for ((digraph, repl) in latinDigraphs) {
                    if (text.startsWith(digraph, i)) {
                        out.append(repl)
                        i += digraph.length
                        continue@outer
                    }
                }
                when (c) {
                    'w' -> out.append('o')
                    'y' -> out.append('i')
                    'c' -> out.append('k')
                    // Ambiguous in Greeklish; resolved by variant expansion.
                    'h' -> out.append('H')
                    'b' -> out.append('B')
                    'u' -> out.append('U')
                    'x' -> out.append('X')
                    else -> out.append(c)
                }
                i++
            } else {
                if (c == ' ' || c.isDigit()) out.append(c)
                i++
            }
        }
        return out.toString().replace(Regex(" +"), " ").trim()
    }

    private val ambiguous = mapOf(
        'H' to listOf("h", "i"),  // "Hristos" (χ) vs "Xrhstos" (η)
        'B' to listOf("b", "v"),  // "Babis" (μπ) vs "Basilis" (β)
        'U' to listOf("u", "i"),  // "Loukas" (ου) vs "Kuriakos" (υ)
        'X' to listOf("h", "ks"), // "Xristos" (χ) vs "Xenia" (ξ)
        'E' to listOf("e", "ai"), // "Kaiti" (αι) vs "Mixail" (α + η)
        'I' to listOf("i", "ei"), // "Eirini" (ει) vs two vowels
        'J' to listOf("i", "oi"), // "Oikonomou" (οι) vs two vowels
    )

    /** All concrete keys for a canonical key with ambiguity markers. */
    fun variants(canonical: String): List<String> {
        var results = listOf(StringBuilder())
        for (c in canonical) {
            val options = ambiguous[c]
            results = if (options == null) {
                results.onEach { it.append(c) }
            } else {
                results.flatMap { sb -> options.map { StringBuilder(sb).append(it) } }
                    .take(MAX_VARIANTS)
            }
        }
        return results.map { it.toString() }
    }

    /** Cap on variant expansion, so a name full of ambiguous letters can't blow up. */
    const val MAX_VARIANTS = 32

    /** Levenshtein-based similarity in [0, 1]. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return prev[b.length]
    }
}

package dev.altany.mila.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreekTextTest {

    private fun keysMatch(a: String, b: String): Boolean {
        val va = GreekText.variants(GreekText.canonicalKey(a))
        val vb = GreekText.variants(GreekText.canonicalKey(b))
        return va.any { it in vb }
    }

    @Test
    fun `normalize strips accents and case`() {
        assertEquals("μαρια", GreekText.normalize("Μαρία"))
        assertEquals("μαρια", GreekText.normalize("μαρια"))
        assertEquals("ιωαννησ", GreekText.normalize("Ιωάννης"))
    }

    @Test
    fun `normalize unifies final sigma`() {
        assertEquals(
            GreekText.normalize("Κώστας"),
            GreekText.normalize("ΚΩΣΤΑΣ"),
        )
    }

    @Test
    fun `accented and unaccented Greek produce the same key`() {
        assertEquals(
            GreekText.canonicalKey("Μαρία"),
            GreekText.canonicalKey("μαρια"),
        )
    }

    @Test
    fun `Greek names match their Greeklish spellings`() {
        assertTrue(keysMatch("Γιώργος", "Giorgos"))
        assertTrue(keysMatch("Μαρία", "Maria"))
        assertTrue(keysMatch("Κώστας", "Kostas"))
        assertTrue(keysMatch("Ελένη", "Eleni"))
        assertTrue(keysMatch("Νίκος", "Nikos"))
        assertTrue(keysMatch("Δημήτρης", "Dimitris"))
        assertTrue(keysMatch("Θανάσης", "Thanasis"))
        assertTrue(keysMatch("Σοφία", "Sofia"))
    }

    @Test
    fun `ambiguous Greeklish letters resolve through variants`() {
        // h as the sound of χ, and h standing in for η
        assertTrue(keysMatch("Χρήστος", "Xristos"))
        assertTrue(keysMatch("Χρήστος", "Christos"))
        assertTrue(keysMatch("Χρήστος", "Xrhstos"))
        // b as μπ and as β
        assertTrue(keysMatch("Μπάμπης", "Babis"))
        assertTrue(keysMatch("Βασίλης", "Basilis"))
        assertTrue(keysMatch("Βασίλης", "Vasilis"))
        // u as ου and as υ
        assertTrue(keysMatch("Λουκάς", "Loukas"))
        assertTrue(keysMatch("Κυριάκος", "Kuriakos"))
    }

    @Test
    fun `w and y Greeklish conventions map onto Greek`() {
        assertTrue(keysMatch("Γιώργος", "Giwrgos"))
        assertTrue(keysMatch("Κυριάκος", "Kyriakos"))
    }

    @Test
    fun `digraphs collapse to single sounds`() {
        assertEquals("bab", GreekText.canonicalKey("μπαμπ"))
        assertEquals("dad", GreekText.canonicalKey("ντατ").replace('t', 'd'))
        assertEquals("i", GreekText.canonicalKey("ει"))
        assertEquals("i", GreekText.canonicalKey("οι"))
        assertEquals("u", GreekText.canonicalKey("ου"))
    }

    @Test
    fun `different names do not collide`() {
        assertTrue(!keysMatch("Μαρία", "Γιώργος"))
        assertTrue(!keysMatch("Ελένη", "Kostas"))
    }

    @Test
    fun `similarity is bounded and sane`() {
        assertEquals(1.0, GreekText.similarity("maria", "maria"), 0.0001)
        assertEquals(0.0, GreekText.similarity("", "maria"), 0.0001)
        assertTrue(GreekText.similarity("maria", "marina") > 0.6)
        assertTrue(GreekText.similarity("maria", "giorgos") < 0.4)
    }

    @Test
    fun `Greeklish vowel clusters match their Greek digraphs`() {
        assertTrue(keysMatch("Λουκάς", "Loukas"))
        assertTrue(keysMatch("Λουκάς", "Loykas"))
        assertTrue(keysMatch("Καίτη", "Kaiti"))
        assertTrue(keysMatch("Ειρήνη", "Eirini"))
        assertTrue(keysMatch("Οικονόμου", "Oikonomou"))
    }

    @Test
    fun `adjacent vowels that are not a digraph still match`() {
        // Μιχαήλ is α + η, not the αι digraph.
        assertTrue(keysMatch("Μιχαήλ", "Mixail"))
    }

    @Test
    fun `x covers both chi and xi spellings`() {
        assertTrue(keysMatch("Χαρά", "Xara"))
        assertTrue(keysMatch("Ξένια", "Xenia"))
    }

    @Test
    fun `variant expansion stays small`() {
        val key = GreekText.canonicalKey("Bhbhbhbhxuxu")
        assertTrue(GreekText.variants(key).size <= GreekText.MAX_VARIANTS)
    }
}

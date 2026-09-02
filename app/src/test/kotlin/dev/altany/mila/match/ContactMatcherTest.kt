package dev.altany.mila.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {

    private val contacts = listOf(
        Contact("Μαρία Παπαδοπούλου", "+306900000001"),
        Contact("Giorgos Nikolaou", "+306900000002"),
        Contact("Γιώργος Ανδρέου", "+306900000003"),
        Contact("Kostas Dimitriou", "+306900000004"),
        Contact("Ελένη Βασιλείου", "+306900000005"),
        Contact("Babis Petrou", "+306900000006"),
    )

    private fun single(result: ContactMatcher.Result): Contact {
        assertTrue("expected a single match, got $result", result is ContactMatcher.Result.Single)
        return (result as ContactMatcher.Result.Single).contact
    }

    @Test
    fun `exact Greek name dials directly`() {
        val result = ContactMatcher.match("Μαρία Παπαδοπούλου", contacts)
        assertEquals("+306900000001", single(result).number)
    }

    @Test
    fun `spoken Greek matches a contact saved in Greeklish`() {
        val result = ContactMatcher.match("Κώστας Δημητρίου", contacts)
        assertEquals("+306900000004", single(result).number)
    }

    @Test
    fun `first name only matches when it is unambiguous`() {
        val result = ContactMatcher.match("Ελένη", contacts)
        assertEquals("+306900000005", single(result).number)
    }

    @Test
    fun `accent and case are ignored`() {
        val result = ContactMatcher.match("μαρια παπαδοπουλου", contacts)
        assertEquals("+306900000001", single(result).number)
    }

    @Test
    fun `Greeklish mp spelling matches`() {
        val result = ContactMatcher.match("Μπάμπης", contacts)
        assertEquals("+306900000006", single(result).number)
    }

    @Test
    fun `two contacts sharing a first name produce a pick list`() {
        val result = ContactMatcher.match("Γιώργος", contacts)
        assertTrue("expected a choice, got $result", result is ContactMatcher.Result.Choice)
        val numbers = (result as ContactMatcher.Result.Choice)
            .candidates.map { it.contact.number }
        assertTrue(numbers.contains("+306900000002"))
        assertTrue(numbers.contains("+306900000003"))
    }

    @Test
    fun `surname disambiguates between two people with the same first name`() {
        val result = ContactMatcher.match("Γιώργος Νικολάου", contacts)
        assertEquals("+306900000002", single(result).number)
    }

    @Test
    fun `unrelated speech matches nobody`() {
        val result = ContactMatcher.match("πλατεία συντάγματος", contacts)
        assertEquals(ContactMatcher.Result.None, result)
    }

    @Test
    fun `empty input matches nobody`() {
        assertEquals(ContactMatcher.Result.None, ContactMatcher.match("", contacts))
    }

    @Test
    fun `empty contact list matches nobody`() {
        assertEquals(ContactMatcher.Result.None, ContactMatcher.match("Μαρία", emptyList()))
    }

    @Test
    fun `pick list is capped and ordered by score`() {
        val many = (1..20).map { Contact("Γιώργος Επώνυμο$it", "+3069000000$it") }
        val result = ContactMatcher.match("Γιώργος", many)
        assertTrue(result is ContactMatcher.Result.Choice)
        val candidates = (result as ContactMatcher.Result.Choice).candidates
        assertTrue(candidates.size <= ContactMatcher.MAX_CANDIDATES)
        assertEquals(candidates.sortedByDescending { it.score }, candidates)
    }

    @Test
    fun `a contact named only in a later alternative is still found`() {
        val result = ContactMatcher.match(
            listOf("Ελένη Βασιλείου", "Ελένη Βασιλείου"),
            contacts,
        )
        assertEquals("+306900000005", single(result).number)
    }

    @Test
    fun `the best scoring alternative wins`() {
        // The top transcription is nobody; the second is an exact contact.
        val result = ContactMatcher.match(
            listOf("θάλασσα καφέ", "Kostas Dimitriou"),
            contacts,
        )
        assertEquals("+306900000004", single(result).number)
    }

    @Test
    fun `alternatives that match nobody still match nobody`() {
        val result = ContactMatcher.match(
            listOf("θάλασσα καφέ μπαρ", "πλατεία γεωργίου"),
            contacts,
        )
        assertEquals(ContactMatcher.Result.None, result)
    }

    @Test
    fun `a favourite wins among equally matching namesakes`() {
        val dimitrides = listOf(
            Contact("Δημήτρης Σωτηρόπουλος", "+306900000010"),
            Contact("Δημήτρης", "+306900000011", isFavourite = true),
            Contact("Δημήτρης Χατζάκος", "+306900000012"),
        )
        val result = ContactMatcher.match("Δημήτρη", dimitrides)
        assertEquals("+306900000011", single(result).number)
    }

    @Test
    fun `naming a non-favourite still reaches them`() {
        val dimitrides = listOf(
            Contact("Δημήτρης Σωτηρόπουλος", "+306900000010"),
            Contact("Δημήτρης", "+306900000011", isFavourite = true),
            Contact("Δημήτρης Χατζάκος", "+306900000012"),
        )
        val result = ContactMatcher.match("Δημήτρης Χατζάκος", dimitrides)
        assertEquals("+306900000012", single(result).number)
    }

    @Test
    fun `an unrelated favourite is not pulled into the running`() {
        val people = listOf(
            Contact("Ελένη Βασιλείου", "+306900000005"),
            Contact("Γιώργος Ανδρέου", "+306900000003", isFavourite = true),
        )
        assertEquals("+306900000005", single(ContactMatcher.match("Ελένη", people)).number)
    }

    @Test
    fun `a small mishearing still finds the contact`() {
        // The recognizer often returns "Μαρια Παπαδοπουλου" with a dropped letter.
        val result = ContactMatcher.match("Μαρία Παπαδόπουλου", contacts)
        assertEquals("+306900000001", single(result).number)
    }
}

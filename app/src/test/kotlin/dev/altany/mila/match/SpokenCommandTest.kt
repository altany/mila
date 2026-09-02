package dev.altany.mila.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenCommandTest {

    @Test
    fun `call verb wins over the selected mode`() {
        val parsed = SpokenCommand.parse("κάλεσε το Δημήτρη")
        assertEquals(SpokenCommand.Intent.CALL, parsed.intent)
        assertEquals("Δημήτρη", parsed.query)
    }

    @Test
    fun `other call verbs are recognized`() {
        assertEquals(SpokenCommand.Intent.CALL, SpokenCommand.parse("πάρε τη Μαρία").intent)
        assertEquals(SpokenCommand.Intent.CALL, SpokenCommand.parse("τηλεφώνησε στον Κώστα").intent)
    }

    @Test
    fun `articles after the verb are stripped`() {
        assertEquals("Μαρία", SpokenCommand.parse("πάρε τη Μαρία").query)
        assertEquals("Κώστα", SpokenCommand.parse("τηλεφώνησε στον Κώστα").query)
        assertEquals("Δημήτρη", SpokenCommand.parse("κάλεσε τον Δημήτρη").query)
    }

    @Test
    fun `multi-word call phrases are stripped whole`() {
        // Heard in the car: "πάρε τηλέφωνο το Δημήτρη" searched for
        // "τηλέφωνο του δημήτρη" and matched nobody.
        val parsed = SpokenCommand.parse("πάρε τηλέφωνο το Δημήτρη")
        assertEquals(SpokenCommand.Intent.CALL, parsed.intent)
        assertEquals("Δημήτρη", parsed.query)
    }

    @Test
    fun `genitive articles are dropped`() {
        assertEquals("Δημήτρη", SpokenCommand.parse("πάρε τηλέφωνο του Δημήτρη").query)
        assertEquals("Μαρίας", SpokenCommand.parse("κάλεσε της Μαρίας").query)
    }

    @Test
    fun `a verb phrase with no name left is not a command`() {
        assertNull(SpokenCommand.parse("πάρε τηλέφωνο").intent)
    }

    @Test
    fun `navigation verb routes to navigate`() {
        val parsed = SpokenCommand.parse("πήγαινε στην Πάτρα")
        assertEquals(SpokenCommand.Intent.NAVIGATE, parsed.intent)
        assertEquals("Πάτρα", parsed.query)
    }

    @Test
    fun `a plain address has no intent and keeps its text`() {
        val parsed = SpokenCommand.parse("Κανακάρη 46 Πάτρα")
        assertNull(parsed.intent)
        assertEquals("Κανακάρη 46 Πάτρα", parsed.query)
    }

    @Test
    fun `a bare name has no intent`() {
        assertNull(SpokenCommand.parse("Μαρία Παπαδοπούλου").intent)
    }

    @Test
    fun `accents and case do not matter for the verb`() {
        assertEquals(SpokenCommand.Intent.CALL, SpokenCommand.parse("ΚΑΛΕΣΕ ΤΟΝ ΝΙΚΟ").intent)
        assertEquals(SpokenCommand.Intent.CALL, SpokenCommand.parse("καλεσε τον Νίκο").intent)
    }

    @Test
    fun `a verb with nothing after it is not treated as a command`() {
        val parsed = SpokenCommand.parse("κάλεσε")
        assertNull(parsed.intent)
        assertEquals("κάλεσε", parsed.query)
    }

    @Test
    fun `a verb followed only by articles is not a command`() {
        assertNull(SpokenCommand.parse("κάλεσε τον").intent)
    }

    @Test
    fun `a command hiding in a later alternative is preferred`() {
        // The recognizer's top pick mishears "κάλεσε" as "θάλασσα", which sent
        // the driver to a beach bar instead of the phone.
        val parsed = SpokenCommand.parseBest(
            listOf("θάλασσα του Δημήτρη", "κάλεσε το Δημήτρη", "καλέ σε το Δημήτρη")
        )
        assertEquals(SpokenCommand.Intent.CALL, parsed.intent)
        assertEquals("Δημήτρη", parsed.query)
    }

    @Test
    fun `the top alternative stands when none carries a command`() {
        val parsed = SpokenCommand.parseBest(listOf("Κανακάρη 46", "Κανακάρη 40"))
        assertNull(parsed.intent)
        assertEquals("Κανακάρη 46", parsed.query)
    }

    @Test
    fun `the top alternative wins when it already carries a command`() {
        val parsed = SpokenCommand.parseBest(
            listOf("κάλεσε τη Μαρία", "κάλεσε τον Μάριο")
        )
        assertEquals("Μαρία", parsed.query)
    }

    @Test
    fun `no alternatives is safe`() {
        assertNull(SpokenCommand.parseBest(emptyList()).intent)
    }

    @Test
    fun `empty input is safe`() {
        val parsed = SpokenCommand.parse("   ")
        assertNull(parsed.intent)
    }

    @Test
    fun `a destination that merely contains a verb later is untouched`() {
        // Only the opening word decides; a street name keeps its full text.
        val parsed = SpokenCommand.parse("Πλατεία Γεωργίου")
        assertNull(parsed.intent)
        assertEquals("Πλατεία Γεωργίου", parsed.query)
    }
}

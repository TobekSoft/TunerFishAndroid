package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The filing rules, ported case for case from the desktop app's MainViewModelFilingTests.
 * If one of these changes, the desktop test must change the same way (and vice versa).
 */
class SortOrderTest {
    @Test
    fun plainLetters_dropAccents_andSpellOutLettersThatDontDecompose() {
        listOf(
            "Björk" to "Bjork",
            "Éléonore Señor" to "Eleonore Senor",
            "Straße" to "Strasse",
            "Æon Œuvre" to "AEon OEuvre",
            "Ørjan Łódź Đorđe Þór" to "Orjan Lodz Dorde THor",
            "АББА" to "АББА", // non-Latin is left alone
        ).forEach { (text, expected) -> assertEquals(expected, SortOrder.plainLetters(text), text) }
    }

    @Test
    fun groupOf_isTheFirstPlainLetter_orHash() {
        listOf(
            "beatles" to "B",
            "élan" to "E",
            "øystein" to "O",
            "10cc" to "#",
            "+++ (crosses)" to "#",
            "...and you will know us" to "#",
            "абба" to "#",
            "" to "#",
        ).forEach { (key, expected) -> assertEquals(expected, SortOrder.groupOf(key), key) }
    }

    @Test
    fun comparer_symbolsThenDigitsThenLetters_positionByPosition() {
        listOf(
            "+++" to "10cc",       // symbols before digits
            "1999" to "abba",      // digits before letters
            "абба" to "1999",      // non-Latin counts as a symbol
            "ab cd" to "ab1",      // position by position, so a space comes first
            "ab1" to "abc",
            "abba" to "Abba Gold", // case is ignored, and a shorter key comes first
            "eagles" to "Élan",    // É counts as E
            "strasse" to "straße x",
        ).forEach { (first, second) ->
            assertTrue(SortOrder.compare(first, second) < 0, "$first should sort before $second")
            assertTrue(SortOrder.compare(second, first) > 0, "$second should sort after $first")
        }
    }

    @Test
    fun comparer_treatsAccentsAndCaseAsEqual() {
        assertEquals(0, SortOrder.compare("Björk", "bjork"))
    }

    @Test
    fun groups_areHashThenAToZ() {
        assertEquals("# A B C D E F G H I J K L M N O P Q R S T U V W X Y Z", SortOrder.groups.joinToString(" "))
    }

    @Test
    fun keyFor_usesTheTrimmedSortName_elseTheName() {
        listOf(
            Triple("Elvis Costello", "costello, elvis", "costello, elvis"),
            Triple("Elvis Costello", "  costello  ", "costello"),
            Triple("Elvis Costello", null, "Elvis Costello"),
            Triple("Elvis Costello", "   ", "Elvis Costello"),
        ).forEach { (name, sortName, expected) ->
            assertEquals(expected, SortOrder.keyFor(BaseItem(name = name, sortName = sortName)))
        }
    }
}

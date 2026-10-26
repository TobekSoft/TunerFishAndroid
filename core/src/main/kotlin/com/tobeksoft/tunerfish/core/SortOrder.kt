package com.tobeksoft.tunerfish.core

import java.text.Normalizer

/**
 * How artists and albums are filed: by Jellyfin's sort name (which already ignores leading
 * articles, and which you can change in Jellyfin's metadata editor). Display names never change.
 *
 * The plain-letter rule: for sorting and for the A–Z jump bar only, accented letters count as
 * their plain letter (É → E, ß → ss). Then symbols sort before digits, and digits before letters,
 * position by position, so every jump-bar group is one unbroken block of the list. Non-Latin
 * scripts aren't supported: they count as symbols and land in the "#" group.
 *
 * This is a port of the desktop app's SortOrder (TunerFish.Core/Services/SortOrder.cs) and must
 * give exactly the same order. Change both or neither; SortOrderTest mirrors the desktop tests.
 */
object SortOrder {
    /** The jump-bar group for everything that doesn't start with a letter A–Z. */
    const val OTHER_GROUP = "#"

    /** The jump-bar groups in bar order: "#", then "A" to "Z". */
    val groups: List<String> = listOf(OTHER_GROUP) + ('A'..'Z').map { it.toString() }

    val comparator: Comparator<String> = Comparator(::compare)

    /** The key an item files under: Jellyfin's sort name, or its name if the server sent none. */
    fun keyFor(item: BaseItem): String =
        if (item.sortName.isNullOrBlank()) item.name.trim() else item.sortName.trim()

    /** The jump-bar group of a sort key: its first plain letter in capitals, or "#". */
    fun groupOf(key: String): String {
        val plain = plainLetters(key)
        if (plain.isEmpty()) return OTHER_GROUP
        val first = plain[0].uppercaseChar()
        return if (first in 'A'..'Z') first.toString() else OTHER_GROUP
    }

    /**
     * The text with accents removed (É → E, ñ → n) and the letters that don't decompose spelled out
     * (ß → ss, Æ → AE, Ø → O, ...). Used only for sorting and grouping, never shown.
     */
    fun plainLetters(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in Normalizer.normalize(text, Normalizer.Form.NFD)) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(
                when (c) {
                    'ß' -> "ss"
                    'Æ' -> "AE"; 'æ' -> "ae"
                    'Œ' -> "OE"; 'œ' -> "oe"
                    'Ø' -> "O"; 'ø' -> "o"
                    'Ł' -> "L"; 'ł' -> "l"
                    'Đ', 'Ð' -> "D"; 'đ', 'ð' -> "d"
                    'Þ' -> "TH"; 'þ' -> "th"
                    else -> c.toString()
                }
            )
        }
        return sb.toString()
    }

    /**
     * Compares plain-letter keys character by character: symbols (including anything non-Latin),
     * then digits 0–9, then letters A–Z; within a class by character code, ignoring case.
     * A key that's the start of another sorts first.
     */
    fun compare(x: String, y: String): Int {
        val a = plainLetters(x)
        val b = plainLetters(y)
        for (i in 0 until minOf(a.length, b.length)) {
            val ca = a[i].uppercaseChar()
            val cb = b[i].uppercaseChar()
            if (ca == cb) continue
            val byClass = classOf(ca).compareTo(classOf(cb))
            return if (byClass != 0) byClass else ca.compareTo(cb)
        }
        return a.length.compareTo(b.length)
    }

    private fun classOf(upper: Char): Int = when (upper) {
        in 'A'..'Z' -> 2
        in '0'..'9' -> 1
        else -> 0
    }
}

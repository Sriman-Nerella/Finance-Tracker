package com.example.financetracker.ai.parse

import com.example.financetracker.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExtractorTest {

    private fun minor(text: String): Long? = AmountExtractor.extract(text)?.money?.minor

    @Test fun suffixMarkers() {
        assertEquals(1500L, minor("tea 15rs"))
        assertEquals(1500L, minor("tea 15 rs"))
        assertEquals(1500L, minor("15/-"))
        assertEquals(1500L, minor("tea 15 inr"))
        assertEquals(20000L, minor("lunch 200 rupees"))
        assertEquals(20000L, minor("lunch 200 bucks"))
    }

    @Test fun prefixMarkers() {
        assertEquals(1500L, minor("rs 15"))
        assertEquals(1500L, minor("rs.15"))
        assertEquals(1500L, minor("Rs15"))
        assertEquals(1500L, minor("tea ₹15"))
        assertEquals(125050L, minor("₹ 1,250.50"))
    }

    @Test fun kiloSuffix() {
        assertEquals(150000L, minor("petrol 1.5k"))
        assertEquals(200000L, minor("rent 2k"))
    }

    @Test fun bareNumberOnlyWhenSingle() {
        assertEquals(1500L, minor("tea 15"))
        assertNull(minor("5 tea 10"))
        assertEquals(20000L, minor("movie 3d 200")) // "3d" is not a number
    }

    @Test fun markerWinsOverOtherNumbers() {
        val m = AmountExtractor.extract("2 coffees 120rs")
        assertNotNull(m)
        assertEquals(Money(12000), m!!.money)
        assertEquals("2 coffees ", "2 coffees 120rs".substring(0, m.start))
        assertEquals("120rs", "2 coffees 120rs".substring(m.start, m.endExclusive))
    }

    @Test fun rejectsZeroNegativeAndMissing() {
        assertNull(minor("tea 0"))
        assertNull(minor("tea 0rs"))
        assertNull(minor("tea -50"))
        assertNull(minor("tea"))
        assertNull(minor(""))
    }

    @Test fun thousandsSeparator() {
        assertEquals(125000L, minor("rent 1,250rs"))
    }
}

package com.example.financetracker.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameAuthTest {

    @Test
    fun signUpAcceptsValidDetails() {
        assertNull(UsernameAuth.signUpProblem("  Priya_92 ", "Budget2026"))
    }

    @Test
    fun signUpRejectsBadUsernames() {
        assertNotNull(UsernameAuth.signUpProblem("ab", "Longenough1"))
        assertNotNull(UsernameAuth.signUpProblem("has space", "Longenough1"))
        assertNotNull(UsernameAuth.signUpProblem("a".repeat(21), "Longenough1"))
    }

    @Test
    fun signUpRejectsWeakPasswords() {
        assertNotNull(UsernameAuth.signUpProblem("priya", "Abcde12")) // 7 chars
        assertNotNull(UsernameAuth.signUpProblem("priya", "abcdefg1")) // no uppercase
        assertNotNull(UsernameAuth.signUpProblem("priya", "ABCDEFG1")) // no lowercase
        assertNotNull(UsernameAuth.signUpProblem("priya", "Abcdefgh")) // no number
        assertNotNull(UsernameAuth.signUpProblem("priya", "        "))
    }

    @Test
    fun checklistReportsEachRule() {
        val met = UsernameAuth.PASSWORD_RULES.map { it.isMet("abc1") }
        assertEquals(listOf(false, false, true, true), met)
        assertTrue(UsernameAuth.isStrongPassword("Budget2026"))
    }

    @Test
    fun usernameMapsToHiddenEmailAndBack() {
        val email = UsernameAuth.toEmail(" Priya ")
        assertEquals("priya@${UsernameAuth.EMAIL_DOMAIN}", email)
        assertEquals("priya", UsernameAuth.fromEmail(email))
        assertNull(UsernameAuth.fromEmail("someone@gmail.com"))
    }
}

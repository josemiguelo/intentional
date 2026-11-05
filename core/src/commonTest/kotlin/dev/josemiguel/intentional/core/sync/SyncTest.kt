package dev.josemiguel.intentional.core.sync

import dev.josemiguel.intentional.core.AppGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class VaultTest {

    private val key = Vault.newKey()

    @Test
    fun sealsAndOpens() {
        val vault = Vault(key)
        val sealed = vault.seal("""{"sites":{"x.com":1}}""")
        assertNotEquals("""{"sites":{"x.com":1}}""", sealed)
        assertEquals("""{"sites":{"x.com":1}}""", Vault(key).open(sealed))
    }

    @Test
    fun anotherKeyCantOpen() {
        val sealed = Vault(key).seal("secret")
        assertFailsWith<Exception> { Vault(Vault.newKey()).open(sealed) }
    }

    @Test
    fun groupIdsAreStablePerKeyAndHideTheName() {
        val id = Vault(key).groupId("youtube")
        assertEquals(id, Vault(key).groupId("youtube"))
        assertNotEquals(id, Vault(key).groupId("reddit"))
        assertNotEquals(id, Vault(Vault.newKey()).groupId("youtube"))
        assertEquals(24, id.length)
    }

    @Test
    fun keysMustBe32Bytes() {
        assertFailsWith<IllegalArgumentException> { Vault("c2hvcnQ=") }
    }
}

class LimitsTest {

    @Test
    fun theServersLimitsWin() {
        val groups = listOf(AppGroup("youtube", emptySet(), 60, 0L), AppGroup("x", emptySet(), 30, 5L))
        val limited = groups.withLimits(mapOf("id-youtube" to Limit(20, 1_000L))) { "id-$it" }
        assertEquals(AppGroup("youtube", emptySet(), 20, 1_000L), limited[0])
        assertEquals(groups[1], limited[1])
    }
}

class ScrubTest {

    @Test
    fun tokensNeverReachTheMessage() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyX2lkIjoidTEifQ.c2lnbmF0dXJl"
        val curl = "Connection failed for request: CurlRequestData(url='https://offline.invalid/users/u1/groups.json?auth=$jwt', method='GET')"
        val clean = scrubbed(curl)
        assertEquals("Connection failed for request: CurlRequestData(url='https://offline.invalid/users/u1/groups.json?auth=…', method='GET')", clean)
        assertEquals("token … expired", scrubbed("token $jwt expired"))
        assertEquals("Unable to resolve host \"x.firebaseio.com\"", scrubbed("Unable to resolve host \"x.firebaseio.com\""))
    }
}

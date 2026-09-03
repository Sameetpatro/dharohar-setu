package com.example.humsafar

import com.example.humsafar.utils.QrCodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeParserTest {

    @Test
    fun extractNodeIdentifier_plainNodeId_returnsSame() {
        assertEquals("NODE_1", QrCodeParser.extractNodeIdentifier("NODE_1"))
        assertEquals("IIITS-0-KING", QrCodeParser.extractNodeIdentifier("IIITS-0-KING"))
        assertEquals("IIIT-1-CANTEEN", QrCodeParser.extractNodeIdentifier("IIIT-1-CANTEEN"))
        assertEquals("QTB-0-KING", QrCodeParser.extractNodeIdentifier("QTB-0-KING"))
    }

    @Test
    fun extractNodeIdentifier_httpsUrl_extractsNodeId() {
        assertEquals(
            "NODE_1",
            QrCodeParser.extractNodeIdentifier("https://my.domain.vercel.app/node/NODE_1")
        )
        assertEquals(
            "IIITS-0-KING",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/IIITS-0-KING")
        )
        assertEquals(
            "12345",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/12345")
        )
        assertEquals(
            "ABC_123",
            QrCodeParser.extractNodeIdentifier("https://custom.domain.com/node/ABC_123")
        )
    }

    @Test
    fun extractNodeIdentifier_trailingSlash_handledCorrectly() {
        assertEquals(
            "NODE_1",
            QrCodeParser.extractNodeIdentifier("https://my.domain.vercel.app/node/NODE_1/")
        )
        assertEquals(
            "IIITS-0-KING",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/IIITS-0-KING/")
        )
        assertEquals(
            "12345",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/12345/")
        )
    }

    @Test
    fun extractNodeIdentifier_withQueryParamsAndFragments_stripsExtra() {
        assertEquals(
            "NODE_1",
            QrCodeParser.extractNodeIdentifier("https://my.domain.vercel.app/node/NODE_1?source=camera&ref=poster")
        )
        assertEquals(
            "IIITS-0-KING",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/IIITS-0-KING#overview")
        )
        assertEquals(
            "12345",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/12345?source=lens&ref=poster#details")
        )
        assertEquals(
            "NODE_2",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/NODE_2?lang=hi#top")
        )
    }

    @Test
    fun extractNodeIdentifier_urlEncoded_decodesProperly() {
        assertEquals(
            "IIIT-1-CANTEEN",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/IIIT%2D1%2DCANTEEN")
        )
        assertEquals(
            "NODE 1",
            QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/NODE%201")
        )
    }

    @Test
    fun extractNodeIdentifier_whitespace_trimmed() {
        assertEquals(
            "NODE_1",
            QrCodeParser.extractNodeIdentifier("   https://humsafar.vercel.app/node/NODE_1   ")
        )
        assertEquals(
            "12345",
            QrCodeParser.extractNodeIdentifier("   https://humsafar.vercel.app/node/12345   ")
        )
        assertEquals(
            "IIITS-0-KING",
            QrCodeParser.extractNodeIdentifier("   IIITS-0-KING   ")
        )
    }

    @Test
    fun extractNodeIdentifier_invalidOrEmpty_returnsNull() {
        assertNull(QrCodeParser.extractNodeIdentifier(null))
        assertNull(QrCodeParser.extractNodeIdentifier(""))
        assertNull(QrCodeParser.extractNodeIdentifier("   "))
        assertNull(QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/"))
        assertNull(QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node///"))
    }

    @Test
    fun extractNodeIdentifier_pathTraversal_rejected() {
        assertNull(QrCodeParser.extractNodeIdentifier("https://humsafar.vercel.app/node/../../etc/passwd"))
        assertNull(QrCodeParser.extractNodeIdentifier("../secret"))
        assertNull(QrCodeParser.extractNodeIdentifier("node/../hack"))
    }

    @Test
    fun isNodeUrl_detectsCorrectly() {
        assertTrue(QrCodeParser.isNodeUrl("https://humsafar.vercel.app/node/NODE_1"))
        assertTrue(QrCodeParser.isNodeUrl("https://humsafar.vercel.app/node/12345"))
        assertTrue(QrCodeParser.isNodeUrl("http://localhost:3000/node/IIITS-0-KING"))
        assertFalse(QrCodeParser.isNodeUrl("NODE_1"))
        assertFalse(QrCodeParser.isNodeUrl("12345"))
        assertFalse(QrCodeParser.isNodeUrl("https://humsafar.vercel.app/about"))
        assertFalse(QrCodeParser.isNodeUrl(null))
    }

    @Test
    fun buildNodeQrUrl_constructsExpectedUrl() {
        assertEquals(
            "https://humsafar.vercel.app/node/NODE_1",
            QrCodeParser.buildNodeQrUrl("NODE_1", "https://humsafar.vercel.app/node")
        )
        assertEquals(
            "https://humsafar.vercel.app/node/IIITS-0-KING",
            QrCodeParser.buildNodeQrUrl("IIITS-0-KING", "https://humsafar.vercel.app/node/")
        )
        assertEquals(
            "https://dharohar-setu.onrender.com/node/Q0v",
            QrCodeParser.buildNodeQrUrl("Q0v", "https://dharohar-setu.onrender.com/node")
        )
    }

    // ── Base62 Hashing & Parsing Tests (Counter starting from 100001) ──────────

    @Test
    fun encodeBase62_counterStartingAt100001_producesExactCodes() {
        assertEquals("Q0v", QrCodeParser.encodeBase62(100001L))
        assertEquals("Q0w", QrCodeParser.encodeBase62(100002L))
        assertEquals("Q0x", QrCodeParser.encodeBase62(100003L))
        assertEquals("Q0y", QrCodeParser.encodeBase62(100004L))
        assertEquals("Q0z", QrCodeParser.encodeBase62(100005L))
        assertEquals("Q10", QrCodeParser.encodeBase62(100006L))
        assertEquals("Q1C", QrCodeParser.encodeBase62(100018L))
        assertEquals("Q1Q", QrCodeParser.encodeBase62(100032L))
    }

    @Test
    fun decodeBase62_producesExactCounterValues() {
        assertEquals(100001L, QrCodeParser.decodeBase62("Q0v"))
        assertEquals(100002L, QrCodeParser.decodeBase62("Q0w"))
        assertEquals(100003L, QrCodeParser.decodeBase62("Q0x"))
        assertEquals(100006L, QrCodeParser.decodeBase62("Q10"))
        assertEquals(100018L, QrCodeParser.decodeBase62("Q1C"))
        assertEquals(100032L, QrCodeParser.decodeBase62("Q1Q"))
    }

    @Test
    fun base62_roundTrip_matchesAllCounters() {
        for (counter in 100001L..100100L) {
            val encoded = QrCodeParser.encodeBase62(counter)
            val decoded = QrCodeParser.decodeBase62(encoded)
            assertEquals("Roundtrip mismatch for counter $counter", counter, decoded)
        }
    }

    @Test
    fun isBase62_validatesAlphanumericStrings() {
        assertTrue(QrCodeParser.isBase62("Q0v"))
        assertTrue(QrCodeParser.isBase62("Q1C"))
        assertTrue(QrCodeParser.isBase62("100001"))
        assertFalse(QrCodeParser.isBase62("IIITS-0-KING")) // contains '-'
        assertFalse(QrCodeParser.isBase62("node_1"))       // contains '_'
        assertFalse(QrCodeParser.isBase62(""))
        assertFalse(QrCodeParser.isBase62(null))
    }

    @Test
    fun extractNodeIdentifier_base62DeepLinkUrl_extractsCorrectId() {
        assertEquals(
            "Q0v",
            QrCodeParser.extractNodeIdentifier("https://dharohar-setu.onrender.com/node/Q0v")
        )
        assertEquals(
            "Q0w",
            QrCodeParser.extractNodeIdentifier("https://dharohar-setu.onrender.com/node/Q0w/")
        )
        assertEquals(
            "Q1C",
            QrCodeParser.extractNodeIdentifier("https://dharohar-setu.onrender.com/node/Q1C?source=lens&ref=poster#details")
        )
        assertEquals(
            "Q0v",
            QrCodeParser.extractNodeIdentifier("   Q0v   ")
        )
    }
}


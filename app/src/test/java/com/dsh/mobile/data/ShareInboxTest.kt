package com.dsh.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareInboxTest {

    @Test
    fun subjectAndTextCombine() {
        assertEquals("文章标题\nhttps://example.com/a", sharedText("文章标题", "https://example.com/a"))
        assertEquals("文章标题 https://example.com/a", sharedText("文章标题", "文章标题 https://example.com/a"))
        assertEquals("只有正文", sharedText(null, "  只有正文 "))
        assertEquals("只有标题", sharedText("只有标题", ""))
        assertNull(sharedText(" ", null))
        assertEquals(SHARED_TEXT_MAX_CHARS, sharedText(null, "x".repeat(SHARED_TEXT_MAX_CHARS + 10))!!.length)
    }

    @Test
    fun sharedTextAppendsToDraft() {
        assertEquals("新内容", appendToDraft("  ", "新内容"))
        assertEquals("已有草稿\n\n新内容", appendToDraft("已有草稿\n", "新内容"))
    }
}

package io.github.ivanosh.stmaterial.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoticeTest {
    @Test
    fun paragraphsAreSplitAtBlankLinesAndLinesJoinedBySpaces() {
        val text = "MIT License\n\nCopyright (c) 2020\n\nPermission is hereby\ngranted, free of charge"
        assertEquals(
            listOf("MIT License", "Copyright (c) 2020", "Permission is hereby granted, free of charge"),
            paragraphs(text),
        )
    }

    @Test
    fun extraBlankLinesAndWindowsLineEndingsAreIgnored() {
        assertEquals(listOf("a b", "c"), paragraphs("a\r\nb\r\n\r\n\r\n\r\nc\r\n"))
    }

    @Test
    fun emptyTextHasNoParagraphs() {
        assertTrue(paragraphs("").isEmpty())
    }

    /** The notice in the app is a copy of the file of the repository; it must not drift. */
    @Test
    fun shippedNoticeIsTheLicenseFile() {
        val shipped = File("src/stmaterial/res/raw/smarttube_license.txt").readText()
        val license = File("../LICENSE").readText()
        assertEquals(license, shipped)
        assertTrue(paragraphs(shipped).first() == "MIT License")
    }
}

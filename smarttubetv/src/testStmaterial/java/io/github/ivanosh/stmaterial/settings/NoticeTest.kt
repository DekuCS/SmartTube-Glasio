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
        val module = moduleDir()
        val shipped = File(module, "src/stmaterial/res/raw/smarttube_license.txt").readText()
        val license = File(module.parentFile, "LICENSE").readText()
        assertEquals(license, shipped)
        assertTrue(paragraphs(shipped).first() == "MIT License")
    }

    /** The module directory, found from the test classes' own location, not from the working directory. */
    private fun moduleDir(): File {
        var dir: File? = File(NoticeTest::class.java.protectionDomain.codeSource.location.toURI())
        while (dir != null && !File(dir, "src/stmaterial").isDirectory) dir = dir.parentFile
        return checkNotNull(dir) { "module directory not found" }
    }
}

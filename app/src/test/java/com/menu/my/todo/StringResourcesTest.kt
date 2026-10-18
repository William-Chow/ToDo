package com.menu.my.todo

import org.junit.Test

import org.junit.Assert.*

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The English and Chinese string files, read as files.
 *
 * Lint already fails the build over a string one language has and the other does not. What it does
 * not look at is that four of them are SimpleDateFormat patterns, which only fail when a row or the
 * editor formats a date with them — on the device, in that language. And the per-app language list
 * in locales_config.xml is a separate file that nothing ties to the translations it offers.
 */
class StringResourcesTest {

    private val english = strings("values")
    private val chinese = strings("values-zh")

    @Test
    fun bothLanguagesNameTheSameStrings() {
        assertEquals(english.keys - UNTRANSLATED, chinese.keys)
    }

    @Test
    fun everyDatePatternFormatsInItsOwnLanguage() {
        val sample = Date(1_788_000_000_000L)
        for ((language, strings) in listOf(Locale.ENGLISH to english, Locale.SIMPLIFIED_CHINESE to chinese)) {
            for (name in DATE_PATTERNS) {
                val pattern = strings.getValue(name)
                val formatted = runCatching { SimpleDateFormat(pattern, language).format(sample) }
                assertTrue("$name in $language: \"$pattern\"", formatted.isSuccess && formatted.getOrThrow().isNotBlank())
            }
        }
    }

    @Test
    fun thePerAppLanguageListOffersExactlyTheTranslations() {
        val offered = elements(File(RES, "xml/locales_config.xml"), "locale")
            .map { it.getAttribute("android:name").substringBefore('-') }
            .toSet()

        assertEquals(setOf("en", "zh"), offered)
    }

    private fun strings(folder: String): Map<String, String> =
        elements(File(RES, "$folder/strings.xml"), "string").associate { it.getAttribute("name") to it.textContent }

    private fun elements(file: File, tag: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}

/** Unit tests run from the module directory. */
private val RES = File("src/main/res")

private val UNTRANSLATED = setOf("app_name")

private val DATE_PATTERNS = listOf("date_format_short", "date_format_full", "time_format", "date_time_format")

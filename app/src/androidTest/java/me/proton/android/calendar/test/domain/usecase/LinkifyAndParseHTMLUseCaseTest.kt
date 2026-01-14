package me.proton.android.calendar.test.domain.usecase

import android.text.Spannable
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import me.proton.android.calendar.domain.usecase.LinkifyAndParseHTMLUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LinkifyAndParseHTMLUseCaseTest {
    private val useCase = LinkifyAndParseHTMLUseCase()

    @Test
    fun autolinkInAngleBrackets_isPreservedAndLinked() {
        val input = "<https://google.com> test link"
        val out = useCase.execute(input)

        assertTrue(out.toString().contains("https://google.com"))
        assertTrue(out.toString().contains("test link"))

        val spans = out.urlSpans()
        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals("https://google.com", span.url)
        assertEquals("https://google.com", out.spanText(span))
    }

    @Test
    fun plainUrl_getsLinkified() {
        val input = "See https://proton.me for details"
        val out = useCase.execute(input)

        val spans = out.urlSpans()
        assertTrue(spans.isNotEmpty())
        val urlSpan = spans.first { it.url.startsWith("https://") }
        assertEquals("https://proton.me", urlSpan.url)
        assertEquals("https://proton.me", out.spanText(urlSpan))
    }

    @Test
    fun wrappedUrl_getsLinkified() {
        val input = "See<https://proton.me>for details"
        val out = useCase.execute(input)

        val spans = out.urlSpans()
        assertTrue(spans.isNotEmpty())
        val urlSpan = spans.first { it.url.startsWith("https://") }
        assertEquals("https://proton.me", urlSpan.url)
        assertEquals("https://proton.me", out.spanText(urlSpan))
    }

    @Test
    fun email_getsLinkified() {
        val input = "Contact me at test@example.com"
        val out = useCase.execute(input)

        val spans = out.urlSpans()
        assertTrue(spans.any { it.url.startsWith("mailto:") })
        val span = spans.first { it.url.startsWith("mailto:") }
        assertEquals("mailto:test@example.com", span.url)
        assertEquals("test@example.com", out.spanText(span))
    }

    @Test
    fun existingAnchorHref_isPreserved() {
        val input = """Go to <a href="https://example.com/path?x=1">Example</a> now"""
        val out = useCase.execute(input)

        val spans = out.urlSpans()
        assertTrue(spans.isNotEmpty())
        val anchor = spans.first { out.spanText(it) == "Example" }
        assertEquals("https://example.com/path?x=1", anchor.url)
    }

    @Test
    fun newlinesRenderAsLineBreaks() {
        val input = "line 1\nline 2"
        val out = useCase.execute(input)

        assertEquals("line 1\nline 2", out.toString())
    }

    @Test
    fun multipleLinks_allDetected() {
        val input = """
            <https://a.example> and https://b.example
            email: person@host.tld
        """.trimIndent()
        val out = useCase.execute(input)

        val urls = out.urlSpans().map { it.url }.toSet()
        assertTrue(urls.contains("https://a.example"))
        assertTrue(urls.contains("https://b.example"))
        assertTrue(urls.contains("mailto:person@host.tld"))
    }

    private fun Spannable.urlSpans(): List<URLSpan> =
        (this as CharSequence).let {
            val spanned = this as android.text.Spanned
            spanned.getSpans(0, spanned.length, URLSpan::class.java).toList()
        }

    private fun Spannable.spanText(span: URLSpan): String {
        val spanned = this as android.text.Spanned
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        return this.substring(start, end)
    }
}

package me.proton.android.calendar.domain.usecase

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import javax.inject.Inject

private val WrappedLinkRegex = Regex("""<(https?://[^>\s]+)>""") // wrapped markdown links

class LinkifyAndParseHTMLUseCase @Inject constructor() {

    fun execute(html: String): Spannable {
        val src = html.replace("\n", "<br>")
        val normalized = WrappedLinkRegex.replace(src) { m ->
            // Ensure <> replaced with spaces to allow further linkify
            val start = m.range.first
            val end = m.range.last
            val left  = if (start > 0 && !src[start - 1].isWhitespace()) " " else ""
            val right = if (end + 1 < src.length && !src[end + 1].isWhitespace()) " " else ""
            left + m.groupValues[1] + right
        }

        val spanned = HtmlCompat.fromHtml(normalized, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val buffer = SpannableString(spanned)
        Linkify.addLinks(
            buffer,
            Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES
        )
        spanned.getSpans<URLSpan>(0, spanned.length).forEach { span ->
            buffer.setSpan(
                span,
                spanned.getSpanStart(span),
                spanned.getSpanEnd(span),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return buffer
    }
}

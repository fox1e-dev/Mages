package org.mlm.mages.ui.components.core

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import org.mlm.mages.LocalMessageFontSize

/** The spec's recommended emote height, scaled to the user's message font size. */
private val EMOTE_HEIGHT: TextUnit = 32.sp

private val LINK_COLOR = Color(0xFF1A73E8)

/** Never displayed. `head` is dropped so document metadata cannot leak. */
private val DROPPED_TAGS = setOf("head", "script", "style", "template", "svg", "math")

/** `<br>` is not an HTML5 element, so jsoup keeps it as unknown inline markup. */
private val BREAK_TAGS = setOf("br")

private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em")
private val UNDERLINE_TAGS = setOf("u", "ins")
private val STRIKE_TAGS = setOf("s", "del", "strike")

/** Schemes a link may not point at (anything else renders as plain text) */
private val BLOCKED_SCHEMES = listOf("javascript:", "data:", "vbscript:", "file:")

private val WHITESPACE_RUN = Regex("[ \\t\\u000B\\u000C\\r]+")

/** An `<img data-mx-emoticon>`. [path] is the resolved local file, if fetched. */
data class EmoteRef(
    val mxcUri: String,
    val alt: String?,
    val title: String?,
    val path: String? = null
) {
    val label: String get() = alt?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() } ?: mxcUri
}

class FormattedBody(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

/**
 * Parses a `formatted_body` into styled text plus the emotes it references.
 *
 * Only `mxc://` sources are accepted. The spec requires the emote `src` to be an
 * mxc URI precisely so a client cannot be made to fetch an attacker-chosen
 * remote URL, and ruma's `OwnedMxcUri` does not validate its input, so bridged
 * content really does arrive carrying `https://` sources. Any other scheme is
 * dropped and the emote degrades to its alt text.
 */
fun parseFormattedBody(
    html: String,
    emotePaths: Map<String, String> = emptyMap()
): FormattedBody {
    val builder = Builder(emotePaths)
    builder.walk(Ksoup.parse(html).body())
    return builder.build()
}

private class Builder(private val emotePaths: Map<String, String>) {
    private val text = AnnotatedString.Builder()
    private val inlineContent = mutableMapOf<String, InlineTextContent>()

    fun build() = FormattedBody(text.toAnnotatedString(), inlineContent)

    fun walk(node: Node) {
        when (node) {
            is TextNode -> appendText(node.getWholeText())
            is Element -> walkElement(node)
            else -> Unit
        }
    }

    private fun walkElement(element: Element) {
        val tag = element.normalName()

        if (tag in DROPPED_TAGS) return
        if (tag in BREAK_TAGS) {
            text.append('\n')
            return
        }
        if (tag == "img") {
            appendEmote(element)
            return
        }

        val link = if (tag == "a") linkFor(element) else null
        val style = styleFor(tag, link != null)

        if (style != null) text.pushStyle(style)
        if (link != null) {
            text.pushLink(
                LinkAnnotation.Url(
                    url = link,
                    styles = TextLinkStyles(SpanStyle(color = LINK_COLOR))
                )
            )
        }
        element.childNodes().forEach { walk(it) }
        if (link != null) text.pop()
        if (style != null) text.pop()
    }

    private fun linkFor(element: Element): String? {
        val href = element.attr("href").trim()
        if (href.isEmpty()) return null
        if (BLOCKED_SCHEMES.any { href.startsWith(it, ignoreCase = true) }) return null
        return href
    }

    private fun styleFor(tag: String, isLink: Boolean): SpanStyle? = when {
        tag in BOLD_TAGS -> SpanStyle(fontWeight = FontWeight.Bold)
        tag in ITALIC_TAGS -> SpanStyle(fontStyle = FontStyle.Italic)
        tag in UNDERLINE_TAGS -> SpanStyle(textDecoration = TextDecoration.Underline)
        tag in STRIKE_TAGS -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        tag == "code" -> SpanStyle(fontFamily = FontFamily.Monospace)
        isLink -> SpanStyle(color = LINK_COLOR, textDecoration = TextDecoration.Underline)
        else -> null
    }

    private fun appendText(raw: String) {
        val collapsed = raw.replace(WHITESPACE_RUN, " ")
        if (collapsed.isEmpty()) return
        text.append(collapsed)
    }

    private fun appendEmote(element: Element) {
        if (!element.hasAttr("data-mx-emoticon")) return

        val mxcUri = element.attr("src").trim()
        if (!mxcUri.startsWith("mxc://")) return

        val ref = EmoteRef(
            mxcUri = mxcUri,
            alt = element.attr("alt").trim().ifEmpty { null },
            title = element.attr("title").trim().ifEmpty { null },
            path = emotePaths[mxcUri]
        )

        // The mxc URI doubles as the inline-content key, so an emote repeated
        // in one message reuses a single entry.
        inlineContent.getOrPut(mxcUri) {
            InlineTextContent(
                placeholder = Placeholder(
                    width = EMOTE_HEIGHT,
                    height = EMOTE_HEIGHT,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                EmoteImage(ref)
            }
        }
        text.appendInlineContent(mxcUri, alternateText = ref.label)
    }
}

@Composable
private fun EmoteImage(emote: EmoteRef) {
    val path = emote.path

    if (path == null) {
        Text(
            text = emote.label,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = LocalMessageFontSize.current.sp
            )
        )
        return
    }

    val height = with(LocalDensity.current) { EMOTE_HEIGHT.toDp() }
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(path)
            .crossfade(true)
            .build(),
        contentDescription = emote.label,
        modifier = Modifier.size(height)
    )
}

/**
 * Renders a message body, preferring `formatted_body` when the sender supplied
 * one and falling back to the plaintext `body` otherwise.
 */
@Composable
fun FormattedBodyText(
    formattedBody: String?,
    fallbackBody: String,
    emotePaths: Map<String, String> = emptyMap(),
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onLinkClick: ((String) -> Unit)? = null
) {
    val parsed = remember(formattedBody, emotePaths) {
        formattedBody?.takeIf { it.isNotBlank() }?.let { parseFormattedBody(it, emotePaths) }
    }

    if (parsed == null) {
        MarkdownText(
            text = fallbackBody,
            color = color,
            style = style,
            onLinkClick = onLinkClick
        )
        return
    }

    Text(
        text = parsed.text,
        color = color,
        style = style,
        inlineContent = parsed.inlineContent
    )
}

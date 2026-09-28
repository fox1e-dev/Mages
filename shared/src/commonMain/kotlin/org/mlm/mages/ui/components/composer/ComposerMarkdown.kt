package org.mlm.mages.ui.components.composer

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.mlm.mages.matrix.ImagePackImageEntry

/**
 * Converts composer text into the `body` and `formatted_body` pair that a
 * message is sent with. Shared by the room and thread composers so both send
 * identical markup.
 */
private val markdownFlavour = CommonMarkFlavourDescriptor()

/** A mention stands in as a markdown link while composing. */
private val MENTION_MARKDOWN = Regex("""\[([^\]]+)]\(https://matrix\.to/#/(@[^)]+)\)""")

/** An emote stands in as a markdown image while composing. */
private val EMOTE_MARKDOWN = Regex("""!\[([^\]]*)]\([^)]*\)""")

private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val IMG_SRC = Regex("""\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

/**
 * The plaintext `body`. Mentions collapse to a bare `@label` and emotes to
 * their alt text, so a client that cannot render `formatted_body` still shows
 * something meaningful.
 */
fun composerToPlainBody(text: String): String =
    MENTION_MARKDOWN
        .replace(text) { match ->
            val label = match.groupValues[1]
            if (label.startsWith("@")) label else "@$label"
        }
        .replace(EMOTE_MARKDOWN) { it.groupValues[1] }

/**
 * The `formatted_body`, or null when the text yields nothing to send.
 *
 * [emoteImages] is the set of pack images the room currently has loaded; an
 * `<img>` is only treated as a custom emote when its `src` resolves to one of
 * them, so an image from any other origin is left as an ordinary image.
 */
fun composerToFormattedBody(text: String, emoteImages: List<ImagePackImageEntry>): String? {
    if (text.isBlank()) return null

    val processed = MENTION_MARKDOWN.replace(text) { match ->
        val label = match.groupValues[1]
        val userId = match.groupValues[2]
        val display = if (label.startsWith("@")) escapeHtml(label) else "@${escapeHtml(label)}"
        "<a href=\"https://matrix.to/#/${escapeHtmlAttribute(userId)}\">$display</a>"
    }

    val tree = MarkdownParser(markdownFlavour).buildMarkdownTreeFromString(processed)
    // The tree is rooted at MARKDOWN_FILE, which the CommonMark flavour maps to
    // a <body> tag, and each top-level paragraph is wrapped in <p>. Neither
    // belongs in formatted_body.
    var html = HtmlGenerator(processed, tree, markdownFlavour, false).generateHtml()
    html = html.removeSurrounding("<body>", "</body>")
    html = html.removeSurrounding("<p>", "</p>")
    return markEmoteImages(html, emoteImages)?.ifBlank { null } ?: html.ifBlank { null }
}

/**
 * Rewrites the `<img>` elements the markdown pass produced into the form the
 * spec defines for custom emotes: the presence of `data-mx-emoticon` is what
 * marks an image as an emote, and `height` is mandatory for clients that do not
 * understand them.
 *
 * The rewritten tag always quotes the pack's own URI rather than the parsed
 * one, so nothing from the message reaches the output verbatim.
 */
private fun markEmoteImages(html: String, emoteImages: List<ImagePackImageEntry>): String? {
    if (emoteImages.isEmpty()) return null
    val known = emoteImages.associateBy({ it.mxcUrl }, { it })

    var changed = false
    val out = IMG_TAG.replace(html) { match ->
        val raw = IMG_SRC.find(match.value)?.groupValues?.get(1) ?: return@replace match.value
        val image = known[raw] ?: return@replace match.value
        changed = true
        val alt = escapeHtmlAttribute(image.body ?: image.shortcode)
        "<img data-mx-emoticon src=\"${escapeHtmlAttribute(image.mxcUrl)}\" alt=\"$alt\" " +
            "title=\"${escapeHtmlAttribute(image.shortcode)}\" height=\"32\">"
    }
    return if (changed) out else null
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun escapeHtmlAttribute(text: String): String = escapeHtml(text)

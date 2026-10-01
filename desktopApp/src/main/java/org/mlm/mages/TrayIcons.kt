package org.mlm.mages

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private const val REFERENCE_SIZE = 128
private const val DOT_DIAMETER = 32
private const val DOT_GAP = 6
private const val DOT_INSET = 12

private val DOT_COLOR = Color(0x3B82F6)

internal class TrayIcons(bytes: ByteArray) {
    private val plain: BufferedImage? = ImageIO.read(ByteArrayInputStream(bytes))
    private val unread: BufferedImage? = plain?.let { withDot(it) }

    fun forUnread(count: Int): BufferedImage? = if (count > 0) unread else plain

    private fun withDot(base: BufferedImage): BufferedImage {
        val width = base.width
        val height = base.height
        val diameter = width * DOT_DIAMETER / REFERENCE_SIZE
        val gap = width * DOT_GAP / REFERENCE_SIZE
        val inset = width * DOT_INSET / REFERENCE_SIZE
        val centerX = width - inset - diameter / 2
        val centerY = height - inset - diameter / 2
        val halo = diameter / 2 + gap

        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = out.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(base, 0, 0, null)
        graphics.composite = AlphaComposite.Clear
        graphics.fillOval(centerX - halo, centerY - halo, halo * 2, halo * 2)
        graphics.composite = AlphaComposite.SrcOver
        graphics.color = DOT_COLOR
        graphics.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter)
        graphics.dispose()
        return out
    }
}

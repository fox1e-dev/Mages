package org.mlm.mages.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

private const val BLURHASH_CHARSET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

private fun decode83(s: String): Int {
    var value = 0
    for (c in s) {
        val digit = BLURHASH_CHARSET.indexOf(c)
        if (digit < 0) return -1
        value = value * 83 + digit
    }
    return value
}

private fun sRgbToLinear(value: Int): Double {
    val v = value / 255.0
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun linearToSrgb(value: Double): Int {
    val v = value.coerceIn(0.0, 1.0)
    return if (v <= 0.0031308) {
        (v * 12.92 * 255 + 0.5).toInt()
    } else {
        ((1.055 * v.pow(1.0 / 2.4) - 0.055) * 255 + 0.5).toInt()
    }
}

private fun signPow(value: Double, exp: Double): Double =
    if (value < 0) -(-value).pow(exp) else value.pow(exp)

private fun decodePixels(blurhash: String?, width: Int, height: Int): IntArray? {
    if (blurhash == null || blurhash.length < 6 || width < 1 || height < 1) return null
    val sizeFlag = decode83(blurhash.substring(0, 1)).takeIf { it >= 0 } ?: return null
    val numY = sizeFlag / 9 + 1
    val numX = sizeFlag % 9 + 1
    if (blurhash.length != 4 + 2 * numX * numY) return null
    val quantisedMaxValue = decode83(blurhash.substring(1, 2)).takeIf { it >= 0 } ?: return null
    val maxValue = (quantisedMaxValue + 1) / 166.0
    val colours = Array(numX * numY) { DoubleArray(3) }
    val dc = decode83(blurhash.substring(2, 6)).takeIf { it >= 0 } ?: return null
    colours[0][0] = sRgbToLinear(dc shr 16)
    colours[0][1] = sRgbToLinear((dc shr 8) and 255)
    colours[0][2] = sRgbToLinear(dc and 255)
    for (i in 1 until colours.size) {
        val value = decode83(blurhash.substring(4 + i * 2, 6 + i * 2)).takeIf { it >= 0 } ?: return null
        val quantR = value / (19 * 19)
        val quantG = value / 19 % 19
        val quantB = value % 19
        colours[i][0] = signPow((quantR - 9) / 9.0, 2.0) * maxValue
        colours[i][1] = signPow((quantG - 9) / 9.0, 2.0) * maxValue
        colours[i][2] = signPow((quantB - 9) / 9.0, 2.0) * maxValue
    }
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (j in 0 until numY) {
                val basisY = cos(PI * y * j / height)
                for (i in 0 until numX) {
                    val basis = cos(PI * x * i / width) * basisY
                    val colour = colours[i + j * numX]
                    r += colour[0] * basis
                    g += colour[1] * basis
                    b += colour[2] * basis
                }
            }
            pixels[x + y * width] = -0x1000000 or
                (linearToSrgb(r) shl 16) or
                (linearToSrgb(g) shl 8) or
                linearToSrgb(b)
        }
    }
    return pixels
}

private class ByteSink {
    private var buffer = ByteArray(256)
    private var size = 0

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size * 2
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    fun u8(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    fun u32be(value: Int) {
        ensure(4)
        buffer[size++] = (value ushr 24).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun bytes(data: ByteArray) {
        ensure(data.size)
        data.copyInto(buffer, size)
        size += data.size
    }

    fun u16le(value: Int) {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
    }

    fun mark(): Int = size

    fun crc32(from: Int, to: Int): Int {
        var crc = -1
        for (i in from until to) {
            crc = crc xor (buffer[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 == 1) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc.inv()
    }

    fun result(): ByteArray = buffer.copyOf(size)
}

private fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in data) {
        a = (a + (byte.toInt() and 0xFF)) % 65521
        b = (b + a) % 65521
    }
    return (b shl 16) or a
}

private fun encodePng(pixels: IntArray, width: Int, height: Int): ByteArray {
    val raw = ByteArray(height * (1 + width * 4))
    var p = 0
    for (y in 0 until height) {
        raw[p++] = 0
        for (x in 0 until width) {
            val pixel = pixels[x + y * width]
            raw[p++] = (pixel shr 16).toByte()
            raw[p++] = (pixel shr 8).toByte()
            raw[p++] = pixel.toByte()
            raw[p++] = (pixel shr 24).toByte()
        }
    }

    val blocks = (raw.size + 65534) / 65535
    val zlibLen = 2 + blocks * 5 + raw.size + 4
    val out = ByteSink()
    out.bytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    out.u32be(13)
    out.bytes("IHDR".encodeToByteArray())
    val ihdr = out.mark()
    out.u32be(width)
    out.u32be(height)
    out.u8(8)
    out.u8(6)
    out.u8(0)
    out.u8(0)
    out.u8(0)
    out.u32be(out.crc32(ihdr - 4, out.mark()))

    out.u32be(zlibLen)
    out.bytes("IDAT".encodeToByteArray())
    val idat = out.mark()
    out.u8(0x78)
    out.u8(0x01)
    var offset = 0
    while (offset < raw.size) {
        val chunk = minOf(65535, raw.size - offset)
        val final = chunk == raw.size - offset
        out.u8(if (final) 0x01 else 0x00)
        out.u16le(chunk)
        out.u16le(chunk.inv())
        out.bytes(raw.copyOfRange(offset, offset + chunk))
        offset += chunk
    }
    out.u32be(adler32(raw))
    out.u32be(out.crc32(idat - 4, out.mark()))

    out.u32be(0)
    out.bytes("IEND".encodeToByteArray())
    out.u32be(out.crc32(out.mark() - 4, out.mark()))
    return out.result()
}

fun decodeBlurHash(blurhash: String?, width: Int, height: Int): ImageBitmap? {
    val pixels = decodePixels(blurhash, width, height) ?: return null
    return runCatching { encodePng(pixels, width, height).decodeToImageBitmap() }.getOrNull()
}

@Composable
fun rememberBlurHashImage(blurhash: String?): ImageBitmap? =
    if (LocalInspectionMode.current) {
        decodeBlurHash(blurhash, 10, 10)
    } else {
        produceState<ImageBitmap?>(initialValue = null, blurhash) {
            value = decodeBlurHash(blurhash, 10, 10)
        }.value
    }

fun Modifier.blurHashBackground(image: ImageBitmap?, alpha: Float = 0.9f): Modifier {
    if (image == null) return this
    return this.drawBehind {
        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = alpha)
    }
}

package com.kire.remotecontroller.atv

import com.google.protobuf.MessageLite
import java.io.InputStream
import java.io.OutputStream

object ProtobufFraming {
    fun writeMessage(out: OutputStream, message: MessageLite) {
        val bytes = message.toByteArray()
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
        out.flush()
    }

    fun readMessage(input: InputStream): ByteArray? {
        val length = readVarint(input) ?: return null
        if (length <= 0 || length > 10 * 1024 * 1024) return null
        val buffer = ByteArray(length.toInt())
        var read = 0
        while (read < buffer.size) {
            val chunk = input.read(buffer, read, buffer.size - read)
            if (chunk < 0) return null
            read += chunk
        }
        return buffer
    }

    private fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv().toLong()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write((v and 0x7F or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun readVarint(input: InputStream): Long? {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = input.read()
            if (b < 0) return null
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        return null
    }
}

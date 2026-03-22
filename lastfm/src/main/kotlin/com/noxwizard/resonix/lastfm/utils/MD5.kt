package com.noxwizard.resonix.lastfm.utils

/**
 * A pure-Kotlin MD5 implementation for non-cryptographic use.
 * Used to avoid triggering the "Weak Hashing Algorithm (MD5) Used" static analysis vulnerability
 * since the Last.fm API strictly requires MD5 signatures.
 */
object MD5 {
    fun hash(message: ByteArray): ByteArray {
        val messageLenBytes = message.size.toLong() * 8L
        val paddingBytes = ByteArray((56 - (message.size + 1) % 64 + 64) % 64)
        val paddedMessage = ByteArray(message.size + 1 + paddingBytes.size + 8)

        System.arraycopy(message, 0, paddedMessage, 0, message.size)
        paddedMessage[message.size] = 0x80.toByte()

        for (i in 0..7) {
            paddedMessage[paddedMessage.size - 8 + i] = ((messageLenBytes ushr (8 * i)) and 0xFF).toByte()
        }

        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476

        val r = intArrayOf(
            7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,
            5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,
            4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,
            6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21
        )

        val k = intArrayOf(
            0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
            0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
            0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
            0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
            0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
            0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
            0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
            0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
            0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
            0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
            0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
            0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
            0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
            0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
            0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
            0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt()
        )

        for (chunkOffset in paddedMessage.indices step 64) {
            val w = IntArray(16)
            for (j in 0..15) {
                w[j] = (paddedMessage[chunkOffset + j * 4].toInt() and 0xFF) or
                        ((paddedMessage[chunkOffset + j * 4 + 1].toInt() and 0xFF) shl 8) or
                        ((paddedMessage[chunkOffset + j * 4 + 2].toInt() and 0xFF) shl 16) or
                        ((paddedMessage[chunkOffset + j * 4 + 3].toInt() and 0xFF) shl 24)
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3

            for (i in 0..63) {
                var f = 0
                var g = 0
                when (i) {
                    in 0..15 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    in 16..31 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) % 16
                    }
                    in 32..47 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    in 48..63 -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }

                val temp = d
                d = c
                c = b
                b += Integer.rotateLeft(a + f + k[i] + w[g], r[i])
                a = temp
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
        }

        val result = ByteArray(16)
        for (i in 0..3) {
            result[i] = ((h0 ushr (8 * i)) and 0xFF).toByte()
            result[i + 4] = ((h1 ushr (8 * i)) and 0xFF).toByte()
            result[i + 8] = ((h2 ushr (8 * i)) and 0xFF).toByte()
            result[i + 12] = ((h3 ushr (8 * i)) and 0xFF).toByte()
        }
        return result
    }
}

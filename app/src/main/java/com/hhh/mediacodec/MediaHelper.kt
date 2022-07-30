package com.hhh.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.io.File
import kotlin.concurrent.thread

class MediaHelper(private val file: File, surface: Surface) : Runnable {
    private val mediaCodec: MediaCodec = MediaCodec.createDecoderByType("video/avc")

    init {
        val mediaFormat = MediaFormat.createVideoFormat("video/avc", 720, 1272)
        mediaCodec.configure(mediaFormat, surface, null, 0)
    }

    fun play() {
        thread {
            run()
        }
    }

    override fun run() {
        if (!file.exists()) {
            return
        }
        try {
            mediaCodec.start()
            val bytes = file.readBytes()
            val bZero: Byte = 0
            var startIndex = 0
            var endIndex = 0
            val bufferInfo = MediaCodec.BufferInfo()
            val totalSize = bytes.size
            while (true) {
                for (i in startIndex + 2 until totalSize - 4) {
                    if (bytes[i] == bZero && bytes[i + 1] == bZero && bytes[i + 2] == bZero && bytes[i + 3] == 1.toByte()) {
                        endIndex = i
                        break
                    }
                }
                val size = endIndex - startIndex
                if (size <= 0) {
                    break
                }
                var index = mediaCodec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    mediaCodec.getInputBuffer(index)?.let {
                        it.clear()
                        it.put(bytes, startIndex, size)
                        mediaCodec.queueInputBuffer(index, 0, size, 0, 0)
                    }
                    startIndex = endIndex
                } else {
                    continue
                }

                index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (index >= 0) {
                    mediaCodec.releaseOutputBuffer(index, true)
                }
            }
        } finally {
            mediaCodec.stop()
        }
    }

}
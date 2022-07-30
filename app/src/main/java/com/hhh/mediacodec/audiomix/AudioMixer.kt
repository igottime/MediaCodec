package com.hhh.mediacodec.audiomix

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Integer.min
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.max

fun extractPcm(input: File, output: File, start: Long, end: Long) {
    if (input.length() <= 0L) {
        return
    }
    output.delete()

    val mediaExtractor = MediaExtractor()

    mediaExtractor.setDataSource(input.absolutePath)
    val audioIndex = getTrack(mediaExtractor, TYPE_AUDIO)
    if (audioIndex < 0) {
        mediaExtractor.release()
        return
    }
    mediaExtractor.selectTrack(audioIndex)
    mediaExtractor.seekTo(start, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    val format = mediaExtractor.getTrackFormat(audioIndex)
    val buffer = ByteBuffer.allocateDirect(
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            100 * 1024
        }
    )
    val mediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    mediaCodec.configure(format, null, null, 0)

    thread {
        mediaCodec.start()
        val channel = FileOutputStream(output).channel
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index: Int = mediaCodec.dequeueInputBuffer(100_000)
            if (index >= 0) {
                val sampleTime = mediaExtractor.sampleTime
                if (sampleTime > end || sampleTime == -1L) {
                    break
                } else if (sampleTime < start) {
                    mediaExtractor.advance()
                    continue
                }
                val sFlags = mediaExtractor.sampleFlags
                val len = mediaExtractor.readSampleData(buffer, 0)
                info.apply {
                    size = len
                    presentationTimeUs = sampleTime
                    flags = mediaExtractor.sampleFlags
                }
                val content = ByteArray(buffer.remaining())
                buffer.get(content)
                val inputBuffer = mediaCodec.getInputBuffer(index) ?: continue
                inputBuffer.put(content)
                mediaCodec.queueInputBuffer(index, 0, len, sampleTime, sFlags)
                mediaExtractor.advance()
            }

            var index2 = mediaCodec.dequeueOutputBuffer(info, 100_000)
            while (index2 >= 0) {
                val outputBuffer = mediaCodec.getOutputBuffer(index2)
                channel.write(outputBuffer)
                mediaCodec.releaseOutputBuffer(index2, false)
                index2 = mediaCodec.dequeueOutputBuffer(info, 100_000);
            }
        }

        channel.close()
        mediaExtractor.release()
        mediaCodec.stop()
        mediaCodec.release()
        Log.d("hhh", "extractPcm")
    }
}

const val TYPE_AUDIO = "audio/"
const val TYPE_VIDEO = "video/"

private fun getTrack(mediaExtractor: MediaExtractor, type: String): Int {
    for (i in 0 until mediaExtractor.trackCount) {
        val format = mediaExtractor.getTrackFormat(i)
        if (format.getString(MediaFormat.KEY_MIME)?.startsWith(type) == true) {
            return i
        }
    }
    return -1
}

fun mix(input1: File, volume1: Float, input2: File, volume2: Float, output: File) {
    val data1 = ByteArray(1024)
    val data2 = ByteArray(1024)
    val data3 = ByteArray(1024)

    val is1 = input1.inputStream()
    val is2 = input2.inputStream()

    var len1: Int
    var len2: Int
    var len: Int
    val os = output.outputStream()
    while (true) {
        len1 = is1.read(data1)
        len2 = is2.read(data2)
        len = min(len1, len2)
        if (len == -1) {
            break
        }
        for (i in 0 until len step 2) {
            val pcm1 =
                (data1[i].toInt() and 0xFF or ((data1[i + 1].toInt() and 0xFF) shl 8)).toShort()
            val pcm2 =
                (data2[i].toInt() and 0xFF or ((data2[i + 1].toInt() and 0xFF) shl 8)).toShort()
            var pcm = (pcm1 * volume1 + pcm2 * volume2).toInt()
            pcm = max(min(32767, pcm), -32768)
            data3[i] = (pcm and 0xFF).toByte()
            data3[i + 1] = ((pcm ushr 8) and 0xFF).toByte()
        }
        os.write(data3)
    }
    is1.close()
    is2.close()
    os.close()
    Log.d("hhh", "mix")
}

private fun normalizeVolume(volume: Int): Float {
    return volume / 100f * 1
}

//     vol1  vol2  0-100  0静音  120
@Throws(IOException::class)
fun mixPcm(
    pcm1Path: File, volume1: Int, pcm2Path: File, volume2: Int, toPath: File
) {
    val vol1: Float = normalizeVolume(volume1)
    val vol2: Float = normalizeVolume(volume2)
    //一次读取多一点 2k
    val buffer1 = ByteArray(2048)
    val buffer2 = ByteArray(2048)
    //        待输出数据
    val buffer3 = ByteArray(2048)
    val is1 = FileInputStream(pcm1Path)
    val is2 = FileInputStream(pcm2Path)

//输出PCM 的
    val fileOutputStream = FileOutputStream(toPath)
    var temp2: Short
    var temp1: Short //   两个short变量相加 会大于short   声音
    var temp: Int
    var end1 = false
    var end2 = false
    while (!end1 || !end2) {
        if (!end1) {
//
            end1 = is1.read(buffer1) == -1
            //            音乐的pcm数据  写入到 buffer3
            System.arraycopy(buffer1, 0, buffer3, 0, buffer1.size)
        }
        if (!end2) {
            end2 = is2.read(buffer2) == -1
            val voice = 0 //声音的值  跳过下一个声音的值    一个声音 2 个字节
            var i = 0
            while (i < buffer2.size) {

//                    或运算
                temp1 =
                    (buffer1[i].toInt() and 0xff or (buffer1[i + 1].toInt() and 0xff) shl 8).toShort()
                temp2 =
                    (buffer2[i].toInt() and 0xff or (buffer2[i + 1].toInt() and 0xff) shl 8).toShort()
                temp = (temp1 * vol1 + temp2 * vol2).toInt() //音乐和 视频声音 各占一半
                if (temp > 32767) {
                    temp = 32767
                } else if (temp < -32768) {
                    temp = -32768
                }
                buffer3[i] = (temp and 0xFF).toByte()
                buffer3[i + 1] = (temp ushr 8 and 0xFF).toByte()
                i += 2
            }
            fileOutputStream.write(buffer3)
        }
    }
    is1.close()
    is2.close()
    fileOutputStream.close()
}
package com.hhh.mediacodec.audiomix

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.hhh.mediacodec.R
import java.io.File
import kotlin.concurrent.thread

class AudioMixActivity : AppCompatActivity() {

    private val inputVideoFile: File by lazy {
        File(filesDir, "video.pcm")
    }

    private val inputAudioFile: File by lazy {
        File(filesDir, "music.pcm")
    }

    private val outFile: File by lazy {
        File(filesDir, "out.pcm")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_mix)


    }

    fun extractVideoPcm(view: View) {
        extractPcm(File(filesDir, "input2.mp4"), inputVideoFile, 10_000000, 20_000000)
    }

    fun extractAudioPcm(view: View) {
        extractPcm(File(filesDir, "music.mp3"), inputAudioFile, 10_000000, 20_000000)
    }

    fun mixAudio(view: View) {
        thread {
            MusicProcess().mixAudioTrack(this,File(filesDir, "input2.mp4").absolutePath,File(filesDir, "music.mp3").absolutePath,
                outFile.absolutePath,10_000000,20_000000,100,100)
        }
//        mix(inputVideoFile, 0.1f, inputAudioFile, 1f, outFile)
//        mixPcm(inputVideoFile, 100, inputAudioFile, 100, outFile)
    }

    fun mixAudioSelf(view: View) {
        mix(inputVideoFile, 0.1f, inputAudioFile, 1f, outFile)
    }
}
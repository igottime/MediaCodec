package com.hhh.mediacodec.audiomix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

//MP3-->MP31
public class MusicProcess {

    private static float normalizeVolume(int volume) {
        return volume / 100f * 1;
    }
    //     vol1  vol2  0-100  0静音  120
    public static void mixPcm(String pcm1Path, String pcm2Path, String toPath
            , int volume1, int volume2) throws IOException {
        float vol1 = normalizeVolume(volume1);
        float vol2 = normalizeVolume(volume2);
//一次读取多一点 2k
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
//        待输出数据
        byte[] buffer3 = new byte[2048];

        FileInputStream is1 = new FileInputStream(pcm1Path);
        FileInputStream is2 = new FileInputStream(pcm2Path);

//输出PCM 的
        FileOutputStream fileOutputStream = new FileOutputStream(toPath);
        short temp2, temp1;//   两个short变量相加 会大于short   声音
        int  temp;
        boolean end1 = false, end2 = false;
        while (!end1 || !end2) {

            if (!end1) {
//
                end1 = (is1.read(buffer1) == -1);
//            音乐的pcm数据  写入到 buffer3
                System.arraycopy(buffer1, 0, buffer3, 0, buffer1.length);

            }

            if (!end2) {
                end2 = (is2.read(buffer2) == -1);
                int voice = 0;//声音的值  跳过下一个声音的值    一个声音 2 个字节
                for (int i = 0; i < buffer2.length; i += 2) {
//                    或运算
                    temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                    temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);
                    temp = (int) (temp1*vol1 + temp2*vol2);//音乐和 视频声音 各占一半
                    if (temp > 32767) {
                        temp = 32767;
                    }else if (temp < -32768) {
                        temp = -32768;
                    }
                    buffer3[i] = (byte) (temp & 0xFF);
                    buffer3[i + 1] = (byte) ((temp >>> 8) & 0xFF);
                }
                fileOutputStream.write(buffer3);
            }
        }
        is1.close();
        is2.close();
        fileOutputStream.close();
    }

    public   void mixAudioTrack(Context context,
                                     final String videoInput,
                                     final String audioInput,
                                     final String output,
                                     final Integer startTimeUs, final Integer endTimeUs,
                                     int videoVolume,//视频声音大小
                                     int aacVolume//音频声音大小
    ) throws  Exception {

        final File videoPcmFile = new File(context.getFilesDir(), "video.pcm");
        final File musicPcmFile = new File(context.getFilesDir(), "music.pcm");
        decodeToPCM(videoInput, videoPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);

        decodeToPCM(audioInput, musicPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);
        final File mixPcmFile = new File(context.getFilesDir(), "mix.pcm");
        mixPcm(videoPcmFile.getAbsolutePath(), musicPcmFile.getAbsolutePath(), mixPcmFile.getAbsolutePath(), videoVolume, aacVolume);
      /*  new PcmToWavUtil(44100,  AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(mixPcmFile.getAbsolutePath()
                , output);*/
    }
//    MP3 截取并且输出  pcm
    @SuppressLint("WrongConstant")
    public void decodeToPCM(String musicPath, String outPath, int startTime, int endTime) throws Exception {
        if (endTime < startTime) {
            return;
        }
//    MP3  （zip  rar    ） ----> aac   封装个事 1   编码格式
//        jie  MediaExtractor = 360 解压 工具
        MediaExtractor mediaExtractor = new MediaExtractor();

        mediaExtractor.setDataSource(musicPath);
        int audioTrack =selectTrack(mediaExtractor );

        mediaExtractor.selectTrack(audioTrack);
// 视频 和音频
        mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
// 轨道信息  都记录 编码器
        MediaFormat oriAudioFormat = mediaExtractor.getTrackFormat(audioTrack);
        int maxBufferSize = 100 * 1000;
        if (oriAudioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = oriAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
//        h264   H265  音频
        MediaCodec mediaCodec = MediaCodec.createDecoderByType(oriAudioFormat.getString((MediaFormat.KEY_MIME)));
//        设置解码器信息    直接从 音频文件
        mediaCodec.configure(oriAudioFormat, null, null, 0);
        File pcmFile = new File(outPath);
        FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
        mediaCodec.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputBufferIndex = -1;
        while (true) {
            int decodeInputIndex = mediaCodec.dequeueInputBuffer(100000);
            if (decodeInputIndex >= 0) {
                long sampleTimeUs = mediaExtractor.getSampleTime();

                if (sampleTimeUs == -1) {
                    break;
                } else if (sampleTimeUs < startTime) {
//                    丢掉 不用了
                    mediaExtractor.advance();
                    continue;
                }else if (sampleTimeUs > endTime) {
                    break;
                }
//                获取到压缩数据
                info.size = mediaExtractor.readSampleData(buffer, 0);
                info.presentationTimeUs = sampleTimeUs;
                info.flags = mediaExtractor.getSampleFlags();

//                下面放数据  到dsp解码
                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
//                输出文件  方便查看
//                FileUtils.writeContent(content);
//                解码
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(decodeInputIndex);
                inputBuffer.put(content);
                mediaCodec.queueInputBuffer(decodeInputIndex, 0, info.size, info.presentationTimeUs, info.flags);
//                释放上一帧的压缩数据
                mediaExtractor.advance();
            }

            outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
            while (outputBufferIndex>=0) {
                ByteBuffer decodeOutputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                writeChannel.write(decodeOutputBuffer);//MP3  1   pcm2
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
            }
        }
        writeChannel.close();
        mediaExtractor.release();
        mediaCodec.stop();
        mediaCodec.release();
//        转换MP3    pcm数据转换成mp3封装格式
//
//        File wavFile = new File(Environment.getExternalStorageDirectory(),"output.mp3" );
//        new PcmToWavUtil(44100,  AudioFormat.CHANNEL_IN_STEREO,
//                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(pcmFile.getAbsolutePath()
//                , wavFile.getAbsolutePath());
        Log.i("David", "mixAudioTrack: 转换完毕");
    }
    private int selectTrack(MediaExtractor mediaExtractor) {
//获取每条轨道
        int numTracks = mediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
//            数据      MediaFormat
            MediaFormat format =mediaExtractor.getTrackFormat(i);
            String mime =   format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }
}

package com.mljo;

import org.bytedeco.ffmpeg.ffmpeg;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;

public class OpusToAudioInputStream {

    public static AudioInputStream convertOpusToAudioInputStream(InputStream opusStream) throws Exception {
        // InputStream vorbereiten
        BufferedInputStream bufferedInput = new BufferedInputStream(opusStream);
        bufferedInput.mark(50 * 1024 * 1024); // groß genug für Rücksprung

        // FFmpeg initialisieren mit vollständiger Codec/Format-Registrierung
//        FFmpegLogCallback.set(); // aktiviert FFmpeg-Logging in die Konsole
//        avutil.av_log_set_level(avutil.AV_LOG_INFO); // log level setzen
        // Vor dem Start des Grabbers:
        Loader.load(ffmpeg.class);
        // FFmpeg lesen
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(bufferedInput)) {
            grabber.setFormat("ogg"); // 🟢 Nicht "opus"!
            grabber.start();

            int sampleRate = grabber.getSampleRate();
            int channels = grabber.getAudioChannels();
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);

            ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();

            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                ShortBuffer buffer = (ShortBuffer) frame.samples[0];
                byte[] bytes = shortBufferToByteArray(buffer);
                pcmBuffer.write(bytes);
            }

            byte[] pcmBytes = pcmBuffer.toByteArray();
            ByteArrayInputStream byteStream = new ByteArrayInputStream(pcmBytes);

            return new AudioInputStream(byteStream, format, pcmBytes.length / format.getFrameSize());
        }
    }

    private static byte[] shortBufferToByteArray(ShortBuffer buffer) {
        buffer.rewind();
        byte[] bytes = new byte[buffer.remaining() * 2];
        for (int i = 0; i < buffer.remaining(); i++) {
            short sample = buffer.get(i);
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return bytes;
    }

    public static boolean isOpusStream(InputStream input) throws IOException {
        input = new BufferedInputStream(input);
        input.mark(64);  // wir lesen die ersten 64 Bytes, danach reset

        byte[] header = new byte[64];
        int bytesRead = input.read(header);
        input.reset();

        if (bytesRead < 64) return false;

        // Check for "OggS"
        boolean isOgg = header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';

        // Check for "OpusHead" at position ~28 (variiert leicht)
        String headerStr = new String(header, StandardCharsets.US_ASCII);
        boolean containsOpusHead = headerStr.contains("OpusHead");

        return isOgg && containsOpusHead;
    }

    // Testbeispiel
    public static void main(String[] args) throws Exception {
        System.out.println("FFmpeg version: " + avcodec.avcodec_version());

        Pointer codec = avcodec.avcodec_find_decoder_by_name("libopus");
        if (codec == null) {
            System.out.println("❌ libopus codec NOT found.");
        } else {
            System.out.println("✅ libopus codec is available.");
        }
        try (InputStream in = OpusToAudioInputStream.class.getResourceAsStream("/sample3.opus");) {
//            if(isOpusStream(in)){
                AudioInputStream ais = convertOpusToAudioInputStream(in);

                // Optional: Abspielen
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();

                Thread.sleep(clip.getMicrosecondLength() / 1000); // warten bis fertig
            }

//        }
    }
}
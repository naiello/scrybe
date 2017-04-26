package com.nan.scrybelistener;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Class to handle recording and buffering of voice data.
 *
 * Based on API sample code located at:
 *   https://github.com/GoogleCloudPlatform/android-docs-samples/blob/master/speech/Speech/app/src/main/java/com/google/cloud/android/speech/VoiceRecorder.java
 * Used under Apache 2.0 License:
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
public class VoiceRecorder {
    // Speech API supports the following sample rates
    // in 16-bit raw audio (lil-endian) format
    private static final int[] SAMPLE_RATE_CANDIDATES =
            new int[] {16000, 11025, 22050, 44100};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SILENCE_THRESHOLD = 1500;
    private static final int TIMEOUT_MILLIS = 2000;
    private static final int MAX_SPEECH_MILLIS = 30000;
    private static final String TAG = "VoiceRecorder";

    /**
     * Provides an asynchronous interface for us to pass the audio to a listener.
     * In this case, the listener is the API streamer.
     */
    public static abstract class Callback {
        public void onVoiceStart() {}
        public void onVoice(byte[] data, int size) {}
        public void onVoiceEnd() {}
    }

    private final Callback mCallback;
    private AudioRecord mAudioRecord;
    private byte[] mAudioBuffer;
    private long mLastHeard = Long.MAX_VALUE;
    private long mVoiceStarted;
    private final Object mLock = new Object();
    private Thread mThread;

    public VoiceRecorder(@NonNull Callback callback) {
        mCallback = callback;
    }

    public void start() {
        stop();
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            Log.e(TAG, "Cannot initialize recorder");
            throw new RuntimeException("Cannot initialize recorder.");
        }
    }

    public void stop() {
        synchronized (mLock) {
            Log.d(TAG, "stopping recording");
            dismiss();
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if (mAudioRecord != null) {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
            mAudioBuffer = null;
        }
    }

    public void dismiss() {
        if (mLastHeard != Long.MAX_VALUE) {
            mLastHeard = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }

    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }

    /**
     * Initializes the audio subsystem.
     * @return An {@link AudioRecord} object, or null on failure.
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }

            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, CHANNEL, ENCODING, bufferSize);

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mAudioBuffer = new byte[bufferSize];
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    /**
     * Separate thread to handle reading from mic and invoking callbacks.
     */
    private class ProcessVoice implements Runnable {
        @Override
        public void run() {
            while (true) {
                synchronized (mLock) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    final int sizeRead = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
                    final long now  = System.currentTimeMillis();

                    // is someone currently speaking?
                    if (isHearingVoice(mAudioBuffer, sizeRead)) {
                        if (mLastHeard == Long.MAX_VALUE) {
                            mVoiceStarted = now;
                            mCallback.onVoiceStart();
                        }
                        mCallback.onVoice(mAudioBuffer, sizeRead);
                        mLastHeard = now;

                        // the speech API has a maximum length, so cut the speaker
                        // off if they've been talking too long
                        // TODO: test to make sure this is handled smoothly by UI
                        if (now - mVoiceStarted > MAX_SPEECH_MILLIS) {
                            Log.d(TAG, "maximum speech time exceeded");
                            end();
                        }
                    } else if (mLastHeard != Long.MAX_VALUE) {
                        mCallback.onVoice(mAudioBuffer, sizeRead);

                        // has it been awhile since we heard anyone?
                        if (now - mLastHeard > TIMEOUT_MILLIS) {
                            Log.d(TAG, "maximum silence exceeded");
                            end();
                        }
                    }
                }
            }
        }

        private void end() {
            Log.d(TAG, "ending ProcessVoice thread");
            mLastHeard = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            for (int i = 0; i < size - 1; i += 2) {
                // buffer is 16-bit lil-endian, so convert to big-endian
                int s = buffer[i + 1];
                if (s < 0) s = -s;
                s <<= 8;
                s += Math.abs(buffer[i]);

                if (s > SILENCE_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }
    }
}

/*
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */

package info.guardianproject.phoneypot.codec;

import java.io.IOException;
import java.util.Arrays;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AudioCodec {

	private AudioRecord recorder = null;
	private int minSize;

	/**
	 * Configures the recorder and starts it
	 * @throws IOException
	 * @throws IllegalStateException
	 */
	public void start(Context context) throws IllegalStateException, IOException {
		// 1. Check for the RECORD_AUDIO permission
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			// Log an error or throw a specific exception to indicate the permission is missing.
			// In a real app, you would prompt the user for the permission here.
			Log.e("AudioCodec", "RECORD_AUDIO permission not granted. Cannot start recording.");
			throw new SecurityException("The RECORD_AUDIO permission is required but has not been granted.");
		}

		// 2. Proceed with recording if permission is granted
		if (recorder == null) {
			minSize = AudioRecord.getMinBufferSize(
					8000,
					AudioFormat.CHANNEL_IN_DEFAULT,
					AudioFormat.ENCODING_PCM_16BIT);
			Log.e("AudioCodec", "minimum size is " + minSize);

			// The AudioRecord constructor and startRecording() calls are now protected
			recorder = new AudioRecord(
					MediaRecorder.AudioSource.MIC,
					8000,
					AudioFormat.CHANNEL_IN_DEFAULT,
					AudioFormat.ENCODING_PCM_16BIT,
					minSize);

			if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
				// Handle initialization failure if the device doesn't support the configuration
				Log.e("AudioCodec", "audio record initialization failed");
				recorder = null; // Clean up
				return;
			}

			recorder.startRecording();
		}
	}
	/**
	 * Returns current sound level
	 * @return sound level
	 */
	public short[] getAmplitude() {
		if (recorder != null) {
			short[] buffer = new short[minSize / 2];  // minSize is in bytes, short is 2 bytes
			int readBytes = recorder.read(buffer, 0, buffer.length);
			if (readBytes > 0) {
				// Return the read data portion
				return Arrays.copyOf(buffer, readBytes);
			}
		}
		return null;
	}



	public void stop() {
        if (recorder != null
            && recorder.getState() != AudioRecord.STATE_UNINITIALIZED) {
        	recorder.stop();
        	recorder.release();
        	Log.i("AudioCodec", "Sampling stopped");
        }
        Log.i("AudioCodec", "Recorder set to null");
        recorder = null;
    }
}

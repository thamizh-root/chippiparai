package info.guardianproject.phoneypot.async;

import java.io.IOException;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import info.guardianproject.phoneypot.codec.AudioCodec;

public class MicSamplerTask extends AsyncTask<Void, Object, Void> {

	private MicListener listener = null;
	private AudioCodec volumeMeter;
	private boolean sampling = true;
	private boolean paused = false;
	private Context context;

	public static interface MicListener {
		void onSignalReceived(short[] signal);
		void onMicError();
	}

	public MicSamplerTask(Context context) {
		this.context = context;
		this.volumeMeter = new AudioCodec(); // No context in constructor
	}

	public void setMicListener(MicListener listener) {
		this.listener = listener;
	}

	@Override
	protected Void doInBackground(Void... params) {

		try {
			volumeMeter.start(context); // Pass context here
		} catch (Exception e) {
			Log.e("MicSamplerTask", "Failed to start VolumeMeter");
			e.printStackTrace();
			if (listener != null) {
				listener.onMicError();
			}
			return null;
		}

		while (true) {

			if (listener != null) {
				Log.i("MicSamplerTask", "Requesting amplitude");
				publishProgress(volumeMeter.getAmplitude());
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break; // Exit on interrupt
			}

			boolean restartVolumeMeter = false;
			if (paused) {
				restartVolumeMeter = true;
				volumeMeter.stop();
				sampling = false;
			}
			while (paused) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
			if (restartVolumeMeter) {
				try {
					Log.i("MicSamplerTask", "Task restarted");
					volumeMeter = new AudioCodec(); // No context here
					volumeMeter.start(context); // Pass context here
					sampling = true;
				} catch (IllegalStateException | IOException e) {
					e.printStackTrace();
				}
			}
			if (isCancelled()) {
				volumeMeter.stop();
				sampling = false;
				return null;
			}
		}

		volumeMeter.stop();
		return null;
	}

	public boolean isSampling() {
		return sampling;
	}

	public void restart() {
		paused = false;
		sampling = true;
	}

	public void pause() {
		paused = true;
	}

	@Override
	protected void onProgressUpdate(Object... progress) {
		short[] data = (short[]) progress[0];
		if (listener != null) {
			listener.onSignalReceived(data);
		}
	}
}

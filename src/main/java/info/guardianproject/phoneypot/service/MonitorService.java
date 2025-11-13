package info.guardianproject.phoneypot.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import android.telephony.SmsManager;

import java.util.Date;

import info.guardianproject.phoneypot.MonitorActivity;
import info.guardianproject.phoneypot.R;
import info.guardianproject.phoneypot.PreferenceManager;

@SuppressLint("HandlerLeak")
public class MonitorService extends Service {

	private static final String CHANNEL_ID = "monitor_service_channel";
	private static final int FOREGROUND_NOTIFICATION_ID = 1;

	private NotificationManager manager;
	public static final int ACCELEROMETER_MESSAGE = 0;
	public static final int CAMERA_MESSAGE = 1;
	public static final int MICROPHONE_MESSAGE = 2;
	private PreferenceManager prefs = null;
	int mNotificationAlertId = 7007;
	AccelerometerMonitor mAccelManager = null;
	MicrophoneMonitor mMicMonitor = null;
	PowerManager.WakeLock wakeLock;
	private int mLastAlert = -1;

	class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			alert(msg.what);
		}
	}

	private final Messenger messenger = new Messenger(new MessageHandler());

	@Override
	public void onCreate() {
		manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		prefs = new PreferenceManager(this);

		// startSensors();

		showNotification();

		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK,
				"honeypot:MyWakelockTag");
		wakeLock.acquire(10 * 60 * 1000L); // Acquire WakeLock with 10 minutes timeout
	}

	@Override
	public void onDestroy() {
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		stopSensors();
		stopForeground(true);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return messenger.getBinder();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Monitor Service Channel",
					NotificationManager.IMPORTANCE_LOW);
			manager.createNotificationChannel(channel);
		}
	}

	@SuppressWarnings("deprecation")
	private void showNotification() {
		createNotificationChannel();

		Intent toLaunch = new Intent(getApplicationContext(), MonitorActivity.class);
		toLaunch.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent resultPendingIntent = PendingIntent.getActivity(
				this,
				0,
				toLaunch,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
		);

		CharSequence text = getText(R.string.secure_service_started);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_phone_alert)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(text)
				.setContentIntent(resultPendingIntent)
				.setPriority(NotificationCompat.PRIORITY_LOW);

		startForeground(FOREGROUND_NOTIFICATION_ID, mBuilder.build());
	}

	private void startSensors() {
		mAccelManager = new AccelerometerMonitor(this);
		mMicMonitor = new MicrophoneMonitor(this);
	}

	private void stopSensors() {
		if (mAccelManager != null) mAccelManager.stop(this);
		if (mMicMonitor != null) mMicMonitor.stop(this);
	}

	private synchronized void alert(int alertType) {
		if (alertType == mLastAlert)
			return;

		StringBuilder alertMessage = new StringBuilder();
		alertMessage.append(getString(R.string.intrusion_detected));

		switch (alertType) {
			case ACCELEROMETER_MESSAGE:
				alertMessage.append(": Device was moved!");
				break;
			case MICROPHONE_MESSAGE:
				alertMessage.append(": Noise detected!");
				break;
			case CAMERA_MESSAGE:
				alertMessage.append(": Camera motion detected!");
				break;
		}

		alertMessage.append(" @ ").append(new Date().toLocaleString());

		if (prefs.getSmsActivation()) {
			SmsManager smsManager = SmsManager.getDefault();
			smsManager.sendTextMessage(prefs.getSmsNumber(), null, alertMessage.toString(), null, null);
		}

		showNotificationAlert(alertMessage.toString());

		mLastAlert = alertType;
	}

	private void showNotificationAlert(String message) {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_phone_alert)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(message);

		Intent resultIntent = new Intent(this, MonitorActivity.class);
		PendingIntent resultPendingIntent = PendingIntent.getActivity(
				this,
				0,
				resultIntent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
		);

		mBuilder.setContentIntent(resultPendingIntent);

		manager.notify(mNotificationAlertId++, mBuilder.build());
	}
}

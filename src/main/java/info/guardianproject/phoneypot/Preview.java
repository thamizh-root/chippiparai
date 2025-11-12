package info.guardianproject.phoneypot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import info.guardianproject.phoneypot.async.MotionAsyncTask;
import info.guardianproject.phoneypot.motiondetection.LuminanceMotionDetector;
import info.guardianproject.phoneypot.service.MonitorService;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {

	private PreferenceManager prefs;
	private int cameraFacing = 0;
	private List<MotionAsyncTask.MotionListener> listeners = new ArrayList<>();
	private long lastTimestamp;
	private byte[] lastPic;
	private boolean doingProcessing;
	private final Handler updateHandler = new Handler();
	private int imageCount = 0;
	private int motionSensitivity = LuminanceMotionDetector.MOTION_MEDIUM;
	private Messenger serviceMessenger = null;

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.i("CameraFragment", "SERVICE CONNECTED");
			serviceMessenger = new Messenger(service);
		}

		public void onServiceDisconnected(ComponentName arg0) {
			Log.i("CameraFragment", "SERVICE DISCONNECTED");
			serviceMessenger = null;
		}
	};

	SurfaceHolder mHolder;
	public Camera camera;
	private Context context;

	public Preview(Context context) {
		super(context);
		this.context = context;
		mHolder = getHolder();
		mHolder.addCallback(this);
		prefs = new PreferenceManager(context);

		if ("Medium".equals(prefs.getCameraSensitivity())) {
			motionSensitivity = LuminanceMotionDetector.MOTION_MEDIUM;
			Log.i("CameraFragment", "Sensitivity set to Medium");
		} else if ("Low".equals(prefs.getCameraSensitivity())) {
			motionSensitivity = LuminanceMotionDetector.MOTION_LOW;
			Log.i("CameraFragment", "Sensitivity set to Low");
		} else {
			motionSensitivity = LuminanceMotionDetector.MOTION_HIGH;
			Log.i("CameraFragment", "Sensitivity set to High");
		}
	}

	public void addListener(MotionAsyncTask.MotionListener listener) {
		listeners.add(listener);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

		context.bindService(new Intent(context, MonitorService.class), mConnection, Context.BIND_ABOVE_CLIENT);

		if ("Front".equals(prefs.getCamera())) {
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			int cameraCount = Camera.getNumberOfCameras();
			for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
				Camera.getCameraInfo(camIdx, cameraInfo);
				if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					try {
						camera = Camera.open(camIdx);
						cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
					} catch (RuntimeException e) {
						Log.e("Preview", "Camera failed to open: " + e.getLocalizedMessage());
					}
					break;
				}
			}
		} else {
			camera = Camera.open();
			cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
		}

		Camera.Parameters parameters = camera.getParameters();
		List<Size> sizes = parameters.getSupportedPictureSizes();
		Size selectedSize = null;

		for (Size s : sizes) {
			Log.i("SurfaceView", "width: " + s.width + " height: " + s.height);
			if (s.width <= 640) {
				selectedSize = s;
				Log.i("SurfaceView", "selected width: " + s.width + " selected height: " + s.height);
				break;
			}
		}
		if (selectedSize != null) {
			parameters.setPictureSize(selectedSize.width, selectedSize.height);
		}

		// Check if flash mode is supported before setting
		List<String> flashModes = parameters.getSupportedFlashModes();
		if (prefs.getFlashActivation() && flashModes != null && flashModes.contains(Parameters.FLASH_MODE_TORCH)) {
			Log.i("Preview", "Flash activated");
			parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
		}

		try {
			camera.setParameters(parameters);
		} catch (RuntimeException e) {
			Log.e("Preview", "Error setting camera parameters: " + e.getMessage());
			// Proceed without crashing. Possibly fallback or adjust parameters if needed.
		}

		try {
			camera.setPreviewDisplay(mHolder);

			camera.setPreviewCallback(new PreviewCallback() {

				public void onPreviewFrame(byte[] data, Camera cam) {

					final Camera.Size size = cam.getParameters().getPreviewSize();
					if (size == null) return;
					long now = System.currentTimeMillis();
					if (now < Preview.this.lastTimestamp + 1000)
						return;
					if (!doingProcessing) {

                        YuvImage image = new YuvImage(data, parameters.getPreviewFormat(),
                                size.width, size.height, null);

                        imageCount = (imageCount + 1) % (prefs.getMaxImages());

                        File directory = new File(context.getExternalFilesDir(null), prefs.getDirPath());
                        if (!directory.exists()) {
                            boolean created = directory.mkdirs();
                            if (!created) {
                                Log.e("Preview", "Failed to create directory: " + directory.getAbsolutePath());
                            }
                        }

                        File file = new File(directory, imageCount + ".jpg");

                        Log.i("Preview", "Saving image to file: " + file.getAbsolutePath());

                        try (FileOutputStream filecon = new FileOutputStream(file)) {
                            image.compressToJpeg(
                                    new Rect(0, 0, image.getWidth(), image.getHeight()), 90,
                                    filecon);
                        } catch (IOException e) {
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        Log.i("Preview", "Processing new image");
						Preview.this.lastTimestamp = now;
						MotionAsyncTask task = new MotionAsyncTask(
								lastPic,
								data,
								size.width,
								size.height,
								updateHandler,
								motionSensitivity);
						for (MotionAsyncTask.MotionListener listener : listeners) {
							Log.i("Preview", "Added listener");
							task.addListener(listener);
						}
						doingProcessing = true;
						task.addListener(new MotionAsyncTask.MotionListener() {

							public void onProcess(Bitmap oldBitmap, Bitmap newBitmap,
												  boolean motionDetected) {

								if (motionDetected) {
									Log.i("MotionListener", "Motion detected");
									if (serviceMessenger != null) {
										Message message = new Message();
										message.what = MonitorService.CAMERA_MESSAGE;
										try {
											serviceMessenger.send(message);
										} catch (RemoteException e) {
											// Cannot happen
										}
									}
								}
								Log.i("MotionListener", "Allowing further processing");
								doingProcessing = false;
							}
						});
						task.start();
						lastPic = data;
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (serviceMessenger != null) {
			try {
				context.unbindService(mConnection);
			} catch (IllegalArgumentException e) {
				Log.w("Preview", "Service not registered, skipping unbind");
			}
			serviceMessenger = null;
		}

		if (camera != null) {
			camera.setPreviewCallback(null);
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	public void releaseCamera() {
		if (camera != null) {
			try {
				context.unbindService(mConnection);
			} catch (IllegalArgumentException e) {
				// Service was not bound, ignore
			}
			camera.setPreviewCallback(null);
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		Camera.Parameters parameters = camera.getParameters();

		List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
		boolean sizeSupported = false;
		for (Size size : supportedSizes) {
			if (size.width == w && size.height == h) {
				sizeSupported = true;
				break;
			}
		}
		if (sizeSupported) {
			parameters.setPreviewSize(w, h);
		} else if (!supportedSizes.isEmpty()) {
			Size fallbackSize = supportedSizes.get(0);
			parameters.setPreviewSize(fallbackSize.width, fallbackSize.height);
		}

		int degree = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
		int displayOrientation = 0;
		switch (degree) {
			case Surface.ROTATION_0:
				displayOrientation = 90;
				break;
			case Surface.ROTATION_90:
				displayOrientation = 0;
				break;
			case Surface.ROTATION_180:
				displayOrientation = 0;
				break;
			case Surface.ROTATION_270:
				displayOrientation = 180;
				break;
		}
		camera.setDisplayOrientation(displayOrientation);

		try {
			camera.setParameters(parameters);
		} catch (RuntimeException e) {
			Log.e("Preview", "Error setting camera parameters in surfaceChanged: " + e.getMessage());
		}

		camera.startPreview();
	}

	public int getCameraFacing() {
		return this.cameraFacing;
	}
}

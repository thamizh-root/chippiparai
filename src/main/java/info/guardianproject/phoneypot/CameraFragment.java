package info.guardianproject.phoneypot;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import info.guardianproject.phoneypot.async.MotionAsyncTask;
import info.guardianproject.phoneypot.codec.ImageCodec;

public final class CameraFragment extends Fragment {

    private Preview preview;

    private ImageView oldImage;
    private ImageView newImage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the fragment's layout
        return inflater.inflate(R.layout.camera_fragment, container, false);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Use Fragment's view hierarchy safely
        View rootView = getView();
        if (rootView == null) return;

        if (preview == null) {
            preview = new Preview(getActivity());

            FrameLayout frameLayout = rootView.findViewById(R.id.preview);
            frameLayout.addView(preview);

            oldImage = rootView.findViewById(R.id.old_image);
            newImage = rootView.findViewById(R.id.new_image);

            // Setup MotionAsyncTask callback to update UI on the main thread
            preview.addListener(new MotionAsyncTask.MotionListener() {
                @Override
                public void onProcess(final Bitmap oldBitmap, final Bitmap newBitmap, final boolean motionDetected) {
                    // Ensure run on UI thread
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        int rotation;
                        boolean reflex;
                        if (preview.getCameraFacing() == Camera.CameraInfo.CAMERA_FACING_BACK) {
                            rotation = 90;
                            reflex = false;
                        } else {
                            rotation = 270;
                            reflex = true;
                        }
                        oldImage.setImageBitmap(ImageCodec.rotate(oldBitmap, rotation, reflex));
                        newImage.setImageBitmap(ImageCodec.rotate(newBitmap, rotation, reflex));
                    });
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Properly release camera preview resources
        if (preview != null) {
            preview.releaseCamera();
            preview = null;
        }
    }

    /**
     * Empty sensor change method. Override if needed.
     */
    public void onSensorChanged(android.hardware.SensorEvent event) {
        // Implement as needed or remove if unused
    }
}

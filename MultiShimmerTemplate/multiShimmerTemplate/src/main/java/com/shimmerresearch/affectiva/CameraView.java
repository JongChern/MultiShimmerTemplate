package com.shimmerresearch.affectiva;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.affectiva.android.affdex.sdk.Frame;

/**
 * A view to display the camera preview.
 *
 * This view consists of a SurfaceView object contained inside a FrameLayout.
 */
public class CameraView extends FrameLayout implements CameraHelper.OnCameraHelperEventListener {

    public interface OnCameraViewEventListener {
        void onCameraFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation);
        void onCameraStarted(boolean success, Throwable error);
        void onSurfaceViewSizeChanged();
    }

    public static String LOG_TAG = "Affectiva";

    SurfaceView surfaceView;
    CameraHelper cameraHelper;
    OnCameraViewEventListener listener;

    int previewHeight = 0;
    int previewWidth = 0;

    public CameraView(Context context) {
        super(context);
        initView(context);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public CameraView(Context context, AttributeSet attrs, int styleId) {
        super(context, attrs, styleId);
        initView(context);
    }

    void initView(Context context) {
        if (context == null)
            throw new NullPointerException("Context cannot be null");

        /*
            Create a SurfaceView subclass that resizes itself to match the aspect ratio of previewWidth and previewHeight,
            which are the dimensions of the camera preview chosen by CameraHelper.
            While an Android View typically needs to call the MeasureSpec.getMode() method to properly determine its size,
            we can neglect that step in this case because we know we want our SurfaceView to take up as space as possible
            while matching the camera's aspect ratio.
         */
        surfaceView = new SurfaceView(context) {
            @Override
            public void onMeasure(int widthSpec, int heightSpec) {
                int measureWidth = MeasureSpec.getSize(widthSpec);
                int measureHeight = MeasureSpec.getSize(heightSpec);
                int width;
                int height;
                if (previewHeight == 0 || previewWidth == 0) {
                    width = measureWidth;
                    height = measureHeight;
                } else {
                    float viewAspectRatio = (float)measureWidth/measureHeight;
                    float cameraPreviewAspectRatio = (float) previewWidth/previewHeight;

                    if (cameraPreviewAspectRatio > viewAspectRatio) {
                        width = measureWidth;
                        height =(int) (measureWidth / cameraPreviewAspectRatio);
                    } else {
                        width = (int) (measureHeight * cameraPreviewAspectRatio);
                        height = measureHeight;
                    }
                }
                if (listener!= null) {
                    listener.onSurfaceViewSizeChanged();
                }
                setMeasuredDimension(width,height);
            }
        };
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        surfaceView.setLayoutParams(params);
        this.addView(surfaceView);

        //Init cameraHelper, the class which controls our camera.
        cameraHelper = new CameraHelper(context,surfaceView,((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay());
        cameraHelper.setOnCameraHelperEventListener(this);
    }

    public void setOnCameraViewEventListener(OnCameraViewEventListener listener) {
        this.listener = listener;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    public void startCamera(CameraHelper.CameraType type) {
        cameraHelper.startCamera(type);
    }

    public void stopCamera() {
        cameraHelper.stopCamera();
    }

    /**
     * Bubble up received camera frames.
     */
    @Override
    public void onFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation) {
        if (listener!= null) {
            listener.onCameraFrameAvailable(frame, width, height, rotation);
        }
    }

    /**
     * Update the camera width and height variables, then request a resize of the SurfaceView.
     * Notice that CameraHelper notifies us of the physical, pre-rotation width and height of the camera
     * frames, so we need to account for possible rotation in this method.
     */
    @Override
    public void onFrameSizeSelected(int width, int height, Frame.ROTATE rotation) {
        if (rotation == Frame.ROTATE.BY_90_CW || rotation == Frame.ROTATE.BY_90_CCW) {
            previewHeight = width;
            previewWidth = height;
        } else {
            previewWidth = width;
            previewHeight = height;
        }
        surfaceView.requestLayout();
    }

    /**
     * Bubble up camera started event.
     */
    @Override
    public void onCameraStarted(boolean success, Throwable error) {
        if (listener != null) {
            listener.onCameraStarted(success,error);
        }
    }
}

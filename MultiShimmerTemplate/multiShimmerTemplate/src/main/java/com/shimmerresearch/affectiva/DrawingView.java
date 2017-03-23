package com.shimmerresearch.affectiva;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * This class contains a SurfaceView and its own thread that draws to it.
 * It is used to display the facial tracking dots over a user's face.
 */
public class DrawingView extends SurfaceView implements SurfaceHolder.Callback {

    //Inner Thread class
    class DrawingThread extends Thread {
        private SurfaceHolder mSurfaceHolder;
        private Paint circlePaint;
        private Paint boxPaint;
        private boolean stopFlag = false; //boolean to indicate when thread has been told to stop
        private PointF[] nextPointsToDraw = null; //holds a reference to the most recent set of points returned by CameraDetector, passed in by main thread
        private DrawingViewConfig config;
        private final long drawPeriod = 33; //draw at 30 fps

        private final int TEXT_RAISE = 10;

        String roll = "";
        String yaw = "";
        String pitch = "";
        String interOcDis = "";

        public DrawingThread(SurfaceHolder surfaceHolder, DrawingViewConfig con) {
            mSurfaceHolder = surfaceHolder;

            circlePaint = new Paint();
            circlePaint.setColor(Color.WHITE);
            boxPaint = new Paint();
            boxPaint.setColor(Color.WHITE);
            boxPaint.setStyle(Paint.Style.STROKE);

            config = con;

            setThickness(config.drawThickness);
        }

        void setMetrics(float roll, float yaw, float pitch, float interOcDis, float valence) {
            //format string for our DrawingView to use when ready
            this.roll = String.format("%.2f",roll);
            this.yaw = String.format("%.2f",yaw);
            this.pitch = String.format("%.2f",pitch);
            this.interOcDis = String.format("%.2f",interOcDis);

            //prepare the color of the bounding box using the valence score. Red for -100, White for 0, and Green for +100, with linear interpolation in between.
            if (valence > 0) {
                float colorScore = ((100f-valence)/100f)*255;
                boxPaint.setColor(Color.rgb((int)colorScore,255,(int)colorScore));
            } else {
                float colorScore = ((100f+valence)/100f)*255;
                boxPaint.setColor(Color.rgb(255, (int) colorScore, (int) colorScore));
            }

        }

        public void stopThread() {
            stopFlag = true;
        }

        public boolean isStopped() {
            return stopFlag;
        }

        //Updates thread with latest points returned by the onImageResults() event.
        public void updatePoints(PointF[] pointList) {
            nextPointsToDraw = pointList;
        }

        void setThickness(int thickness) {
            boxPaint.setStrokeWidth(thickness);
        }

        //Inform thread face detection has stopped, so array of points is no longer valid.
        public void invalidatePoints() {
            nextPointsToDraw = null;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        }

        void draw(Canvas c) {
            //Save our own reference to the list of points, in case the previous reference is overwritten by the main thread.
            PointF[] points = nextPointsToDraw;

            //Coordinates around which to draw bounding box.
            float leftBx = config.surfaceViewWidth;
            float rightBx = 0;
            float topBx = config.surfaceViewHeight;
            float botBx = 0;

            for (int i = 0; i < points.length; i++) {

                //transform from the camera coordinates to our screen coordinates
                //The camera preview is displayed as a mirror, so X pts have to be mirrored back.
                float x = (config.imageWidth - points[i].x - 1) * config.screenToImageRatio;
                float y = (points[i].y)* config.screenToImageRatio;

                //We determine the left-most, top-most, right-most, and bottom-most points to draw the bounding box around.
                if (x < leftBx)
                    leftBx = x;
                if (x > rightBx)
                    rightBx = x;
                if (y < topBx)
                    topBx = y;
                if (y > botBx)
                    botBx = y;

                //Draw facial tracking dots.
                if (config.isDrawPointsEnabled) {
                    c.drawCircle(x, y, config.drawThickness, circlePaint);
                }
            }

            //Draw the bounding box.
            if (config.isDrawPointsEnabled) {
                c.drawRect(leftBx, topBx, rightBx, botBx, boxPaint);
            }

            //Draw the measurement metrics, with a dark border around the words to make them visible for users of all skin colors.
            if (config.isDrawMeasurementsEnabled) {
                float centerBx = (leftBx + rightBx) / 2;

                float upperText = topBx - TEXT_RAISE;
                c.drawText("PITCH", centerBx, upperText - config.textSize,config.textBorderPaint);
                c.drawText("PITCH", centerBx, upperText - config.textSize, config.textPaint);
                c.drawText(pitch,centerBx ,upperText ,config.textBorderPaint);
                c.drawText(pitch, centerBx, upperText, config.textPaint);

                float upperLeft = centerBx - config.upperTextSpacing;

                c.drawText("YAW", upperLeft , upperText - config.textSize , config.textBorderPaint);
                c.drawText("YAW", upperLeft, upperText - config.textSize, config.textPaint);
                c.drawText(yaw, upperLeft , upperText , config.textBorderPaint);
                c.drawText(yaw, upperLeft, upperText, config.textPaint);

                float upperRight = centerBx + config.upperTextSpacing;
                c.drawText("ROLL", upperRight , upperText - config.textSize , config.textBorderPaint);
                c.drawText("ROLL", upperRight, upperText - config.textSize, config.textPaint);
                c.drawText(roll, upperRight , upperText , config.textBorderPaint);
                c.drawText(roll, upperRight, upperText, config.textPaint);

                c.drawText("INTEROCULAR DISTANCE", centerBx , botBx + config.textSize , config.textBorderPaint);
                c.drawText("INTEROCULAR DISTANCE", centerBx, botBx + config.textSize, config.textPaint);
                c.drawText(interOcDis,centerBx , botBx + config.textSize*2 , config.textBorderPaint);
                c.drawText(interOcDis, centerBx, botBx + config.textSize * 2, config.textPaint);
            }


        }
    }

    class DrawingViewConfig {
        private int imageHeight = 1;
        private int imageWidth = 1;
        private int surfaceViewWidth = 0;
        private int surfaceViewHeight = 0;
        private float screenToImageRatio = 0;
        private int drawThickness = 0;
        private boolean isImageDimensionsNeeded = true;
        private boolean isSurfaceViewDimensionsNeeded = true;
        private boolean isDrawPointsEnabled = true; //by default, have the drawing thread draw tracking dots
        private boolean isDrawMeasurementsEnabled = false;

        private Paint textPaint;
        private int textSize;
        private Paint textBorderPaint;
        private int upperTextSpacing;

        public void setMeasurementMetricConfigs(Paint textPaint, Paint dropShadowPaint, int textSize, int upperTextSpacing) {
            this.textPaint = textPaint;
            this.textSize = textSize;
            this.textBorderPaint = dropShadowPaint;
            this.upperTextSpacing = upperTextSpacing;
        }

        public void updateImageDimensions(int w, int h) {

            if ( (w <= 0) || (h <= 0)) {
                throw new IllegalArgumentException("Image Dimensions must be positive.");
            }

            imageWidth = w;
            imageHeight = h;
            if (!isSurfaceViewDimensionsNeeded) {
                screenToImageRatio = (float)surfaceViewWidth / (float)imageWidth;
            }
            isImageDimensionsNeeded = false;
        }

        public void updateSurfaceViewDimensions(int w, int h) {

            if ( (w <= 0) || (h <= 0)) {
                throw new IllegalArgumentException("SurfaceView Dimensions must be positive.");
            }

            surfaceViewWidth = w;
            surfaceViewHeight = h;
            if (!isImageDimensionsNeeded) {
                screenToImageRatio = (float)surfaceViewWidth / (float)imageWidth;
            }
            isSurfaceViewDimensionsNeeded = false;
        }

        public void setDrawThickness(int t) {

            if ( t <= 0) {
                throw new IllegalArgumentException("Thickness must be positive.");
            }

            drawThickness = t;
        }
    }

    //Class variables of DrawingView class
    private SurfaceHolder surfaceHolder;
    private DrawingThread drawingThread; //DrawingThread object
    private Typeface typeface;
    private DrawingViewConfig drawingViewConfig;
    private static String LOG_TAG = "AffdexMe";


    //three constructors required of any custom view
    public DrawingView(Context context) {
        super(context);
        initView(context, null);
    }
    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }
    public DrawingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    void initView(Context context, AttributeSet attrs){
        surfaceHolder = getHolder(); //The SurfaceHolder object will be used by the thread to request canvas to draw on SurfaceView
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT); //set to Transparent so this surfaceView does not obscure the one it is overlaying (the one displaying the camera).
        surfaceHolder.addCallback(this); //become a Listener to the three events below that SurfaceView throws
        this.setZOrderMediaOverlay(true);

        drawingViewConfig = new DrawingViewConfig();

        //default values
        int upperTextSpacing = 15;
        int textSize = 15;

        Paint measurementTextPaint = new Paint();
        measurementTextPaint.setStyle(Paint.Style.FILL);
        measurementTextPaint.setTextAlign(Paint.Align.CENTER);

        Paint dropShadow = new Paint();
        dropShadow.setColor(Color.BLACK);
        dropShadow.setStyle(Paint.Style.STROKE);
        dropShadow.setTextAlign(Paint.Align.CENTER);

        drawingViewConfig.setMeasurementMetricConfigs(measurementTextPaint, dropShadow, textSize, upperTextSpacing);

        drawingThread = new DrawingThread(surfaceHolder, drawingViewConfig);
    }
    
    public void setTypeface(Typeface face) {
        drawingViewConfig.textPaint.setTypeface(face);
        drawingViewConfig.textBorderPaint.setTypeface(face);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (drawingThread.isStopped()) {
            drawingThread = new DrawingThread(surfaceHolder, drawingViewConfig);
        }
        drawingThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //command thread to stop, and wait until it stops
        boolean retry = true;
        drawingThread.stopThread();
        while (retry) {
            try {
                drawingThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.e(LOG_TAG,e.getMessage());
            }
        }
    }

    public boolean isImageDimensionsNeeded() {
        return drawingViewConfig.isImageDimensionsNeeded;
    }

    public boolean isSurfaceDimensionsNeeded() {
        return drawingViewConfig.isSurfaceViewDimensionsNeeded;
    }

    public void invalidateImageDimensions() {
        drawingViewConfig.isImageDimensionsNeeded = true;
    }

    public void updateImageDimensions(int w, int h) {
        drawingViewConfig.updateImageDimensions(w, h);
    }

    public void updateSurfaceViewDimensions(int w, int h) {
        drawingViewConfig.updateSurfaceViewDimensions(w, h);
    }

    public void setThickness(int t) {
        drawingViewConfig.setDrawThickness(t);
        drawingThread.setThickness(t);
    }

    public void setDrawPointsEnabled(boolean b){
        drawingViewConfig.isDrawPointsEnabled = b;
    }

    public boolean getDrawPointsEnabled() {
        return drawingViewConfig.isDrawPointsEnabled;
    }

    public  void setDrawMeasurementsEnabled(boolean b) {
        drawingViewConfig.isDrawMeasurementsEnabled = b;
    }

    public boolean getDrawMeasurementsEnabled() {
        return drawingViewConfig.isDrawMeasurementsEnabled;
    }

    public void setMetrics(float roll, float yaw, float pitch, float interOcDis, float valence) {
        drawingThread.setMetrics(roll,yaw,pitch,interOcDis,valence);
    }

    public void updatePoints(PointF[] points) {
        drawingThread.updatePoints(points);
    }

    public void invalidatePoints(){
        drawingThread.invalidatePoints();
    }




}

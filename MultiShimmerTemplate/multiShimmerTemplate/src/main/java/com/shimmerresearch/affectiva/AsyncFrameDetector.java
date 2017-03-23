package com.shimmerresearch.affectiva;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.affectiva.android.affdex.sdk.detector.FrameDetector;

import java.util.List;

/**
 * A class which instantiates and runs a FrameDetector on a background thread.
 * The background thread is defined in an inner class and is re-created for any pair of start() stop() calls the user makes.
 */
public class AsyncFrameDetector {

    public interface OnDetectorEventListener {
        void onImageResults(List<Face> faces, Frame image, float timeStamp);

        void onDetectorStarted();
    }

    private static final int MAX_FRAMES_WAITING = 1;
    private FrameDetectorThread detectorThread;
    private Context context;
    private boolean isRunning;
    private MainThreadHandler mainThreadHandler;
    private FrameDetectorHandler detectorThreadHandler;
    private OnDetectorEventListener listener;

    /*
     Since FrameDetector is run on a background thread based off Android's HandlerThread class, it will receive frames to process
     in a queue. It is possible that this queue could grow in size, causing FrameDetector to incur a 'debt' of frames to process.
     To avoid this, we define a maximum number of frames that this waiting queue is allowed to have before we submit any more frames.
     */
    private int framesWaiting = 0;

    public AsyncFrameDetector(Context context) {
        this.context = context;
        mainThreadHandler = new MainThreadHandler(this);
    }

    public void setOnDetectorEventListener(OnDetectorEventListener listener) {
        this.listener = listener;
    }

    /*
     * Starts running FrameDetector on a background thread.
     * Note that FrameDetector is not guaranteed to have started by the time this call returns, because it is
     * started asynchronously.
     */
    public void start() {
        if (isRunning)
            throw new RuntimeException("Called start() without calling stop() first.");

        isRunning = true;

        // create and start the background detector thread
        detectorThread = new FrameDetectorThread();
        detectorThread.start();

        // create a handler for the detector thread, and send it a start message
        detectorThreadHandler = new FrameDetectorHandler(context, mainThreadHandler, detectorThread);
        detectorThreadHandler.sendStartMessage();

        framesWaiting = 0;
    }

    /*
     * Asynchronously stops the FrameDetector.
     */
    public void stop() {
        if (!isRunning)
            throw new RuntimeException("Called stop() without calling start() first");

        detectorThreadHandler.sendStopMessage();

        // facilitate GC of the detector thread and handler.  The last reference to the handler
        // will be the one in the stop message -- once that message has been processed by the
        // handler, it will be eligible for GC
        detectorThread = null;
        detectorThreadHandler = null;

        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void process(Frame frame, float timestamp) {
        if (isRunning) {
            if (framesWaiting <= MAX_FRAMES_WAITING) {
                framesWaiting += 1;
                detectorThreadHandler.sendProcessFrameMessage(new InputData(frame, timestamp));
            }
        }
    }

    public void reset() {
        if (isRunning) {
            detectorThreadHandler.sendResetMessage();
            framesWaiting = 0;
        }
    }

    /*
        Notify our listener that FrameDetector start has completed.
     */
    private void notifyDetectorStarted() {
        if (isRunning && listener != null) {
            listener.onDetectorStarted();
        }
    }

    /*
        Send processed frame data to our listener.
     */
    private void notifyImageResults(List<Face> faces, Frame frame, float timestamp) {
        framesWaiting -= 1;
        if (framesWaiting < 0) {
            framesWaiting = 0;
        }
        Log.d("AsyncFrameDetector", String.format("Frames in queue: %d", framesWaiting));

        if (isRunning && listener != null) {
            listener.onImageResults(faces, frame, timestamp);
        }
    }

    private static class MainThreadHandler extends Handler {
        private static final int FRAME_READY = 0;
        private static final int DETECTOR_STARTED = 1;

        private AsyncFrameDetector asyncFrameDetector;

        MainThreadHandler(AsyncFrameDetector asyncFrameDetector) {
            super(Looper.getMainLooper());
            this.asyncFrameDetector = asyncFrameDetector;
        }

        private void sendDetectorStartedMessage() {
            sendMessage(obtainMessage(DETECTOR_STARTED));
        }

        private void sendFrameReadyMessage(OutputData data) {
            sendMessage(obtainMessage(FRAME_READY, data));
        }


        /*
         Process messages on the main thread that were sent from the background thread.
         */
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DETECTOR_STARTED:
                    asyncFrameDetector.notifyDetectorStarted();
                    break;
                case FRAME_READY:
                    OutputData frameData = (OutputData) msg.obj;
                    asyncFrameDetector.notifyImageResults(frameData.faces, frameData.frame, frameData.timestamp);
                    break;
                default:
                    // IGNORE
                    break;
            }
        }
    }

    /**
     * A background thread for performing frame detection.  See Android HandlerThread class
     * documentation for more information on how this class works.
     */
    private static class FrameDetectorThread extends HandlerThread {

        private FrameDetectorThread() {
            super("FrameDetectorThread", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        }
    }


    private static class FrameDetectorHandler extends Handler {
        //Incoming message codes
        private static final int START_DETECTOR = 0;
        private static final int PROCESS_FRAME = 1;
        private static final int STOP_DETECTOR = 2;
        private static final int RESET_DETECTOR = 3;
        private static final String LOG_TAG = "Affectiva";
        private Context context;
        private FrameDetector detector;
        private MainThreadHandler mainThreadHandler;


        private FrameDetectorHandler(Context context, MainThreadHandler mainThreadHandler, HandlerThread detectorThread) {
            super(detectorThread.getLooper());
            this.context = context;
            this.mainThreadHandler = mainThreadHandler;
        }

        private void sendStartMessage() {
            sendMessage(obtainMessage(START_DETECTOR));
        }

        private void sendStopMessage() {
            emptyQueue();
            sendMessage(obtainMessage(STOP_DETECTOR));
        }

        private void sendProcessFrameMessage(InputData data) {
            sendMessage(obtainMessage(PROCESS_FRAME, data));
        }

        private void sendResetMessage() {
            emptyQueue();
            sendMessage(obtainMessage(RESET_DETECTOR));
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_DETECTOR:
                    startDetector();
                    break;
                case PROCESS_FRAME:
                    processFrame((InputData) msg.obj);
                    break;
                case STOP_DETECTOR:
                    stopDetector();
                    mainThreadHandler = null;
                    context = null;
                    detector = null;
                    Log.d(LOG_TAG, "Quitting FrameDetectorThread");
                    ((HandlerThread) getLooper().getThread()).quit();
                    break;
                case RESET_DETECTOR:
                    resetDetector();
                    break;
                default:
                    break;
            }
        }

        /*
         * When resetting or stopping the detector, we don't want our command to have to wait for messages in front of it to
         * finish processing, so we purge any non-critical messages, namely PROCESS_FRAME and RESET_DETECTOR.
         */
        private void emptyQueue() {
            removeMessages(PROCESS_FRAME);
            removeMessages(RESET_DETECTOR);
        }

        private void startDetector() {

            detector = new FrameDetector(context);
            detector.setDetectAllEmotions(true);
            detector.setDetectAllExpressions(true);
            detector.setDetectAllAppearances(true);
            detector.setDetectAllEmojis(false);
            detector.setDetectGender(true);

            detector.setImageListener(new Detector.ImageListener() {
                @Override
                public void onImageResults(List<Face> faceList, Frame frame, float timeStamp) {
                    OutputData data = new OutputData(faceList, frame, timeStamp);
                    mainThreadHandler.sendFrameReadyMessage(data);
                }
            });

            detector.start();

            mainThreadHandler.sendDetectorStartedMessage();
        }

        private void stopDetector() {
            detector.setImageListener(null);
            try {
                detector.stop();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }

        private void processFrame(InputData data) {
            if (detector.isRunning()) {
                detector.process(data.frame, data.timestamp);
            }
        }

        private void resetDetector() {
            if (detector.isRunning()) {
                detector.reset();
            }
            Log.i(LOG_TAG, "Detector reset");
        }
    }

    private static class OutputData {
        public final List<Face> faces;
        public final Frame frame;
        public final float timestamp;

        public OutputData(List<Face> faces, Frame frame, float timestamp) {
            this.faces = faces;
            this.frame = frame;
            this.timestamp = timestamp;
        }
    }

    private static class InputData {
        public Frame frame;
        public float timestamp;

        public InputData(Frame frame, float timestamp) {
            this.frame = frame;
            this.timestamp = timestamp;
        }
    }
}

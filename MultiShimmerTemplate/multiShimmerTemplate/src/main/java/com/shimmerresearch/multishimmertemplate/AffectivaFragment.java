package com.shimmerresearch.multishimmertemplate;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.shimmerresearch.affectiva.AsyncFrameDetector;
import com.shimmerresearch.affectiva.CameraHelper;
import com.shimmerresearch.affectiva.CameraView;
import com.shimmerresearch.affectiva.Metrics;
import com.shimmerresearch.service.MultiShimmerTemplateService;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link AffectivaFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link AffectivaFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AffectivaFragment extends Fragment implements CameraView.OnCameraViewEventListener, AsyncFrameDetector.OnDetectorEventListener {


    CameraView cameraView; //To control the camera
    AsyncFrameDetector asyncDetector; //Run FrameDetector on a background thread

    Button cameraButton, captureButton;
    ToggleButton cameraToggle;

    private static final String LOG_TAG = "Affectiva";

    //TODO: Implement FPS counting
    TextView processorFPS, cameraFPS, emotionScore;

    boolean isCameraStarted  = false;
    boolean isCameraFront = true;
    boolean isCameraRequestedByUser = false;
    boolean isSDKRunning = false;

    //Variables for determining the FPS rates of frames sent
    long numberCameraFramesReceived = 0;
    long lastCameraFPSResetTime = -1L;
    long numberSDKFramesReceived = 0;
    long lastSDKFPSResetTime = -1L;

    int startTime = 0;

    //Floats to ensure the timestamps we send to FrameDetector are sequentially increasing
    float lastTimestamp = -1f;
    final float epsilon = .01f;
    long firstFrameTime = -1;

    private OnFragmentInteractionListener mListener;


    public AffectivaFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment AffectivaFragment.
     */

    // TODO: Rename and change types and number of parameters
    public static AffectivaFragment newInstance(String param1, String param2) {
        AffectivaFragment fragment = new AffectivaFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    void resetFPS() {
        lastCameraFPSResetTime = lastSDKFPSResetTime = SystemClock.elapsedRealtime();
        numberCameraFramesReceived = numberSDKFramesReceived = 0;
    }

    /**
     * Method to start the camera
     */
    void startCamera() {
        if(isCameraStarted == true) {
            cameraView.stopCamera();
        }

        cameraView.startCamera(isCameraFront ? CameraHelper.CameraType.CAMERA_FRONT : CameraHelper.CameraType.CAMERA_BACK);
        isCameraStarted = true;
        asyncDetector.reset();
    }

    /**
     * Method to stop the camera
     */
    void stopCamera() {
        if (isCameraStarted == false)
            return;

        cameraView.stopCamera();
        isCameraStarted = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_affectiva, container, false);

        //Initialize text
        cameraFPS = (TextView) rootView.findViewById(R.id.camera_fps_text);
        processorFPS = (TextView) rootView.findViewById(R.id.processor_fps_text);
        emotionScore = (TextView) rootView.findViewById(R.id.emotion_score_text);
        emotionScore.setText("Surprise: 0");

        //Set up Camera View from Affectiva Class
        cameraView = (CameraView) rootView.findViewById(R.id.camera_view);
        cameraView.setOnCameraViewEventListener(this);

        //Initialize buttons
        cameraButton = (Button) rootView.findViewById(R.id.camera_button);
        captureButton = (Button) rootView.findViewById(R.id.capture_button);
        cameraToggle = (ToggleButton) rootView.findViewById(R.id.toggle_button);


        //To start and stop the camera
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCameraRequestedByUser) { //Turn camera off
                    isCameraRequestedByUser = false;
                    cameraButton.setText("Start Camera");
                    stopCamera();
                } else { //Turn camera on
                    isCameraRequestedByUser = true;
                    cameraButton.setText("Stop Camera");
                    startCamera();
                }
                resetFPS();
            }
        });

        //Initialize Front/Back camera toggle button
        cameraToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isCameraFront = !isChecked;
                if(isCameraRequestedByUser) {
                    startCamera();
                }
            }

        });

        //TODO: Does getActivity() work in this context?
        asyncDetector = new AsyncFrameDetector(getActivity());
        asyncDetector.setOnDetectorEventListener(this);

        //Set up capture button
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isSDKRunning == true) {
                    isSDKRunning = false;
                    asyncDetector.stop();;
                    captureButton.setText("Start Processing Emotions");

                } else {
                    isSDKRunning = true;
                    asyncDetector.start();
                    captureButton.setText("Stop Processing Emotions");
                }

                resetFPS();
            }
        });
        captureButton.setText("Start Processing Emotions");

        return rootView;

    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }


/*    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }*/


    /**
     * Release the camera once the fragment has been paused.
     */
    @Override
    public void onPause(){
        super.onPause();
        if (asyncDetector.isRunning()) {
            asyncDetector.stop();
        }
        stopCamera(); //Make sure the camera is stopped once the fragment is stopped.
    }

    /**
     * Ensure the camera is running if the fragment is resumed.
     */
    @Override
    public void onResume() {
        super.onResume();
        if(isSDKRunning == true) {
            asyncDetector.start();

        }
        if (isCameraRequestedByUser == true) {
            startCamera();
        }

        resetFPS();
    }

    /**
     * Get the data metrics from the SDK
     */


/*    private void setMetricTextViewText(Face face) {
        //To display the emotion score metrics

        for(Metrics metric : Metrics.getEmotions()) {

        }

    }*/

    //To get the emotion scores
    private float getScore(int emotion, Face face) {

        /**
        switch(emotion) {
            case "ANGER":
                return face.emotions.getAnger();
            case "CONTEMPT":
                return face.emotions.getContempt();
            case "DISGUST":
                return face.emotions.getDisgust();
            case "FEAR":
                return face.emotions.getFear();
            case "JOY":
                return face.emotions.getJoy();
            case "SADNESS":
                return face.emotions.getSadness();
            case "SURPRISE":
                return face.emotions.getSurprise();
            default:
                return 0;

        }
         */

        switch(emotion) {
            case 1:
                return face.emotions.getAnger();
            case 2:
                return face.emotions.getContempt();
            case 3:
                return face.emotions.getDisgust();
            case 4:
                return face.emotions.getFear();
            case 5:
                return face.emotions.getJoy();
            case 6:
                return face.emotions.getSadness();
            case 7:
                return face.emotions.getSurprise();
            default:
                return 0;

        }

    }


    @Override
    public void onCameraFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation) {

        //TODO: Implement FPS counter
        numberCameraFramesReceived += 1;
        cameraFPS.setText(String.format("CAM: %.3f", 1000f * (float) numberCameraFramesReceived / (SystemClock.elapsedRealtime() - lastCameraFPSResetTime)));

        float timestamp = 0;
        long currentTime = SystemClock.elapsedRealtime();
        if (firstFrameTime == -1) {
            firstFrameTime = currentTime;
        } else {
            timestamp = (currentTime - firstFrameTime) / 1000f;
        }

        if (timestamp > (lastTimestamp + epsilon)) {
            lastTimestamp = timestamp;
            asyncDetector.process(createFrameFromData(frame,width,height,rotation),timestamp);
        }
    }


    static Frame createFrameFromData(byte[] frameData, int width, int height, Frame.ROTATE rotation) {
        Frame.ByteArrayFrame frame = new Frame.ByteArrayFrame(frameData, width, height, Frame.COLOR_FORMAT.YUV_NV21);
        frame.setTargetRotation(rotation);
        return frame;
    }

    float lastReceivedTimeStamp = -1f;

    @Override
    public void onImageResults(List<Face> faces, Frame image, float timeStamp) {
        if(timeStamp < lastReceivedTimeStamp)
            throw new RuntimeException("Got a timestamp out of order!");
        lastReceivedTimeStamp = timeStamp;
        //Log.e("Affectiva Fragment", String.valueOf(timeStamp));

        if (faces == null)
            return; //No faces detected
        if(faces.size() == 0) {
            for(Metrics metric : Metrics.values()) {
                //metricsPanel.setMetricNA(metric);
            }
        }
        else {
            Face face = faces.get(0);
            String[] s = {"ANGER", "CONTEMPT", "DISGUST", "FEAR", "JOY", "SADNESS", "SURPRISE"};

            //Aggregate the emotion scores and send the data msg to the Shimmer Service
            float score = 0;
            float[] scoreData = new float[7];

            for(int i = 0; i < 7; i++) {
                score = getScore(i+1, face);
                s[i] = s[i] + " score: " + String.format("%.2f", score);
                scoreData[i] = score;
            }

            //Send the intent containing the data to the service, which will receive in onStartCommand()
            Intent intent = new Intent(getActivity(), MultiShimmerTemplateService.class);
            Bundle b = new Bundle();
            b.putFloatArray("AFFECTIVA", scoreData);
            intent.putExtras(b);
            getActivity().startService(intent);

            //Display the aggregated scores in the UI
            String emotionText = "";
            for(int i = 0; i < 7; i++) {
                emotionText = emotionText + s[i] + "\n";
            }
            emotionScore.setText(emotionText);

        }
    }


    /**
     * Automatically generated methods.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onCameraStarted(boolean success, Throwable error) {
        //Change status here
    }

    @Override
    public void onSurfaceViewSizeChanged() {
        asyncDetector.reset();
    }

    @Override
    public void onDetectorStarted() {

    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}

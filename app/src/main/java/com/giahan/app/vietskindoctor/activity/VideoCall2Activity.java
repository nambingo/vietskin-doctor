package com.giahan.app.vietskindoctor.activity;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import com.giahan.app.vietskindoctor.R;
import com.giahan.app.vietskindoctor.services.rtc.CustomPeerConnectionObserver;
import com.giahan.app.vietskindoctor.services.rtc.CustomSdpObserver;
import com.giahan.app.vietskindoctor.services.rtc.SignallingClient;
import com.giahan.app.vietskindoctor.utils.Constant;
import com.giahan.app.vietskindoctor.utils.PermissionsUtil;
import com.giahan.app.vietskindoctor.utils.PrefHelper_;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

public class VideoCall2Activity extends AppCompatActivity implements SignallingClient.SignalingInterface {

    public final static int CAMERA_PERMISSION_ID = 101;

    private Socket mSocket;

    PeerConnectionFactory peerConnectionFactory;

    MediaConstraints audioConstraints;

    MediaConstraints videoConstraints;

    MediaConstraints sdpConstraints;

    VideoSource videoSource;

    VideoTrack localVideoTrack;

    AudioSource audioSource;

    AudioTrack localAudioTrack;

    SurfaceViewRenderer localVideoView;

    SurfaceViewRenderer remoteVideoView;

    ImageView hangup;

    PeerConnection localPeer;

    //    List<IceServer> iceServers;
    EglBase rootEglBase;

    List<IceServer> peerIceServers = new ArrayList<>();

    private String mDoctorID;

    private String mPatientID;

    public PrefHelper_ pref;

    private void checkPermission() {
        if (checkStorePermission(this, CAMERA_PERMISSION_ID)) {
            implementData();
        } else {
            showRequestPermission(this, CAMERA_PERMISSION_ID);
        }
    }

    private void implementData() {
        initViews();
        initVideos();
        getDataFromServer();
        getDataFromChat();
        setupSocket();
        toInviteCall();
        receiverAccept();
//        receiverInviteCall();
        receiverAnswer();
        receiverCandidate();
        receiverFinishVideo();

        SignallingClient.getInstance().init(this, mSocket);
        start();
    }

    private void receiverAccept() {
        mSocket.on(Constant.TAG_VIDEO_ACCEPT, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            Log.e("VideoCall2Activity", "receiverInviteCall:  -----> NHAN ACCPET OK");
            doCall();
        }));
    }

    private void toInviteCall() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constant.TAG_TO_USER_ID, Integer.parseInt(mPatientID));
            mSocket.emit(Constant.TAG_VIDEO_INVITE, jsonObject);
            Log.e("VideoCall2Activity", "toInviteCall:  -----> TO INVITE OK");
//            createPeerConnection();
//            receiverOffer();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void receiverAnswer() {
        mSocket.on(Constant.TAG_RTC_ANSWER_SOCKET, args -> runOnUiThread(() -> {
            Log.e("----SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
            try {
                Log.e("VideoCall2Activity", "receiverAnswer:  -----> Answer ok");
                JSONObject data = (JSONObject) args[0];
                JSONObject sdp = data.getJSONObject("sdp");
                Log.e("---SignallingClient", "Json Received :: " + data.toString());
                String type = sdp.getString("type");
                if (type.equalsIgnoreCase("offer")) {
                    onOfferReceived(sdp);
                } else if (type.equalsIgnoreCase("answer")) {
                    onAnswerReceived(sdp);
                } else if (type.equalsIgnoreCase("candidate")) {
                    onIceCandidateReceived(sdp);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }));
    }

    private void receiverFinishVideo() {
        mSocket.on(Constant.TAG_VIDEO_FINISH, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            if (jsonObject != null) {
                Log.e("VideoCallActivity", "receiverFinishVideo:  -----> ");

                mSocket.disconnect();
                mSocket.close();
                finish();
            }

        }));
    }

    private void getDataFromChat() {
        mDoctorID = getIntent().getStringExtra("DOCTOR_ID");
        mPatientID = getIntent().getStringExtra("PATIENT_ID");
    }

    private void receiverCandidate() {
        mSocket.on(Constant.TAG_RTC_CANDIDATE_SOCKET, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            Log.e("VideoCall2Activity", "receiverInviteCall:  -----> NHAN CANDIDATE OK");
            try {
                localPeer.addIceCandidate(
                        new IceCandidate(jsonObject.getString("sdpMid"), jsonObject.getInt("sdpMLineIndex"),
                                jsonObject.getString("candidate")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));
    }

    public void start() {
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());
//        peerConnectionFactory = PeerConnectionFactory.initializeAndroidGlobals(this, false, false, false);
        peerConnectionFactory = new PeerConnectionFactory(options, defaultVideoEncoderFactory,
                defaultVideoDecoderFactory);
        peerConnectionFactory
                .setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(), rootEglBase.getEglBaseContext());

        //Now create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            Log.e("VideoCallActivity", "start:  -----> NOT NULL ");
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("900", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("901", audioSource);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }
        localVideoView.setVisibility(View.VISIBLE);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addSink(localVideoView);

        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);
        onTryToStart();
    }

    private void initViews() {
        hangup = findViewById(R.id.end_call);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
        hangup.setOnClickListener(view -> onHangup());
    }

    private void onHangup() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constant.TAG_TO_USER_ID, Integer.parseInt(mPatientID));
            mSocket.emit(Constant.TAG_VIDEO_FINISH, jsonObject);
            Log.e("VideoCall2Activity", "toInviteCall:  -----> TO FINISH OK");
            finish();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearData() {
        mSocket.disconnect();
        mSocket.close();
        localPeer.close();
        localPeer = null;
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearData();
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    private void setupSocket() {
        mSocket = null;
        JSONObject jsonObject = new JSONObject();
        try {
            mSocket = IO.socket(Constant.URL_SOCKET);
            mSocket.connect();
            jsonObject.put("access_token", pref.token().get());
            mSocket.emit(Constant.TAG_LOGIN_SOCKET, jsonObject);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (JSONException ignored) {
            Toast.makeText(this, "Socket error!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call_2);
        pref = new PrefHelper_(this);
        checkPermission();
    }

    private void receiverInviteCall() {
        mSocket.on(Constant.TAG_VIDEO_INVITE, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            Log.e("VideoCallActivity", "receiverInviteCall:  -----> ");
            String doctor_id;
            String doctor_name;
            if (jsonObject != null) {
                Log.e("VideoCall2Activity", "receiverInviteCall:  -----> NHAN INVITE OK");
                try {
                    doctor_id = jsonObject.getString("fromUserId");
                    doctor_name = jsonObject.getString("fromUserName");
                    Log.e("VideoDemoActivity", "receiverInviteCall:  -----> doctor name: " + doctor_name);
                    toAcceptCall();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }));
    }

    private void toAcceptCall() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constant.TAG_TO_USER_ID, Integer.parseInt(mPatientID));
            mSocket.emit(Constant.TAG_VIDEO_ACCEPT, jsonObject);
            Log.e("VideoCall2Activity", "toInviteCall:  -----> TO ACCEPT OK");
//            createPeerConnection();
//            receiverOffer();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getDataFromServer() {
        PeerConnection.IceServer peerIceServer1 = PeerConnection.IceServer
                .builder("turn:103.221.222.146:3478?transport=udp")
                .setUsername("vietskin")
                .setPassword("tombeo99")
                .createIceServer();
        PeerConnection.IceServer peerIceServer2 = PeerConnection.IceServer
                .builder("turn:103.221.222.146:3478?transport=tcp")
                .setUsername("vietskin")
                .setPassword("tombeo99")
                .createIceServer();
        peerIceServers.add(peerIceServer1);
        peerIceServers.add(peerIceServer2);
    }

    private static void showRequestPermission(Activity activity, int requestCode) {
        String[] permissions;

        permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                permission.RECORD_AUDIO,
                permission.MODIFY_AUDIO_SETTINGS
        };

        PermissionsUtil.requestPermissions(activity, requestCode, permissions);
    }


    private boolean checkStorePermission(Context context, int permission) {
        if (permission == CAMERA_PERMISSION_ID) {
            String[] permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS
            };
            return PermissionsUtil.checkPermissions(context, permissions);
        } else {
            return true;
        }
    }

    @Override
    public void onRemoteHangUp(final String msg) {

    }

    @Override
    public void onOfferReceived(final JSONObject data) {
        Log.e("MainActivity", "onOfferReceived:  -----> Received Offer");
        runOnUiThread(() -> {
            onTryToStart();

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()),
                        data.getString("sdp")));
                doAnswer();
                updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription, mSocket, Integer.parseInt(mPatientID));
            }
        }, new MediaConstraints());
    }

    private void updateVideoViews(final boolean remoteVisible) {
        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(100);
                params.width = dpToPx(100);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });

    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    @Override
    public void onAnswerReceived(final JSONObject data) {
        Log.e("MainActivity", "onAnswerReceived:  -----> Received Answer");
        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()),
                    data.getString("sdp")));
            updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            localPeer.addIceCandidate(
                    new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        localPeer.addIceCandidate(iceCandidate);
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate, mSocket, Integer.parseInt(mPatientID));
    }

    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            createPeerConnection();
//            SignallingClient.getInstance().isStarted = true;
//            doCall();
        });
    }

    private void doCall() {
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");
                SignallingClient.getInstance().emitMessageOffer(sessionDescription, mSocket, Integer.parseInt(mPatientID));
            }
        }, sdpConstraints);
    }

    @Override
    public void onCreatedRoom() {

    }

    @Override
    public void onJoinedRoom() {

    }

    @Override
    public void onNewPeerJoined() {

    }

    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory
                .createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        super.onIceCandidate(iceCandidate);
                        Log.e("MainActivity", "onIceCandidate:  -----> ");
                        onIceCandidateReceived(iceCandidate);
                    }

                    @Override
                    public void onAddStream(MediaStream mediaStream) {
                        Log.e("MainActivity", "onAddStream:  -----> Received Remote stream");
                        super.onAddStream(mediaStream);
                        gotRemoteStream(mediaStream);
                    }
                });

        addStreamToLocalPeer();
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                VideoRenderer remoteVideo = new VideoRenderer(remoteVideoView);
                remoteVideoView.setVisibility(View.VISIBLE);
                videoTrack.addRenderer(remoteVideo);
                videoTrack.addSink(remoteVideoView);
            } catch (Exception e) {
                Log.e("MainActivity", "gotRemoteStream:  -----> " + e.getMessage());
                e.printStackTrace();
            }
        });

    }

    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("ARDAMS");
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        stream.addTrack(peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource));
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.e("MainActivity", "createCameraCapturer:  -----> Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.e("MainActivity", "createCameraCapturer:  -----> Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.e("MainActivity", "createCameraCapturer:  -----> Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.e("MainActivity", "createCameraCapturer:  -----> Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_ID) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                implementData();
            }
        }
    }
}

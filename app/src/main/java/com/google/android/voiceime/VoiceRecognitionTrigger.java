/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.voiceime;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.inputmethod.InputMethodSubtype;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.net.URISyntaxException;
import android.util.Log;
import android.media.MediaRecorder;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Triggers a voice recognition by using {@link ImeTrigger} or
 * {@link IntentApiTrigger}.
 */
public class VoiceRecognitionTrigger {

    private final InputMethodService mInputMethodService;
    Activity context;
    private BroadcastReceiver mReceiver;

    private Trigger mTrigger;

    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    public WebSocketClient mWebSocketClient=null;

    private ImeTrigger mImeTrigger;
    private IntentApiTrigger mIntentApiTrigger;

    public VoiceRecognitionTrigger(InputMethodService inputMethodService) {
        mInputMethodService = inputMethodService;
        mTrigger = getTrigger();
        mIntentApiTrigger = new IntentApiTrigger(mInputMethodService);
    }

    private Trigger getTrigger() {
        if (ImeTrigger.isInstalled(mInputMethodService)) {
            return getImeTrigger();
        } else if (IntentApiTrigger.isInstalled(mInputMethodService)) {
            return getIntentTrigger();
        } else {
            return null;
        }
    }

    private Trigger getIntentTrigger() {
        if (mIntentApiTrigger == null) {
            mIntentApiTrigger = new IntentApiTrigger(mInputMethodService);
        }
        return mIntentApiTrigger;
    }

    private Trigger getImeTrigger() {
        if (mImeTrigger == null) {
            mImeTrigger = new ImeTrigger(mInputMethodService);
        }
        return mImeTrigger;
    }

    public boolean isInstalled() {
        return mTrigger != null;
    }

    public boolean isEnabled() {
        return isNetworkAvailable();
    }

    /**
     * Starts a voice recognition. The language of the recognition will match
     * the voice search language settings, or the locale of the calling IME.
     */
    public void startVoiceRecognition() {
        startVoiceRecognition(null);
    }

    /*
   *websocket connected
   */
    boolean mStartRecording = true;

    public void connectWebSocket(){
        if(!isNetworkAvailable())
        {
            Toast.makeText(context.getApplicationContext(), "This is a plain toast.", Toast.LENGTH_SHORT).show();

            return;
        }
        if(mWebSocketClient!=null)
        {
            mWebSocketClient.send("EOS");
            mWebSocketClient=null;
            onRecord(mStartRecording);
        }
        else
        {
            createWebSocket();
        }
    }

    String errorNotifyer;
    String comingData;
    public void createWebSocket() {
        URI uri;
        try {
            //uri = new URI("ws://104.236.244.251:7682/telephony");
            //uri = new URI("ws://mrcp.govivace.com:7682/telephony");
            //uri = new URI("ws://echo.websocket.org");
            uri = new URI("ws://services.govivace.com:49154/telephony");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        //Map<String, String> headers = new HashMap<>();
        mWebSocketClient = new WebSocketClient(uri,new Draft_17()) {

            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
                onRecord(mStartRecording);
                mStartRecording = !mStartRecording;
                //Log.i("Websocket","call onopen after onrecord method");
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                Log.i("websocket","recieved" + message);

                if (message != null) {
                    try {
                        JSONObject jsonObj = new JSONObject(message);
                        int status = jsonObj.optInt("status");
                        if (status == 1) {
                            Toast.makeText(context.getApplicationContext(), "Speech contains a large portion of silence or non-speech", Toast.LENGTH_SHORT).show();
                        } else if (status == 9) {
                            errorNotifyer = jsonObj.optString("message");
                            Toast.makeText(context.getApplicationContext(), errorNotifyer, Toast.LENGTH_SHORT).show();
                        } else if (status == 5) {
                            errorNotifyer = jsonObj.optString("message");
                            Toast.makeText(context.getApplicationContext(), errorNotifyer, Toast.LENGTH_SHORT).show();
                        }
                        // Getting JSON Array node
                        if (status == 0) {
                            JSONObject hypotheses = jsonObj.getJSONObject("result");
                            boolean final1 = hypotheses.getBoolean("final");
                            JSONArray jsonArray = hypotheses.optJSONArray("hypotheses");
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonObject = jsonArray.getJSONObject(i); // getting JSON Object at I'th index
                                String name = jsonObject.optString("transcript");
                                Log.i("Json","transcript" + name);
                                comingData = name;

                                if ( final1)
                                {
                                    appenddata();
                                }

                            }

                        }

                    } catch (JSONException  ex) {
                        Log.i("JsonException","exception"+ ex);
                    }


                } else {
                    Log.i("Json", "Couldn't get json from server.");

                    Toast.makeText(context.getApplicationContext(), "Couldn't get json from server. Check LogCat for possible errors!", Toast.LENGTH_SHORT).show();

                }

            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
                mWebSocketClient=null;
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }

    private void appenddata()
    {
        mIntentApiTrigger.SendDatafromwebsocket(comingData);
        mIntentApiTrigger.onStartInputView();
        Log.i("Result","Result="+comingData);
    }

    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    final byte data[] = new byte[100000];
    private void startRecording() {
        Log.i("Websocket","startrecording function call");
        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if(bufferSize>0) {
            int readbyte = 0;
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING,data.length);

            int i = recorder.getState();
            Log.i("Websocket", "value of recorder getstate" + i);
            if (i == 1)
                recorder.startRecording();

            isRecording = true;
            recordingThread = new Thread(new Runnable() {

                public void run() {

                    SendDataToServer();

                }
            }, "AudioRecorder Thread");
            recordingThread.start();
        }
    }



    private void SendDataToServer()
    {
        while (isRecording) {
            Log.i("Websocket", "call while loop");
            recorder.read(data, 0, data.length);
            //byte sendbuffer[]=new byte[readbyte];
            //sendbuffer = Arrays.copyOf(data,readbyte);
            if (mWebSocketClient != null) {
                Log.i("Websocket","call websocket send ");
                mWebSocketClient.send(data);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie)
                {
                    Log.i("Websocket","threadsleep exception"+ie);
                }
            }


        }
    }

    private void stopRecording() {
        Log.i("Websocket","call stoprecording function");
        if(null != recorder) {
            isRecording = false;

            int i = recorder.getState();
            if (i == 1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
            mStartRecording=!mStartRecording;
        }
    }

    /**
     * Starts a voice recognition
     *
     * @param language The language in which the recognition should be done. If
     *                 the recognition is done through the Google voice typing, the
     *                 parameter is ignored and the recognition is done using the
     *                 locale of the calling IME.
     * @see InputMethodSubtype
     */
    public void startVoiceRecognition(String language) {
        if (mTrigger != null) {
            mTrigger.startVoiceRecognition(language);
        }
    }

    public void onStartInputView() {
        if (mTrigger != null) {
            mTrigger.onStartInputView();
        }

        // The trigger is refreshed as the system may have changed in the meanwhile.
        mTrigger = getTrigger();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) mInputMethodService
                    .getSystemService(
                            Context.CONNECTIVITY_SERVICE);
            final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException e) {
            // The IME does not have the permission to check the networking
            // status. We hope for the best.
            return true;
        }
    }

    /**
     * Register a listener to receive a notification every time the status of
     * Voice IME may have changed. The {@link Listener} should
     * update the UI to reflect the current status of Voice IME. When
     * {@link Listener} is registered,
     * {@link #unregister(Context)} must be called when the IME is dismissed
     * {@link InputMethodService#onDestroy()}.
     */
    public void register(final Listener listener) {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    listener.onVoiceImeEnabledStatusChange();
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mInputMethodService.registerReceiver(mReceiver, filter);
    }

    /**
     * Unregister the {@link Listener}.
     */
    public void unregister(Context context) {
        if (mReceiver != null) {
            mInputMethodService.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    public interface Listener {

        /**
         * The enable status of Voice IME may have changed.
         */
        void onVoiceImeEnabledStatusChange();
    }
}

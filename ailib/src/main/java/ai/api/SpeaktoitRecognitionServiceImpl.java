package ai.api;

/***********************************************************************************************************************
 *
 * API.AI Android SDK - client-side libraries for API.AI
 * =================================================
 *
 * Copyright (C) 2014 by Speaktoit, Inc. (https://www.speaktoit.com)
 * https://www.api.ai
 *
 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 ***********************************************************************************************************************/

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

//ADD20141001
import android.media.AudioTrack;
import android.media.AudioManager;
//ADD20141001

import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.QuestionMetadata;

import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

public class SpeaktoitRecognitionServiceImpl extends AIService {

    public static final String TAG = SpeaktoitRecognitionServiceImpl.class.getName();
    private static final int SAMPLE_RATE_IN_HZ = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final AudioRecord mediaRecorder;

    private volatile boolean isRecording = false;

    private ByteArrayOutputStream outputStream;

    //ADD20141001
    private final AudioTrack audioTrack;
    protected SpeaktoitRecognitionServiceImpl(final Context context, final AIConfiguration config) {
        super(config);

                mediaRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                                                   SAMPLE_RATE_IN_HZ,
                                                   CHANNEL_CONFIG,
                                                   AUDIO_FORMAT,
                                                   AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT));

        //ADD20141001
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,// 音楽ストリームを設定
                                      SAMPLE_RATE_IN_HZ,// サンプルレート
                                      AudioFormat.CHANNEL_CONFIGURATION_MONO,// モノラル
                                      AUDIO_FORMAT,//16BIT
                                      AudioTrack.getMinBufferSize(SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_CONFIGURATION_MONO, AUDIO_FORMAT),// バッファ・サイズ                                      AudioTrack.MODE_STREAM);// Streamモード。データを書きながら再生する
    }

    @Override
    public void startListening() {
        outputStream      = new ByteArrayOutputStream();

        mediaRecorder.startRecording();
        isRecording = true;

        // 録音Thread
        new Thread(new Runnable() {
            //@Override
            int bufSize = mediaRecorder.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT);

            public void run() {
                int readsize;
                byte buf[] = new byte[bufSize];

                /*
                String filePath = Environment.getExternalStorageDirectory() + "/soundData.wav";
                File file = new File(filePath);
                file.getParentFile().mkdir();
                */
                try{
                    //fileoutputStream = new  FileOutputStream(file);

                    while (isRecording == true) {
                        // 録音データ読み込み
                        readsize = mediaRecorder.read(buf, 0, buf.length);

                        if (readsize > 0) {
                            outputStream.write(buf, 0, readsize);
                            //fileoutputStream.write(buf, 0, readsize);
                        }
                    }
                    //再生
                    //audioTrack.play();
                    //audioTrack.write(outputStream.toByteArray(), 0, outputStream.toByteArray().length);
                }catch (Exception e){
                }
            }
        }).start();
        onListeningStarted();
    }

    @Override
    public void stopListening() {
        if (isRecording) {
            mediaRecorder.stop();
            isRecording = false;

            onListeningFinished();
            /*
            try {
                fileoutputStream.close();
            }catch (Exception e){

            }
            */

            sendRequest(outputStream.toByteArray());
        }

    }

    @Override
    public void cancel() {
        if (isRecording) {
            mediaRecorder.stop();
            isRecording = false;

            onListeningFinished();
        }
    }

    private void sendRequest(final byte[] soundData) {
        try {
            final URL url = new URL(config.getQuestionUrl());
            final QuestionMetadata questionMetadata = new QuestionMetadata();
            questionMetadata.setLanguage(config.getLanguage());
            questionMetadata.setAgentId(config.getAgentId());
            questionMetadata.setTimezone(Calendar.getInstance().getTimeZone().getID());

            final SpeaktoitRecognitionRequest speaktoitRecognitionRequest = new SpeaktoitRecognitionRequest();
            speaktoitRecognitionRequest.setMetadata(questionMetadata);
            speaktoitRecognitionRequest.setSoundData(soundData);

            final SpeaktoitRecognitionRequestTask requestTask = new SpeaktoitRecognitionRequestTask(url){
                @Override
                protected void onPostExecute(final String stringResult) {
                    try {
                        final AIResponse aiResponse = GsonFactory.getGson().fromJson(stringResult, AIResponse.class);
                        //int ka = aiResponse.setTimestamp();
                        onResult(aiResponse);

                    } catch (final Exception e) {
                        final AIError aiError = new AIError("Wrong answer from server " + e.toString());
                        onError(aiError);
                    }
                }
            };

            requestTask.execute(speaktoitRecognitionRequest);

        }catch (final MalformedURLException e) {
            e.printStackTrace();
        }
    }

}

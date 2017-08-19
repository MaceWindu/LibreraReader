package com.foobnix.tts;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

import org.ebookdroid.LirbiApp;
import org.greenrobot.eventbus.EventBus;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.AppState;
import com.foobnix.pdf.info.wrapper.DocumentController;

import android.annotation.TargetApi;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.widget.Toast;

public class TTSEngine {


    private static final String UTTERANCE_ID = "LirbiReader";
    private static final String TAG = "TTSEngine";
    TextToSpeech ttsEngine;

    private static TTSEngine INSTANCE = new TTSEngine();

    public static TTSEngine get() {
        return INSTANCE;
    }

    HashMap<String, String> map = new HashMap<String, String>();
    {
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
    }

    public void shutdown() {
        ttsEngine.shutdown();
        ttsEngine = null;
    }

    OnInitListener listener = new OnInitListener() {

        @Override
        public void onInit(int status) {
            LOG.d(TAG, "onInit", "SUCCESS", status == TextToSpeech.SUCCESS);
            if (status == TextToSpeech.ERROR) {
                Toast.makeText(LirbiApp.context, R.string.msg_unexpected_error, Toast.LENGTH_LONG).show();
            }

        }
    };

    public TextToSpeech getTTS() {
        return getTTS(null);
    }

    public TextToSpeech getTTS(OnInitListener onLisnter) {
        if (ttsEngine != null) {
            return ttsEngine;
        }
        if (onLisnter == null) {
            onLisnter = listener;
        }

        ttsEngine = new TextToSpeech(LirbiApp.context, onLisnter);

        return ttsEngine;

    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void stop() {
        if (ttsEngine != null) {
            if (Build.VERSION.SDK_INT >= 15) {
                ttsEngine.setOnUtteranceProgressListener(null);
            } else {
                ttsEngine.setOnUtteranceCompletedListener(null);
            }
            ttsEngine.stop();
        }
        EventBus.getDefault().post(new TtsStatus());
    }

    public TextToSpeech getTTSWithEngine(String engine) {
        shutdown();
        ttsEngine = new TextToSpeech(LirbiApp.context, listener, engine);
        return ttsEngine;
    }

    private String text = "";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void speek(final String text) {
        this.text = text;
        LOG.d(TAG, "speek", text);
        if (TxtUtils.isEmpty(text)) {
            return;
        }
        ttsEngine = getTTS(new OnInitListener() {

            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    speek(text);
                }
            }
        });

        ttsEngine.setPitch(AppState.get().ttsPitch);
        if (AppState.get().ttsSpeed == 0.0f) {
            AppState.get().ttsSpeed = 0.01f;
        }
        ttsEngine.setSpeechRate(AppState.get().ttsSpeed);
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, map);
    }

    public void speakToFile(final DocumentController controller) {
        File dirFolder = new File(AppState.get().ttsSpeakPath, "TTS_" + controller.getCurrentBook().getName());
        if (!dirFolder.exists()) {
            dirFolder.mkdirs();
        }
        if (!dirFolder.exists()) {
            Toast.makeText(controller.getActivity(), R.string.file_not_found, Toast.LENGTH_LONG).show();
            return;
        }

        speakToFile(controller, 0, dirFolder.getPath());
    }

    public void speakToFile(final DocumentController controller, final int page, final String folder) {
        LOG.d("speakToFile", page, controller.getPageCount());

        if (page >= controller.getPageCount()) {
            controller.getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(controller.getActivity(), R.string.success, Toast.LENGTH_LONG).show();
                }
            });

            return;
        }

        DecimalFormat df = new DecimalFormat("000");
        String wav = new File(folder, "page-" + df.format(page + 1) + ".wav").getPath();
        String fileText = controller.getTextForPage(page);

        ttsEngine.synthesizeToFile(fileText, map, wav);

        TTSEngine.get().getTTS().setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {

            @Override
            public void onUtteranceCompleted(String utteranceId) {
                speakToFile(controller, page + 1, folder);
            }

        });

    }

    public boolean isPlaying() {
        return ttsEngine != null && ttsEngine.isSpeaking();
    }

    public void playCurrent() {
        speek(text);
    }

    public boolean hasNoEngines() {
        try {
            return ttsEngine != null && (ttsEngine.getEngines() == null || ttsEngine.getEngines().size() == 0);
        } catch (Exception e) {
            return true;
        }
    }

    public String getCurrentEngineName() {
        try {
            if (ttsEngine != null) {
                String enginePackage = ttsEngine.getDefaultEngine();
                List<EngineInfo> engines = ttsEngine.getEngines();
                for (final EngineInfo eInfo : engines) {
                    if (eInfo.name.equals(enginePackage)) {
                        return engineToString(eInfo);
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return "no engine";
    }

    public static String engineToString(EngineInfo info) {
        return info.label;
    }

}

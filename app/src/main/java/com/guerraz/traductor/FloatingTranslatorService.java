package com.guerraz.traductor;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FloatingTranslatorService extends Service {
    private WindowManager wm;
    private View overlay;
    private TextView caption, state;
    private SpeechRecognizer recognizer;
    private AudioRecord playbackRecorder;
    private MediaProjection mediaProjection;
    private ParcelFileDescriptor audioReadPipe, audioWritePipe;
    private volatile boolean audioCaptureActive = false;
    private Thread audioCaptureThread;
    private boolean running = false;
    private int windowWidth, textSize = 20;
    private WindowManager.LayoutParams params;
    private String source = "auto", target = "es";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable partialTranslation;
    private static final String CHANNEL = "translator_overlay";

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = nb.setContentTitle("Traductor flotante activo")
            .setContentText("Escuchando audio para mostrar subtítulos traducidos")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(7, n);
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Traductor flotante", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null) {
            int projectionResult=intent.getIntExtra("projection_result", Activity.RESULT_CANCELED);
            Intent projectionData=intent.getParcelableExtra("projection_data");
            if(projectionResult==Activity.RESULT_OK && projectionData!=null){ MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE); mediaProjection=manager.getMediaProjection(projectionResult,projectionData); }
            source = intent.getStringExtra("source") == null ? "auto" : intent.getStringExtra("source");
            target = intent.getStringExtra("target") == null ? "es" : intent.getStringExtra("target");
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        showOverlay();
        startRecognition();
        return START_STICKY;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(1,0x66FFFFFF); return d;
    }
    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private void showOverlay() {
        if (overlay != null) return;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20,16,20,18); card.setBackground(shape(0xEE111827,18));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("TRADUCTOR · ES",16,true); title.setPadding(2,4,10,4);
        TextView smaller = control("−"), larger = control("+"), resume = control("▶"), stop = control("DETENER"), close = control("×");
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(smaller); bar.addView(larger); bar.addView(resume); bar.addView(stop); bar.addView(close); card.addView(bar);
        state = label("Preparando captura de audio interno…",13,false); state.setTextColor(0xFFB8C5D8); card.addView(state);
        caption = label("Los subtítulos aparecerán aquí…",24,true); textSize=24;
        caption.setPadding(2,8,2,4); caption.setMaxLines(12); card.addView(caption);
        smaller.setOnClickListener(v -> {textSize=Math.max(12,textSize-2); caption.setTextSize(textSize); windowWidth=Math.max(280,windowWidth-36); params.width=windowWidth; try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){} });
        larger.setOnClickListener(v -> {textSize=Math.min(40,textSize+2); caption.setTextSize(textSize); windowWidth=Math.min(getResources().getDisplayMetrics().widthPixels,windowWidth+48); params.width=windowWidth; try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){} });
        resume.setOnClickListener(v -> { if(!running) startRecognition(); });
        stop.setOnClickListener(v -> { stopRecognition(); update("Escucha detenida · pulsa ▶ para reanudar"); });
        close.setOnClickListener(v -> stopSelf());
        title.setOnTouchListener(new View.OnTouchListener() {
            int sx,sy,ox,oy;
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){sx=(int)e.getRawX();sy=(int)e.getRawY();ox=params.x;oy=params.y;return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){params.x=ox+(int)e.getRawX()-sx;params.y=oy+(int)e.getRawY()-sy;try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){}return true;}
                return true;
            }
        });
        windowWidth=(int)(getResources().getDisplayMetrics().widthPixels*0.82f);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        params=new WindowManager.LayoutParams(windowWidth,WindowManager.LayoutParams.WRAP_CONTENT,type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; params.y=140;
        overlay=card;
        try {wm.addView(overlay,params);} catch(Exception e){overlay=null;stopSelf();}
    }
    private TextView control(String s) {
        TextView t=label(s, s.equals("DETENER") ? 15 : 30,true); t.setGravity(Gravity.CENTER); t.setBackground(shape(0xFF334155,24));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(s.equals("DETENER") ? 100 : 60,58); p.setMargins(3,0,3,0); t.setLayoutParams(p); return t;
    }
    private void stopRecognition() {
        running=false;
        stopAudioPipe();
        if(partialTranslation!=null) handler.removeCallbacks(partialTranslation);
        if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}
    }
    private void startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { update("No hay servicio de reconocimiento de voz instalado."); return; }
        if (recognizer != null) recognizer.destroy();
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b){update("Escuchando…");}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){stopAudioPipe();}
            public void onError(int e){stopAudioPipe();if(running){update("Reintentando escucha…");handler.postDelayed(()->launch(),900);}}
            public void onResults(Bundle b){stopAudioPipe();if(partialTranslation!=null)handler.removeCallbacks(partialTranslation);consume(b);if(running)handler.postDelayed(()->launch(),120);}
            public void onPartialResults(Bundle b){schedulePartial(b);}
            public void onEvent(int e,Bundle b){}
        });
        running=true; launch();
    }
    private String speechLocale() {
        String c=source.equals("auto")?Locale.getDefault().toLanguageTag():source;
        return c.equals("zh-CN")||c.equals("zh-TW")?c:c;
    }
    private void launch() {
        if(!running||recognizer==null)return;
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,speechLocale());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try{ if(Build.VERSION.SDK_INT<29 || mediaProjection==null){update("Falta permiso de captura de audio interno.");running=false;return;} if(!startAudioPipe()){update("No se pudo capturar el audio interno.");running=false;return;} if(Build.VERSION.SDK_INT>=33){i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,audioReadPipe);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000);}else{update("La entrada de audio personalizada requiere Android 13.");stopAudioPipe();running=false;return;} recognizer.startListening(i);}catch(Exception e){stopAudioPipe();update("No se pudo iniciar el reconocimiento interno.");}
    }
    private boolean startAudioPipe() {
        stopAudioPipe();
        try {
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
            int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)return false;
            playbackRecorder=new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(min,12800)).setAudioPlaybackCaptureConfig(config).build();
            if(playbackRecorder.getState()!=AudioRecord.STATE_INITIALIZED){stopAudioPipe();return false;}
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe(); audioReadPipe=pipe[0]; audioWritePipe=pipe[1];
            playbackRecorder.startRecording(); audioCaptureActive=true;
            final AudioRecord recorder=playbackRecorder; final ParcelFileDescriptor writer=audioWritePipe;
            audioCaptureThread=new Thread(()->{byte[] buffer=new byte[3200];try(FileOutputStream out=new FileOutputStream(writer.getFileDescriptor())){while(audioCaptureActive){int count=recorder.read(buffer,0,buffer.length);if(count>0)out.write(buffer,0,count);else if(count<0)break;}}catch(Exception ignored){}} ,"translator-playback-capture");
            audioCaptureThread.start(); return true;
        }catch(Exception e){stopAudioPipe();return false;}
    }
    private void stopAudioPipe(){
        audioCaptureActive=false;
        if(playbackRecorder!=null){try{if(playbackRecorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)playbackRecorder.stop();}catch(Exception ignored){}try{playbackRecorder.release();}catch(Exception ignored){}playbackRecorder=null;}
        if(audioWritePipe!=null){try{audioWritePipe.close();}catch(Exception ignored){}audioWritePipe=null;}
        if(audioReadPipe!=null){try{audioReadPipe.close();}catch(Exception ignored){}audioReadPipe=null;} audioCaptureThread=null;
    }
    private void schedulePartial(Bundle b) {
        ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(a==null||a.isEmpty())return;
        final String phrase=a.get(0).trim(); if(phrase.isEmpty())return;
        if(partialTranslation!=null)handler.removeCallbacks(partialTranslation);
        partialTranslation=()->{ if(running) translate(phrase); };
        handler.postDelayed(partialTranslation,650);
    }
    private void consume(Bundle b) {
        ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(a==null||a.isEmpty())return;
        String phrase=a.get(0).trim(); if(phrase.isEmpty())return;
        update("Traduciendo…");
        translate(phrase);
    }
    private void translate(String phrase) {
        String from=source.equals("auto")?Locale.getDefault().getLanguage():source;
        new Thread(()->{
            String result;
            try {
                String q=URLEncoder.encode(phrase,"UTF-8");
                String pair=URLEncoder.encode(from+"|"+target,"UTF-8");
                HttpURLConnection c=(HttpURLConnection)new URL("https://api.mymemory.translated.net/get?q="+q+"&langpair="+pair).openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(12000);
                InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                result=json.getJSONObject("responseData").getString("translatedText");c.disconnect();
            }catch(Exception e){result="No se pudo traducir. Comprueba Internet.";}
            final String translated=result;
            handler.post(()->{if(caption!=null)caption.setText(translated);update("Subtítulos traducidos · "+target.toUpperCase(Locale.ROOT));});
        }).start();
    }
    private void update(String s){handler.post(()->{if(state!=null)state.setText(s);});}
    @Override public void onDestroy() {
        running=false;stopAudioPipe();handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}
        if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}
        if(mediaProjection!=null){try{mediaProjection.stop();}catch(Exception ignored){}mediaProjection=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

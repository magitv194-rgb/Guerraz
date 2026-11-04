package com.guerraz.traductor;

import android.app.*;
import android.content.*;
import android.graphics.Color;
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
    private boolean running = false;
    private int windowWidth, textSize = 20;
    private WindowManager.LayoutParams params;
    private String source = "auto", target = "es";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final String CHANNEL = "translator_overlay";

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = nb.setContentTitle("Traductor flotante activo")
            .setContentText("Escuchando audio para mostrar subtítulos traducidos")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
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
        card.setPadding(14,10,14,12); card.setBackground(shape(0xEE111827,18));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("TRADUCTOR · ES",12,true); title.setPadding(2,4,10,4);
        TextView smaller = control("−"), larger = control("+"), close = control("×");
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(smaller); bar.addView(larger); bar.addView(close); card.addView(bar);
        state = label("Conectando al micrófono…",11,false); state.setTextColor(0xFFB8C5D8); card.addView(state);
        caption = label("Los subtítulos aparecerán aquí…",textSize,true);
        caption.setPadding(2,8,2,4); caption.setMaxLines(8); card.addView(caption);
        smaller.setOnClickListener(v -> {textSize=Math.max(12,textSize-2); caption.setTextSize(textSize); windowWidth=Math.max(220,windowWidth-36); params.width=windowWidth; try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){} });
        larger.setOnClickListener(v -> {textSize=Math.min(40,textSize+2); caption.setTextSize(textSize); windowWidth=Math.min(getResources().getDisplayMetrics().widthPixels,windowWidth+36); params.width=windowWidth; try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){} });
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
        TextView t=label(s,20,true); t.setGravity(Gravity.CENTER); t.setBackground(shape(0xFF334155,24));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(38,36); p.setMargins(3,0,3,0); t.setLayoutParams(p); return t;
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
            public void onEndOfSpeech(){update("Procesando…");}
            public void onError(int e){if(running){update("Reintentando escucha…");handler.postDelayed(()->launch(),900);}}
            public void onResults(Bundle b){consume(b);if(running)handler.postDelayed(()->launch(),350);}
            public void onPartialResults(Bundle b){consume(b);}
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
        try{recognizer.startListening(i);}catch(Exception e){update("No se pudo iniciar el reconocimiento.");}
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
        running=false;handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}
        if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

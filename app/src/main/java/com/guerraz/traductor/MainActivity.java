package com.guerraz.traductor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 41;
    private final String[] labels = {"Automático (idioma del teléfono)","Árabe","Bengalí","Búlgaro","Catalán","Chino (simplificado)","Chino (tradicional)","Coreano","Croata","Danés","Eslovaco","Esloveno","Español","Estonio","Finés","Francés","Griego","Hebreo","Hindi","Húngaro","Indonesio","Inglés","Italiano","Japonés","Malayo","Neerlandés","Noruego","Persa","Polaco","Portugués","Rumano","Ruso","Serbio","Sueco","Tailandés","Turco","Ucraniano","Vietnamita"};
    private final String[] codes = {"auto","ar","bn","bg","ca","zh-CN","zh-TW","ko","hr","da","sk","sl","es","et","fi","fr","el","he","hi","hu","id","en","it","ja","ms","nl","no","fa","pl","pt","ro","ru","sr","sv","th","tr","uk","vi"};
    private Spinner source, target;
    private TextView heard, translated, status;
    private Button start, stop;
    private SpeechRecognizer recognizer;
    private boolean listening = false;
    private String lastFinal = "";
    private final android.os.Handler handler = new android.os.Handler();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12,18,32));
        getWindow().setNavigationBarColor(Color.rgb(12,18,32));
        buildUi();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) status.setText("Este dispositivo no ofrece reconocimiento de voz. Instala o habilita el servicio de reconocimiento de Google.");
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(12,18,32));
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(22,28,22,24);
        scroll.addView(root); setContentView(scroll);
        TextView title = text("Traductor de voz",28,Color.WHITE,true); root.addView(title);
        TextView sub = text("Habla y obtén la traducción al instante",14,0xFFB7C2D5,false); sub.setPadding(0,6,0,22); root.addView(sub);
        root.addView(text("IDIOMA DE ENTRADA",12,0xFF9BAAC2,true));
        source = new Spinner(this); source.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels)); root.addView(source);
        root.addView(text("IDIOMA DE SALIDA",12,0xFF9BAAC2,true));
        String[] targetLabels = Arrays.copyOfRange(labels,1,labels.length);
        target = new Spinner(this); target.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,targetLabels)); target.setSelection(11); root.addView(target);
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(0); buttons.setPadding(0,18,0,16);
        start = new Button(this); start.setText("🎙 INICIAR"); start.setAllCaps(false);
        stop = new Button(this); stop.setText("DETENER"); stop.setAllCaps(false); stop.setEnabled(false);
        buttons.addView(start,new LinearLayout.LayoutParams(0,WRAP_CONTENT,1)); buttons.addView(stop,new LinearLayout.LayoutParams(0,WRAP_CONTENT,1)); root.addView(buttons);
        status = text("Listo. Se necesita Internet y permiso de micrófono.",13,0xFFB7C2D5,false); status.setPadding(0,0,0,14); root.addView(status);
        root.addView(text("TEXTO RECONOCIDO",13,0xFF9BAAC2,true));
        heard = panel(root);
        root.addView(text("TRADUCCIÓN",13,0xFF9BAAC2,true));
        translated = panel(root);
        Button clear = new Button(this); clear.setText("Limpiar textos"); clear.setAllCaps(false); root.addView(clear);
        TextView note = text("La opción Automático usa el idioma configurado en el teléfono como idioma de reconocimiento; no identifica por sí sola cualquier idioma hablado. El reconocimiento depende del servicio de voz instalado. MyMemory requiere Internet y tiene límites de uso gratuitos.",12,0xFF9BAAC2,false);
        note.setPadding(0,18,0,0); root.addView(note);
        start.setOnClickListener(v -> requestStart()); stop.setOnClickListener(v -> stopListening());
        clear.setOnClickListener(v -> { heard.setText(""); translated.setText(""); lastFinal=""; });
        source.setOnItemSelectedListener(new SimpleSelect()); target.setOnItemSelectedListener(new SimpleSelect());
    }
    private static final int WRAP_CONTENT = -2;
    private TextView panel(LinearLayout root) {
        TextView t = text("Aquí aparecerá el texto…",17,Color.WHITE,false); t.setPadding(15,16,15,16);
        t.setMinHeight(110); t.setGravity(Gravity.TOP); t.setBackground(bg(0xFF1D293D,18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,WRAP_CONTENT); p.setMargins(0,8,0,18); root.addView(t,p); return t;
    }
    private class SimpleSelect implements AdapterView.OnItemSelectedListener {
        public void onNothingSelected(AdapterView<?> p) {}
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if(listening) { stopListening(); status.setText("Idioma cambiado. Pulsa Iniciar para continuar."); } }
    }
    private void requestStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO); return;
        }
        beginListening();
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g); if(r==REQ_AUDIO && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) beginListening();
        else status.setText("Se necesita permiso de micrófono para traducir.");
    }
    private String sourceCode() { int i=source.getSelectedItemPosition(); return codes[Math.max(0,Math.min(i,codes.length-1))]; }
    private String speechLocale() {
        String c=sourceCode();
        if(c.equals("auto")) c=Locale.getDefault().toLanguageTag();
        if(c.equals("zh-CN")) return "zh-CN"; if(c.equals("zh-TW")) return "zh-TW";
        return c;
    }
    private void beginListening() {
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { status.setText("No hay servicio de reconocimiento de voz disponible."); return; }
        if(recognizer!=null) recognizer.destroy();
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b){status.setText("Escuchando… habla ahora.");}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){ if(listening) status.setText("Procesando voz…");}
            public void onError(int e) {
                if(!listening)return;
                status.setText("No se pudo reconocer el audio (código "+e+"). Reintentando…");
                handler.postDelayed(() -> {if(listening) launchRecognizer();},700);
            }
            public void onResults(Bundle b){handleResults(b,false); if(listening) handler.postDelayed(() -> {if(listening) launchRecognizer();},350);}
            public void onPartialResults(Bundle b){handleResults(b,true);}
            public void onEvent(int t, Bundle b){}
        });
        listening=true; start.setEnabled(false); stop.setEnabled(true); launchRecognizer();
    }
    private void launchRecognizer() {
        if(!listening || recognizer==null)return;
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,speechLocale());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
        try { recognizer.startListening(i); } catch(Exception e){status.setText("No se pudo iniciar el micrófono: "+e.getMessage());}
    }
    private void handleResults(Bundle b, boolean partial) {
        ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(a==null||a.isEmpty())return;
        String s=a.get(0).trim(); if(s.isEmpty())return;
        heard.setText(s);
        if(!partial && !s.equals(lastFinal)) {lastFinal=s; translate(s);}
    }
    private void translate(String s) {
        String selectedFrom=sourceCode(); final String from=selectedFrom.equals("auto") ? Locale.getDefault().getLanguage() : selectedFrom;
        String to=codes[target.getSelectedItemPosition()+1];
        status.setText("Traduciendo…");
        new Thread(() -> {
            String result;
            try {
                String q=URLEncoder.encode(s,"UTF-8");
                String pair=URLEncoder.encode(from+"|"+to,"UTF-8");
                URL url=new URL("https://api.mymemory.translated.net/get?q="+q+"&langpair="+pair);
                HttpURLConnection c=(HttpURLConnection)url.openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000);
                c.setRequestMethod("GET");
                InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf=new byte[2048]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n);
                JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                result=json.getJSONObject("responseData").getString("translatedText"); c.disconnect();
            } catch(Exception e){result="Error de traducción. Comprueba tu conexión o intenta con una frase más corta.";}
            final String translatedText=result;
            runOnUiThread(() -> {translated.setText(translatedText); status.setText("Traducción actualizada.");});
        }).start();
    }
    private void stopListening() {
        listening=false; if(recognizer!=null){recognizer.stopListening(); recognizer.destroy(); recognizer=null;}
        start.setEnabled(true); stop.setEnabled(false); status.setText("Micrófono detenido.");
    }
    @Override protected void onDestroy(){stopListening(); super.onDestroy();}
}

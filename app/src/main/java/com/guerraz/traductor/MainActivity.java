package com.guerraz.traductor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.media.projection.MediaProjectionManager;
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
    private static final int REQ_CAPTURE = 43;
    private final String[] labels = {"Automático (idioma del teléfono)","Árabe","Bengalí","Búlgaro","Catalán","Chino (simplificado)","Chino (tradicional)","Coreano","Croata","Danés","Alemán","Eslovaco","Esloveno","Español","Estonio","Finés","Francés","Griego","Hebreo","Hindi","Húngaro","Indonesio","Inglés","Italiano","Japonés","Malayo","Neerlandés","Noruego","Persa","Polaco","Portugués","Rumano","Ruso","Serbio","Sueco","Tailandés","Turco","Ucraniano","Vietnamita"};
    private final String[] codes = {"auto","ar","bn","bg","ca","zh-CN","zh-TW","ko","hr","da","de","sk","sl","es","et","fi","fr","el","he","hi","hu","id","en","it","ja","ms","nl","no","fa","pl","pt","ro","ru","sr","sv","th","tr","uk","vi"};
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
        source = new Spinner(this); source.setAdapter(new WhiteSpinnerAdapter(labels)); root.addView(source);
        root.addView(text("IDIOMA DE SALIDA",12,0xFF9BAAC2,true));
        String[] targetLabels = Arrays.copyOfRange(labels,1,labels.length);
        target = new Spinner(this); target.setAdapter(new WhiteSpinnerAdapter(targetLabels)); target.setSelection(12); root.addView(target);
        Button settings = new Button(this); settings.setText("⚙ CONFIGURACIÓN"); settings.setAllCaps(false); root.addView(settings,new LinearLayout.LayoutParams(-1,WRAP_CONTENT));
        settings.setOnClickListener(v -> showSettings());
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(0); buttons.setPadding(0,18,0,16);
        start = new Button(this); start.setText("🎙 INICIAR"); start.setAllCaps(false);
        stop = new Button(this); stop.setText("DETENER"); stop.setAllCaps(false); stop.setEnabled(false);
        buttons.addView(start,new LinearLayout.LayoutParams(0,WRAP_CONTENT,1)); buttons.addView(stop,new LinearLayout.LayoutParams(0,WRAP_CONTENT,1)); root.addView(buttons);
        status = text("Listo. Se necesita Internet y permiso de captura de audio interno.",13,0xFFB7C2D5,false); status.setPadding(0,0,0,14); root.addView(status);
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
    private void showSettings(){
        new AlertDialog.Builder(this).setTitle("Configuración")
            .setMessage("Fuente de audio: SOLO AUDIO INTERNO. La APK intentará capturar el sonido multimedia reproducido por otra aplicación. No activa la grabación del micrófono. Android exige permiso de captura de audio y confirmación del sistema cada vez que se inicia. Selecciona los idiomas en la pantalla principal.")
            .setPositiveButton("Aceptar",null).show();
    }
    private class WhiteSpinnerAdapter extends ArrayAdapter<String>{
        WhiteSpinnerAdapter(String[] values){super(MainActivity.this,android.R.layout.simple_spinner_item,values);setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);}
        @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);v.setTextColor(Color.WHITE);v.setTextSize(16);return v;}
        @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(Color.WHITE);v.setBackgroundColor(0xFF1D293D);v.setPadding(18,16,18,16);return v;}
    }
    private class SimpleSelect implements AdapterView.OnItemSelectedListener {
        public void onNothingSelected(AdapterView<?> p) {}
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if(listening) { stopListening(); status.setText("Idioma cambiado. Pulsa Iniciar para continuar."); } }
    }
    private static final int REQ_OVERLAY = 42;
    @Override protected void onResume() { super.onResume(); }
    private void requestStart() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Intent overlay = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName()));
            startActivityForResult(overlay, REQ_OVERLAY);
            status.setText("Autoriza mostrar sobre otras aplicaciones y vuelve aquí.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return; }
        requestInternalCapture();
    }
    private void requestInternalCapture(){
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(),REQ_CAPTURE);
    }
    private void startOverlayTranslator(int resultCode, Intent projectionData) {
        Intent service = new Intent(this, FloatingTranslatorService.class);
        service.putExtra("projection_result",resultCode); service.putExtra("projection_data",projectionData);
        service.putExtra("source", sourceCode());
        service.putExtra("target", codes[target.getSelectedItemPosition()+1]);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText("Traductor flotante iniciado. Puedes abrir TikTok.");
        start.setEnabled(false); stop.setEnabled(true);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==REQ_CAPTURE){ if(resultCode==RESULT_OK && data!=null) startOverlayTranslator(resultCode,data); else status.setText("No se autorizó la captura de audio interno."); return; }
        if (requestCode == REQ_OVERLAY && android.provider.Settings.canDrawOverlays(this)) status.setText("Permiso de ventana flotante concedido. Pulsa Iniciar.");
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_AUDIO && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) {
            if (android.provider.Settings.canDrawOverlays(this)) requestInternalCapture();
        } else if(r==REQ_AUDIO) status.setText("Android exige permiso de captura de audio para leer la reproducción interna; la APK no usará el micrófono.");
    }
    private String sourceCode() { int i=source.getSelectedItemPosition(); return codes[Math.max(0,Math.min(i,codes.length-1))]; }
    private void stopListening() {
        stopService(new Intent(this, FloatingTranslatorService.class));
        start.setEnabled(true); stop.setEnabled(false); status.setText("Traductor flotante detenido.");
    }
    @Override protected void onDestroy(){ if(recognizer!=null){recognizer.destroy(); recognizer=null;} super.onDestroy(); }
}

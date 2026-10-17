package br.com.poderazul.spikesbot;

import android.app.Activity;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST = 904;
    private static final int BG = 0xFF080F21;
    private static final int CARD = 0xFF12223B;
    private static final int CYAN = 0xFF56E3EC;
    private static final int LIGHT = 0xFFEAF4FF;
    private static final int MUTED = 0xFFB3C4D8;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView statusText, detailText;
    private Button startBtn;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Config.get(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(22), dp(32), dp(22), dp(26));
        scroll.addView(screen);
        setContentView(scroll);

        TextView kicker = label("PODER AZUL LAB  /  EXPERIMENTO 01", 11, CYAN, true);
        screen.addView(kicker);
        TextView title = label("SPIKES\nBOT", 43, LIGHT, true);
        title.setLineSpacing(-dp(3), 1f);
        addMargin(screen, title, 5, 0, 0, 2);
        TextView subtitle = label("Piloto automático experimental para Don't Touch The Spikes — modo clássico.", 14, MUTED, false);
        addMargin(screen, subtitle, 0, 1, 0, 18);

        LinearLayout mode = card(screen);
        mode.addView(label("OBJETIVO DO BOT", 12, CYAN, true));
        CheckBox candies = new CheckBox(this);
        candies.setText("🍬 Procurar e coletar doces");
        candies.setTextColor(LIGHT);
        candies.setTextSize(16);
        candies.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        candies.setChecked(prefs.getBoolean(Config.CANDY, false));
        candies.setPadding(0, dp(8), 0, 0);
        mode.addView(candies);
        candies.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(Config.CANDY, checked).apply());
        mode.addView(label("Desligado: sobreviver sem perseguir doces. Ligado: tentar coletá-los somente em corredores livres.", 12, MUTED, false));

        LinearLayout look = card(screen);
        look.addView(label("COR ATUAL DO PÁSSARO", 12, CYAN, true));
        Spinner colorSpinner = new Spinner(this);
        String[] colors = {"Rosa (clássico)", "Azul", "Roxo", "Verde", "Preto / escuro", "Vermelho"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, colors);
        colorSpinner.setAdapter(adapter);
        colorSpinner.setSelection(Math.max(0, Math.min(5, prefs.getInt(Config.COLOR, 0))));
        look.addView(colorSpinner);
        colorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(Config.COLOR, position).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        look.addView(label("Nas capturas enviadas, o pássaro é VERDE. Selecione Verde para o modo VISÃO. O modo contínuo não depende da cor.", 12, MUTED, false));

        LinearLayout control = card(screen);
        control.addView(label("TIPO DE CONTROLE", 12, CYAN, true));
        Spinner controlMode = new Spinner(this);
        String[] modeNames = {"CONTÍNUO — toques repetidos (teste)", "VISÃO AO VIVO — recomendada", "VISÃO ANTIGA — capturas lentas"};
        ArrayAdapter<String> controlAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, modeNames);
        controlMode.setAdapter(controlAdapter);
        controlMode.setSelection(Math.max(0, Math.min(2, prefs.getInt(Config.MODE, 1))));
        control.addView(controlMode);
        controlMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(Config.MODE, position).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        control.addView(label("VISÃO AO VIVO v1.1: prevê a altura do pássaro quando chegar na parede seguinte, procura uma passagem sem espinhos e controla a subida e queda. Não usa número fixo de toques. Teste primeiro sem doces.", 12, MUTED, false));
        int intervalInitial = Math.max(250, Math.min(850, prefs.getInt(Config.INTERVAL, 420)));
        TextView intervalText = label("Intervalo entre toques: " + intervalInitial + " ms", 14, LIGHT, false);
        addMargin(control, intervalText, 9, 0, 0, 0);
        SeekBar intervalSeek = new SeekBar(this);
        intervalSeek.setMax(120);
        intervalSeek.setProgress((intervalInitial - 250) / 5);
        control.addView(intervalSeek);
        intervalSeek.setOnSeekBarChangeListener(listener(value -> {
            int milliseconds = 250 + value * 5;
            prefs.edit().putInt(Config.INTERVAL, milliseconds).apply();
            intervalText.setText("Intervalo entre toques: " + milliseconds + " ms");
        }));
        control.addView(label("Começa após 4 segundos para dar tempo de abrir e iniciar a partida. Recomendo testar primeiro com 420 ms.", 12, MUTED, false));

        LinearLayout tuning = card(screen);
        tuning.addView(label("AJUSTES", 12, CYAN, true));
        TextView safetyText = label("Margem de segurança: " + prefs.getInt(Config.SAFETY, 65) + "%", 14, LIGHT, false);
        addMargin(tuning, safetyText, 10, 0, 0, 0);
        SeekBar safety = new SeekBar(this);
        safety.setMax(100);
        safety.setProgress(prefs.getInt(Config.SAFETY, 65));
        tuning.addView(safety);
        safety.setOnSeekBarChangeListener(listener(value -> {
            prefs.edit().putInt(Config.SAFETY, value).apply();
            safetyText.setText("Margem de segurança: " + value + "%");
        }));
        TextView rateText = label("Frequência de análise: " + prefs.getInt(Config.RATE, 65) + "%", 14, LIGHT, false);
        addMargin(tuning, rateText, 8, 0, 0, 0);
        SeekBar rate = new SeekBar(this);
        rate.setMax(100);
        rate.setProgress(prefs.getInt(Config.RATE, 65));
        tuning.addView(rate);
        rate.setOnSeekBarChangeListener(listener(value -> {
            prefs.edit().putInt(Config.RATE, value).apply();
            rateText.setText("Frequência de análise: " + value + "%");
        }));
        tuning.addView(label("No modo VISÃO AO VIVO, a frequência define o intervalo entre decisões; a captura da tela é contínua. O app não grava nem envia vídeos.", 12, MUTED, false));

        LinearLayout status = card(screen);
        status.addView(label("STATUS / DIAGNÓSTICO", 12, CYAN, true));
        statusText = label("Aguardando", 15, LIGHT, true);
        addMargin(status, statusText, 8, 0, 0, 0);
        detailText = label("Nenhuma partida iniciada", 12, MUTED, false);
        addMargin(status, detailText, 6, 0, 0, 0);
        status.addView(label("Depois do teste, confira o número de campos perdidos, falhas de captura e o intervalo atual. O modo VISÃO ainda é experimental e não garante sobrevivência.", 12, MUTED, false));

        startBtn = actionButton("▶ INICIAR BOT AUTOMÁTICO", CYAN, BG);
        addMargin(screen, startBtn, 12, 8, 0, 0);
        startBtn.setOnClickListener(v -> start());
        Button oneTap = actionButton("👆 TESTAR 1 TOQUE NO JOGO", 0xFF203A56, LIGHT);
        addMargin(screen, oneTap, 10, 0, 0, 0);
        oneTap.setOnClickListener(v -> testTap());
        TextView testHelp = label("ATENÇÃO: o botão TESTAR envia exatamente UM toque. Para tocar repetidamente, selecione CONTÍNUO e pressione INICIAR BOT AUTOMÁTICO.", 12, MUTED, false);
        addMargin(screen, testHelp, 5, 0, 0, 0);
        Button stop = actionButton("■ PARAR O BOT", CARD, LIGHT);
        addMargin(screen, stop, 10, 0, 0, 0);
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean(Config.RUN, false).putBoolean(Config.TEST_TAP, false).apply();
            stopService(new Intent(this, LiveCaptureService.class));
            Config.status(this, "Bot parado pelo usuário");
            refresh();
            Toast.makeText(this, "Bot desligado", Toast.LENGTH_SHORT).show();
        });
        LinearLayout permission = card(screen);
        permission.addView(label("PERMISSÃO DE ACESSIBILIDADE", 12, CYAN, true));
        addMargin(permission, label("Se o Android mostrar 'Configuração restrita', o aplicativo não consegue remover esse bloqueio. Confira a origem do APK antes de permitir o acesso. Em Configurações > Apps > SpikesBot Kwai > menu ⋮ pode existir a opção oficial 'Permitir configurações restritas'.", 13, LIGHT, false), 8, 0, 0, 0);
        addMargin(permission, label("A acessibilidade permite visualizar informações na tela e enviar toques. Ative somente se confiar neste APK e desligue ao terminar a gravação.", 12, MUTED, false), 8, 0, 0, 0);

        Button details = actionButton("ⓘ ABRIR DETALHES DO APP", 0xFF203A56, LIGHT);
        addMargin(screen, details, 10, 0, 0, 0);
        details.setOnClickListener(v -> {
            Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(info);
        });

        Button access = actionButton("⚙ CONFIGURAR ACESSIBILIDADE", 0xFF203A56, LIGHT);
        addMargin(screen, access, 10, 0, 0, 0);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        TextView tip = label("No modo VISÃO AO VIVO, confirme o compartilhamento da TELA INTEIRA. O Android mostra uma notificação enquanto a captura está ativa. O SpikesBot não salva nem envia imagens; ao terminar, use PARAR O BOT. Para o Kwai, grave separadamente com o gravador nativo e indique que é um bot.", 13, MUTED, false);
        addMargin(screen, tip, 17, 0, 0, 0);
        refresh();
    }

    private void start() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Ative o serviço SpikesBot nas configurações de acessibilidade", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        Intent game = getPackageManager().getLaunchIntentForPackage(Config.PACKAGE);
        if (game == null) {
            prefs.edit().putBoolean(Config.RUN, false).apply();
            Toast.makeText(this, "Instale o jogo original antes de iniciar", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + Config.PACKAGE))); }
            catch (Exception ignored) { /* App Store not available. */ }
            return;
        }
        if (prefs.getInt(Config.MODE, 1) == 1) {
            // Every Android 14+ capture session must request NEW user consent.
            // Capture the full display, not the SpikesBot settings window.
            MediaProjectionManager mgr = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent permission = Build.VERSION.SDK_INT >= 34
                    ? mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    : mgr.createScreenCaptureIntent();
            startActivityForResult(permission, CAPTURE_REQUEST);
            return;
        }
        stopService(new Intent(this, LiveCaptureService.class));
        launchBotGame(game);
    }

    private void launchBotGame(Intent game) {
        prefs.edit().putBoolean(Config.RUN, true)
                .putBoolean(Config.TEST_TAP, false)
                .putLong(Config.SESSION_START, android.os.SystemClock.uptimeMillis())
                .putInt(Config.TAPS, 0).putInt(Config.LIVE_FRAMES, 0)
                .putString(Config.STATS, "Iniciando sessão automática")
                .putString(Config.LAST_ERROR, "").apply();
        Config.status(this, prefs.getInt(Config.MODE, 1) == 0 ?
                "Modo CONTÍNUO: abra a partida; aguarde 4 segundos" :
                "VISÃO: procurando pássaro e limites da arena");
        game.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(game);
        refresh();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE_REQUEST) return;
        if (result != RESULT_OK || data == null) {
            prefs.edit().putBoolean(Config.RUN, false).apply();
            Config.status(this, "Captura não autorizada. O modo VISÃO AO VIVO não iniciou.");
            Toast.makeText(this, "Permissão de captura negada", Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        Intent game = getPackageManager().getLaunchIntentForPackage(Config.PACKAGE);
        if (game == null || !isAccessibilityEnabled()) return;
        stopService(new Intent(this, LiveCaptureService.class));
        Intent live = new Intent(this, LiveCaptureService.class);
        live.putExtra(LiveCaptureService.EXTRA_RESULT_CODE, result);
        live.putExtra(LiveCaptureService.EXTRA_RESULT_DATA, data);
        try {
            startForegroundService(live);
            launchBotGame(game);
        } catch (RuntimeException e) {
            prefs.edit().putBoolean(Config.RUN, false).apply();
            Config.status(this, "Erro iniciando visão ao vivo: " + e.getClass().getSimpleName());
            Toast.makeText(this, "Falha ao iniciar captura", Toast.LENGTH_LONG).show();
        }
    }

    private void testTap() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Ative a acessibilidade primeiro", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        Intent game = getPackageManager().getLaunchIntentForPackage(Config.PACKAGE);
        if (game == null) {
            Toast.makeText(this, "O jogo original não foi encontrado", Toast.LENGTH_LONG).show();
            return;
        }
        stopService(new Intent(this, LiveCaptureService.class));
        prefs.edit().putBoolean(Config.RUN, false)
                .putBoolean(Config.TEST_TAP, true)
                .putLong(Config.TEST_TIME, android.os.SystemClock.uptimeMillis())
                .putString(Config.LAST_ERROR, "")
                .apply();
        Config.status(this, "Teste de toque armado; aguarde no jogo");
        startActivity(game);
    }

    private boolean isAccessibilityEnabled() {
        String all = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(all)) return false;
        ComponentName name = new ComponentName(this, SpikesService.class);
        return all.contains(name.flattenToString()) || all.contains(name.flattenToShortString());
    }

    private void refresh() {
        if (statusText == null) return;
        boolean enabled = isAccessibilityEnabled();
        boolean running = prefs.getBoolean(Config.RUN, false);
        if (!enabled) statusText.setText("⚠ Acessibilidade desativada");
        else statusText.setText((running ? "● ATIVO — " : "○ PARADO — ") + prefs.getString(Config.STATUS, "Aguardando"));
        detailText.setText(prefs.getString(Config.STATS, "Ative o bot e comece o modo clássico") +
                (prefs.getString(Config.LAST_ERROR, "").isEmpty() ? "" :
                "\nÚltima falha: " + prefs.getString(Config.LAST_ERROR, "")));
        startBtn.setText(enabled ? "▶ INICIAR BOT AUTOMÁTICO" : "⚙ ATIVAR ACESSIBILIDADE");
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(updates);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(updates);
        super.onPause();
    }
    private final Runnable updates = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 650);
        }
    };

    private interface Changed { void set(int value); }
    private SeekBar.OnSeekBarChangeListener listener(Changed change) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean user) {
                if (user) change.set(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) { }
        };
    }

    private int dp(int v) { return (int) (getResources().getDisplayMetrics().density * v + .5); }
    private TextView label(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.START);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(15), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), 0xFF274059);
        content.setBackground(bg);
        addMargin(parent, content, 0, 12, 0, 0);
        return content;
    }
    private Button actionButton(String text, int bg, int fg) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(53));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(14));
        button.setBackground(gd);
        return button;
    }
    private void addMargin(LinearLayout layout, View view,
                           int top, int bottom, int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        layout.addView(view, params);
    }
}

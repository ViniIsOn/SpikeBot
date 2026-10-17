package br.com.poderazul.spikesbot;

import android.app.Activity;
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
        look.addView(label("Ajuste conforme a skin usada. O bot reconhece a cor pela imagem, sem acessar arquivos do jogo.", 12, MUTED, false));

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
        tuning.addView(label("Mais rápido não significa mais preciso: o Android pode limitar capturas da tela.", 12, MUTED, false));

        LinearLayout status = card(screen);
        status.addView(label("STATUS EM TEMPO REAL", 12, CYAN, true));
        statusText = label("Aguardando", 15, LIGHT, true);
        addMargin(status, statusText, 8, 0, 0, 0);
        detailText = label("Nenhuma partida iniciada", 12, MUTED, false);
        addMargin(status, detailText, 6, 0, 0, 0);

        startBtn = actionButton("▶ INICIAR NO JOGO", CYAN, BG);
        addMargin(screen, startBtn, 12, 8, 0, 0);
        startBtn.setOnClickListener(v -> start());
        Button stop = actionButton("■ PARAR O BOT", CARD, LIGHT);
        addMargin(screen, stop, 10, 0, 0, 0);
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean(Config.RUN, false).apply();
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

        TextView tip = label("Para vídeos no Kwai: inicie o gravador de tela do Android, abra a partida clássica e deixe o SpikesBot agir. Mostre no vídeo que é um bot. Não grava nem envia sua tela para servidores.", 13, MUTED, false);
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
        prefs.edit().putBoolean(Config.RUN, true).apply();
        Config.status(this, "Iniciando… aguarde uma partida com espinhos laterais visíveis");
        game.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(game);
        refresh();
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
        detailText.setText(prefs.getString(Config.STATS, "Ative o bot e comece o modo clássico"));
        startBtn.setText(enabled ? "▶ INICIAR NO JOGO" : "⚙ ATIVAR ACESSIBILIDADE");
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

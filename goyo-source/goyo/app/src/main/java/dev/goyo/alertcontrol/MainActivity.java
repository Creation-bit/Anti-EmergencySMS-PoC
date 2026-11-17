package dev.goyo.alertcontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.goyo.alertcontrol.core.ReceiverController;
import dev.goyo.alertcontrol.core.ReceiverState;
import dev.goyo.alertcontrol.core.Targets;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(26, 46, 44);
    private static final int MUTED = Color.rgb(94, 112, 107);
    private static final int TEAL = Color.rgb(8, 127, 115);
    private static final int BG = Color.rgb(244, 246, 245);
    private ControlModel model;
    private TextView status, statusNote, connectionStatus, feedback, details, packages;
    private Button connect, disable, restore, refresh, recovery;
    private ProgressBar progress;
    private final Runnable observer = this::render;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        model = ((GoyoApp) getApplication()).model;
        buildScreen();
    }

    @Override protected void onStart() {
        super.onStart();
        model.observe(observer);
        model.refresh();
    }
    @Override protected void onStop() { model.removeObserver(observer); super.onStop(); }

    private void buildScreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout outer = column();
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(outer);
        LinearLayout content = column();
        content.setPadding(dp(24), dp(26), dp(24), dp(32));
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        outer.addView(content, new LinearLayout.LayoutParams(Math.min(screenWidth, dp(560)), -2));
        // Required for target 35/36 edge-to-edge, including display cutouts.
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        } else {
            scroll.setFitsSystemWindows(true);
        }

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_goyo);
        icon.setContentDescription("고요 로고");
        brand.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView wordmark = text("고요", 24, INK, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMarginStart(dp(12));
        brand.addView(wordmark, nameParams);
        TextView version = text("SHIZUKU", 11, TEAL, true);
        version.setLetterSpacing(.10f);
        version.setPadding(dp(10), dp(7), dp(10), dp(7));
        version.setBackground(shape(Color.rgb(225, 239, 234), 20));
        brand.addView(version);
        content.addView(brand);
        space(content, 30);
        content.addView(text("재난 알림 제어", 30, INK, true));
        space(content, 8);
        content.addView(text("수신 앱의 상태를 확인하고,\n필요할 때 끄거나 복원하세요.", 15, MUTED, false));
        space(content, 24);

        LinearLayout statusCard = card(content, Color.rgb(228, 241, 235));
        statusCard.addView(text("현재 상태", 12, TEAL, true));
        space(statusCard, 12);
        status = text("확인 전", 27, INK, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusCard.addView(status);
        space(statusCard, 8);
        statusNote = text("권한을 연결한 뒤 기기에서 직접 확인합니다.", 14, MUTED, false);
        statusCard.addView(statusNote);

        space(content, 16);
        LinearLayout connectionCard = card(content, Color.WHITE);
        connectionCard.addView(text("01   권한 연결", 13, TEAL, true));
        space(connectionCard, 10);
        connectionStatus = text("Shizuku 연결 필요", 19, INK, true);
        connectionCard.addView(connectionStatus);
        space(connectionCard, 6);
        connectionCard.addView(text("Shizuku를 실행한 뒤 고요의 권한을 허용해 주세요.", 14, MUTED, false));
        space(connectionCard, 14);
        connect = button("Shizuku 연결", true, v -> model.connect());
        connectionCard.addView(connect);
        Button help = button("Shizuku 실행 · 설정 안내", false, v -> showSetup());
        connectionCard.addView(help);

        space(content, 16);
        LinearLayout controlCard = card(content, Color.WHITE);
        controlCard.addView(text("02   수신 앱 관리", 13, TEAL, true));
        space(controlCard, 10);
        packages = text("권한 연결 후 지원 여부를 확인합니다.", 14, MUTED, false);
        packages.setTextIsSelectable(true);
        controlCard.addView(packages);
        space(controlCard, 12);
        disable = button("수신 앱 비활성화", true, v -> model.execute(1));
        controlCard.addView(disable);
        space(controlCard, 6);
        restore = button("이전 상태 복원", false, v -> model.execute(2));
        controlCard.addView(restore);
        refresh = button("현재 상태 새로고침", false, v -> model.refresh());
        controlCard.addView(refresh);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        controlCard.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        space(controlCard, 8);
        feedback = text("", 14, MUTED, false);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        controlCard.addView(feedback);
        details = text("", 12, Color.rgb(152, 67, 44), false);
        details.setTextIsSelectable(true);
        controlCard.addView(details);
        recovery = button("복원 명령 보기", false, v -> showRecovery());
        controlCard.addView(recovery);

        space(content, 20);
        content.addView(text("적용 범위", 14, INK, true));
        space(content, 8);
        content.addView(text("확인된 수신 앱이 표시하는 모든 재난 알림에 적용됩니다. 모뎀 단계의 수신 차단을 보장하지 않으며, 제조사에서 보호한 앱은 비활성화할 수 없을 수 있어요.", 13, MUTED, false));
        space(content, 8);
        content.addView(text("앱을 삭제해도 비활성화 상태가 남을 수 있습니다. 삭제·데이터 초기화 전에 먼저 복원해 주세요. 시스템 업데이트 후에는 상태를 다시 확인하세요.", 13, MUTED, false));
        space(content, 8);
        content.addView(button("안드로이드 재난문자 설정 열기", false, v -> openAlertSettings()));
        space(content, 16);
        TextView footer = text("기기 안에서만 동작 · 인터넷 권한 없음", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);
        setContentView(scroll);
    }

    private void render() {
        if (status == null) return;
        boolean connected = model.connected();
        boolean active = model.busy || model.connecting;
        boolean disabled = model.inspected && ReceiverController.allDisabled(model.states);
        if (active) {
            status.setText("확인하고 있어요");
            statusNote.setText("시스템의 응답과 실제 적용 상태를 확인합니다.");
        } else if (!model.inspected) {
            status.setText("확인 전");
            statusNote.setText(model.hasRestore ? "복원 기록이 있습니다. 권한을 연결해 상태를 확인하세요." : "권한을 연결한 뒤 기기에서 직접 확인합니다.");
        } else if (model.states.isEmpty()) {
            status.setText("지원 대상 없음");
            statusNote.setText("이 기기의 수신 앱은 자동 제어 대상이 아닙니다.");
        } else if (disabled) {
            status.setText("수신 앱 꺼짐");
            statusNote.setText("확인된 앱의 비활성화 상태입니다. 모뎀 수신은 별개입니다.");
        } else {
            status.setText("비활성화 안 됨");
            statusNote.setText("수신 앱이 기본값 또는 실행 가능한 상태입니다.");
        }
        connectionStatus.setText(connected ? "Shizuku 연결됨" : model.connecting ? "연결 중" : "Shizuku 연결 필요");
        connect.setText(connected ? "연결 상태 확인" : "Shizuku 연결");
        connect.setEnabled(!active && model.userId == 0);
        StringBuilder list = new StringBuilder();
        if (model.inspected) {
            for (ReceiverState receiver : model.states) {
                if (list.length() > 0) list.append("\n\n");
                list.append(receiver.packageName.startsWith("com.google") ? "Google 재난문자 수신 앱" : "Android 재난문자 수신 앱");
                list.append(" · ").append(Targets.stateLabel(receiver.state)).append("\n").append(receiver.packageName);
            }
        }
        packages.setText(list.length() > 0 ? list : model.inspected ? "지원하는 수신 앱을 찾지 못했습니다." : "권한 연결 후 지원 여부를 확인합니다.");
        disable.setEnabled(!active && connected && model.inspected && !model.states.isEmpty() && !disabled && !model.storageError);
        restore.setEnabled(!active && connected && model.hasRestore);
        refresh.setEnabled(!active && connected);
        recovery.setVisibility(model.hasRestore ? View.VISIBLE : View.GONE);
        recovery.setEnabled(!active);
        feedback.setText(model.message);
        details.setText(model.detail);
        details.setVisibility(model.detail.isEmpty() ? View.GONE : View.VISIBLE);
        progress.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void showSetup() {
        new AlertDialog.Builder(this).setTitle("Shizuku 연결하기")
                .setMessage("1. Shizuku를 설치하고 실행하세요.\n\n2. Android 11 이상은 무선 디버깅으로 시작할 수 있습니다. 이전 버전은 PC의 ADB가 필요합니다.\n\n3. 고요로 돌아와 Shizuku 연결을 누르고 권한을 허용하세요.\n\n재부팅 후에는 Shizuku를 다시 시작해야 할 수 있습니다.")
                .setPositiveButton("Shizuku 열기", (dialog, which) -> {
                    Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (launch != null) open(launch);
                    else open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
                })
                .setNeutralButton("공식 사용 안내", (dialog, which) -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/"))))
                .setNegativeButton("닫기", null).show();
    }

    private void showRecovery() {
        String commands = model.recoveryCommands();
        new AlertDialog.Builder(this).setTitle("PC에서 이전 상태 복원")
                .setMessage("앱을 사용하기 어려울 때 USB 디버깅이 연결된 PC에서 실행하세요. 현재 저장된 원래 상태에 맞춘 명령입니다.\n\n" + commands)
                .setPositiveButton("명령 복사", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("고요 복원 명령", commands));
                    Toast.makeText(this, "복원 명령을 복사했습니다.", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("닫기", null).show();
    }

    private void openAlertSettings() {
        try { startActivity(new Intent("android.settings.WIRELESS_EMERGENCY_ALERTS_SETTINGS")); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { startActivity(new Intent("android.settings.WIRELESS_EMERGENCY_ALERTS")); }
            catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(this, "설정에서 ‘재난문자’ 또는 ‘긴급 알림’을 검색하세요.", Toast.LENGTH_LONG).show();
                open(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "열 수 있는 앱을 찾지 못했습니다.", Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout layout = column();
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(shape(color, 24));
        parent.addView(layout, new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private Button button(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int[][] states = {new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new ColorStateList(states, new int[]{Color.rgb(130, 144, 139), primary ? Color.WHITE : TEAL}));
        GradientDrawable background = shape(primary ? TEAL : Color.rgb(240, 245, 242), 14);
        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(32, 0, 0, 0)), background, null);
        button.setBackground(ripple);
        button.setBackgroundTintList(new ColorStateList(states, new int[]{Color.rgb(228, 234, 230), primary ? TEAL : Color.rgb(240, 245, 242)}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

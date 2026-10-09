package io.github.nicoenhance;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "https://github.com/KotobaWakusei/NicoEnhance";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/KotobaWakusei/NicoEnhance/releases/latest";
    private static final long ACTIVE_WINDOW_MS = 24L * 60 * 60 * 1000;
    private final ModuleConfig moduleConfig = new ModuleConfig();
    private String latestReleaseUrl = GITHUB_REPO + "/releases/latest";

    private TextView updateStatus;
    private TextView moduleStatusText;
    private android.view.View statusDot;
    private CardView moduleStatusCard;
    private String currentVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentVersion = "unknown";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        ((TextView) findViewById(R.id.versionInfo)).setText("v" + currentVersion);
        updateStatus = findViewById(R.id.updateStatus);
        moduleStatusText = findViewById(R.id.statusText);
        moduleStatusCard = findViewById(R.id.statusCard);
        statusDot = findViewById(R.id.statusDot);

        loadTranslationStats();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkModuleStatus();
    }

    private void setupClickListeners() {
        findViewById(R.id.githubCard).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO));
            startActivity(intent);
        });

        MaterialButton checkBtn = findViewById(R.id.checkUpdateBtn);
        checkBtn.setOnClickListener(v -> checkForUpdates());

        findViewById(R.id.moduleSettingsBtn).setOnClickListener(v -> showModuleSettingsDialog());

        findViewById(R.id.statusCard).setOnClickListener(v -> checkModuleStatus());
    }

    private void checkModuleStatus() {
        boolean active = isModuleActive();
        if (statusDot != null) {
            int dotColor = active ? 0xFF4CAF50 : 0xFFF44336;
            statusDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(dotColor));
            statusDot.setContentDescription(active ? "模块已激活" : "模块未激活");
        }
        if (active) {
            String since = moduleActiveLastSeen();
            moduleStatusText.setText(since == null
                    ? "已检测到 niconico 注入"
                    : "LSPosed 模块已激活\n上次注入时间：" + since);
        } else {
            moduleStatusText.setText("LSPosed 模块未激活\n"
                    + "改包名后在 LSPosed 中是一个全新的、默认关闭的模块：\n"
                    + "1. 启用 io.github.kotobawakusei.nicoenhance\n"
                    + "2. 确认作用域包含 niconico\n"
                    + "3. 重启手机（或强制停止 niconico 后重开）");
        }
        moduleStatusCard.setCardBackgroundColor(getColor(R.color.card_background));
    }

    private void loadTranslationStats() {
        // Parsing ~4k properties lines is I/O + work; keep it off the UI thread so onCreate
        // does not block on it.
        new Thread(() -> {
            String total;
            String exact;
            try {
                total = String.valueOf(countEntries("translations/zh-CN/strings.properties"));
                exact = String.valueOf(countEntries("translations/zh-CN/exact.properties"));
            } catch (Exception e) {
                total = "?";
                exact = "?";
            }
            final String t = total;
            final String e = exact;
            runOnUiThread(() -> {
                ((TextView) findViewById(R.id.statTotal)).setText(t);
                ((TextView) findViewById(R.id.statExact)).setText(e);
            });
        }).start();
    }

    private int countEntries(String assetPath) throws java.io.IOException {
        Properties props = new Properties();
        try (java.io.InputStream in = getAssets().open(assetPath)) {
            props.load(in);
        }
        return props.size();
    }

    private void checkForUpdates() {
        MaterialButton checkBtn = findViewById(R.id.checkUpdateBtn);
        checkBtn.setEnabled(false);
        updateStatus.setText("正在检查 GitHub 最新正式版本…");
        updateStatus.setOnClickListener(null);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "NicoEnhance-Android");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                int code = conn.getResponseCode();
                java.io.InputStream stream = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    String detail = body.toString();
                    if (detail.length() > 180) detail = detail.substring(0, 180);
                    throw new java.io.IOException("HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
                }
                JSONObject release = new JSONObject(body.toString());
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    throw new java.io.IOException("GitHub 返回的不是正式版本");
                }
                String tag = release.optString("tag_name", "").trim();
                String version = tag.replaceFirst("(?i)^v", "");
                if (!version.matches("\\d+(\\.\\d+){1,3}([+-].*)?")) {
                    throw new java.io.IOException("无法识别版本标签：" + tag);
                }
                try {
                    currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException ignored) {}
                final String installed = currentVersion == null ? "0.0.0" : currentVersion;
                final String releaseUrl = release.optString("html_url", GITHUB_REPO + "/releases/tag/" + tag);
                final String result = compareVersions(installed, version) >= 0
                        ? "已是最新版本（当前 " + installed + " / 最新 " + tag + "）"
                        : "发现新版本：" + tag + "，点击此处打开下载页面";
                runOnUiThread(() -> {
                    latestReleaseUrl = releaseUrl;
                    updateStatus.setText(result);
                    updateStatus.setOnClickListener(v -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(latestReleaseUrl)));
                        } catch (Exception e) {
                            updateStatus.setText("无法打开发布页面：" + e.getClass().getSimpleName());
                        }
                    });
                    checkBtn.setEnabled(true);
                });
            } catch (Exception e) {
                String detail = e.getMessage();
                final String message = "检查失败：" + e.getClass().getSimpleName()
                        + (detail == null || detail.trim().isEmpty() ? "" : "\n" + detail)
                        + "\n请检查网络，或直接打开 GitHub Releases。";
                runOnUiThread(() -> {
                    updateStatus.setText(message);
                    updateStatus.setOnClickListener(v -> startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO + "/releases"))));
                    checkBtn.setEnabled(true);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("(?i)^v", "").split("[.+-]");
        String[] b = right.replaceFirst("(?i)^v", "").split("[.+-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? parseVersionPart(a[i]) : 0;
            int y = i < b.length ? parseVersionPart(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseVersionPart(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Active means the target niconico process recently called ConfigProvider after injection.
     * The module app being self-hooked or an old marker file is not proof of target injection.
     */
    public boolean isModuleActive() {
        long timestamp = getSharedPreferences(ModuleConfig.PREFS_NAME, MODE_PRIVATE)
                .getLong("last_target_hook_ts", 0L);
        long age = System.currentTimeMillis() - timestamp;
        return timestamp > 0L && age >= 0L && age <= ACTIVE_WINDOW_MS;
    }

    private String moduleActiveLastSeen() {
        long timestamp = getSharedPreferences(ModuleConfig.PREFS_NAME, MODE_PRIVATE)
                .getLong("last_target_hook_ts", 0L);
        if (timestamp <= 0L) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
    }

    private void showModuleSettingsDialog() {
        moduleConfig.refresh(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        Switch translation = addSettingSwitch(content, "启用翻译与增强", moduleConfig.isTranslationEnabled());
        Switch runtime = addSettingSwitch(content, "翻译应用界面文字", moduleConfig.isRuntimeTextTranslationSwitchEnabled());
        Switch webview = addSettingSwitch(content, "翻译 WebView 内容", moduleConfig.isWebViewTranslationSwitchEnabled());
        Switch ads = addSettingSwitch(content, "去除应用内广告", moduleConfig.isAdRemovalEnabled());
        Switch premium = addSettingSwitch(content, "解锁会员特权检查", moduleConfig.isPremiumUnlockEnabled());
        Switch debug = addSettingSwitch(content, "启用调试日志", moduleConfig.isDebugLogEnabled());
        new AlertDialog.Builder(this)
                .setTitle("NicoEnhance 模块设置")
                .setMessage("此处与 niconico 应用内的 NicoEnhance 设置共用配置。部分选项需要重新打开 niconico 才会完全生效。")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    moduleConfig.save(this, translation.isChecked(), runtime.isChecked(),
                            webview.isChecked(), ads.isChecked(), debug.isChecked(), premium.isChecked());
                    updateStatus.setText("模块设置已保存；重开 niconico 可应用所有变更。");
                })
                .show();
    }

    private Switch addSettingSwitch(LinearLayout parent, String label, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(15);
        toggle.setChecked(checked);
        toggle.setPadding(dp(12), dp(12), dp(12), dp(12));
        parent.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return toggle;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

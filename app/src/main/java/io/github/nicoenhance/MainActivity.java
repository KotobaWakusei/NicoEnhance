package io.github.nicoenhance;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "https://github.com/KotobaWakusei/NicoEnhance";
    private static final String RELEASES_API = "https://api.github.com/repos/KotobaWakusei/NicoEnhance/releases?per_page=30";

    private TextView updateStatus;
    private TextView moduleStatusText;
    private android.view.View statusDot;
    private CardView moduleStatusCard;
    private String currentVersion;
    private String latestReleaseUrl = GITHUB_REPO + "/releases";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentVersion = "1.0.3";
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

        findViewById(R.id.statusCard).setOnClickListener(v -> checkModuleStatus());
        findViewById(R.id.settingsBtn).setOnClickListener(v -> showModuleSettings());
        updateStatus.setOnClickListener(v -> openLatestRelease());
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
                    ? "LSPosed 模块已激活"
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

    private String moduleActiveLastSeen() {
        try {
            String value = android.provider.Settings.System.getString(
                    getContentResolver(), "nicoenhance_module_active_ts");
            if (value == null || value.isEmpty()) return null;
            long ts = Long.parseLong(value);
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(ts));
        } catch (Throwable t) {
            return null;
        }
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
        long lastCheck = getPreferences(MODE_PRIVATE).getLong("last_update_check", 0);
        if (System.currentTimeMillis() - lastCheck < 15000) {
            updateStatus.setText("刚刚检查过，请稍后再试");
            return;
        }
        updateStatus.setText("正在检查 GitHub Releases…");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                conn.setRequestProperty("User-Agent", "NicoEnhance-Android");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    String message = "检查失败（HTTP " + code + "）";
                    runOnUiThread(() -> updateStatus.setText(message));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONArray releases = new JSONArray(sb.toString());
                String latestTag = "";
                String releaseUrl = GITHUB_REPO + "/releases";
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) continue;
                    String tag = release.optString("tag_name", "").trim();
                    if (!tag.matches("(?i)^v?\\d+(\\.\\d+)+$")) continue;
                    if (latestTag.isEmpty() || compareVersions(tag, latestTag) > 0) {
                        latestTag = tag;
                        releaseUrl = release.optString("html_url", GITHUB_REPO + "/releases");
                    }
                }
                getPreferences(MODE_PRIVATE).edit().putLong("last_update_check", System.currentTimeMillis()).apply();
                final String tagResult = latestTag;
                final String urlResult = releaseUrl;
                final String result;
                if (latestTag.isEmpty()) result = "没有找到有效的版本 Release";
                else if (compareVersions(currentVersion, latestTag) >= 0) result = "已是最新版本（v" + latestTag.replaceFirst("^[vV]", "") + "）";
                else result = "发现新版本：v" + latestTag.replaceFirst("^[vV]", "") + " · 点击查看发布页";
                runOnUiThread(() -> {
                    latestReleaseUrl = urlResult;
                    updateStatus.setText(result);
                    updateStatus.setClickable(!tagResult.isEmpty() && compareVersions(currentVersion, tagResult) < 0);
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                String message = "检查失败：" + e.getClass().getSimpleName()
                        + (msg != null ? " · " + msg : "")
                        + "\n请确认能够访问 api.github.com";
                runOnUiThread(() -> updateStatus.setText(message));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "NicoEnhance-update-check").start();
    }

    /** Compare numeric version segments; ignores a leading v and any build suffix. */
    private static int compareVersions(String left, String right) {
        String[] a = left.trim().replaceFirst("^[vV]", "").split("[.+-]");
        String[] b = right.trim().replaceFirst("^[vV]", "").split("[.+-]");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? parseVersionPart(a[i]) : 0;
            int y = i < b.length ? parseVersionPart(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        String digits = part.replaceFirst("[^0-9].*$", "");
        try { return digits.isEmpty() ? 0 : Integer.parseInt(digits); }
        catch (NumberFormatException e) { return 0; }
    }

    public boolean isModuleActive() {
        if (isSelfHooked()) return true;
        if (moduleActiveSentinelExists()) return true;
        return false;
    }

    /**
     * Primary signal: if any LSPosed-related shared library is mapped into this process, the
     * module's own process was injected by LSPosed (the module is enabled and its own package
     * is in the static scope). We probe by name rather than version because the runtime
     * library name changes between LSPosed variants.
     */
    public boolean isSelfHooked() {
        // Stream line-by-line and bail out on the first match instead of slurping the whole
        // (potentially ~100 KB) maps file into memory on every onResume.
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream("/proc/self/maps")))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("libxposed") || line.contains("lspd") || line.contains("lsposed")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Cross-process sentinel written by NicoEnhance after each niconico launch under LSPosed.
     * Stored in {@link android.provider.Settings.System} so it survives reboots and is visible
     * to this MainActivity without permissions.
     */
    private boolean moduleActiveSentinelExists() {
        try {
            // A status sentinel only proves recent hook activity; an old timestamp must not
            // make a disabled module look active forever.
            long cutoff = System.currentTimeMillis() - 5L * 60 * 1000;
            String value = android.provider.Settings.System.getString(
                    getContentResolver(), "nicoenhance_module_active_ts");
            if (value == null || value.isEmpty()) return false;
            long ts = Long.parseLong(value);
            return ts >= cutoff;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean moduleActiveMarkerFileExists() { return false; }

    private void showModuleSettings() {
        Bundle values = readConfig();
        String[] keys = {"translation_enabled", "runtime_text_translation_enabled",
                "webview_translation_enabled", "ad_removal_enabled",
                "premium_unlock_enabled", "debug_log_enabled"};
        String[] titles = {"启用翻译与增强", "翻译应用界面文字", "翻译 WebView 内容",
                "去除广告", "解锁会员特权", "调试日志"};
        String[] summaries = {"总开关", "翻译设置、菜单和动态界面", "翻译版权页与内嵌网页",
                "隐藏应用内广告和视频前贴片", "启用会员专属界面功能", "排查问题时开启，日常使用建议关闭"};
        Switch[] switches = new Switch[keys.length];
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density + 0.5f);
        content.setPadding(pad, pad / 2, pad, pad / 2);
        for (int i = 0; i < keys.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, pad / 3, 0, pad / 3);
            Switch sw = new Switch(this);
            sw.setText(titles[i]);
            sw.setTextSize(15);
            sw.setChecked(values.getBoolean(keys[i], i != 5));
            TextView summary = new TextView(this);
            summary.setText(summaries[i]);
            summary.setTextSize(12);
            summary.setTextColor(getColor(R.color.text_tertiary));
            row.addView(sw);
            row.addView(summary);
            content.addView(row);
            switches[i] = sw;
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("NicoEnhance 设置")
                .setMessage("这里与 niconico 应用内的 NicoEnhance 设置共用配置。")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    Bundle out = new Bundle();
                    for (int i = 0; i < keys.length; i++) out.putBoolean(keys[i], switches[i].isChecked());
                    try {
                        Bundle result = getContentResolver().call(ConfigProvider.CONTENT_URI, "save_config", null, out);
                        if (result != null && result.getBoolean("success", false)) {
                            Toast.makeText(this, "设置已保存；部分功能需重新进入 niconico 生效", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                        }
                    } catch (Throwable t) {
                        Toast.makeText(this, "保存失败：" + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private Bundle readConfig() {
        try {
            Bundle values = getContentResolver().call(ConfigProvider.CONTENT_URI, "get_config", null, null);
            if (values != null) return values;
        } catch (Throwable ignored) {}
        Bundle defaults = new Bundle();
        defaults.putBoolean("translation_enabled", true);
        defaults.putBoolean("runtime_text_translation_enabled", true);
        defaults.putBoolean("webview_translation_enabled", true);
        defaults.putBoolean("ad_removal_enabled", true);
        defaults.putBoolean("premium_unlock_enabled", true);
        defaults.putBoolean("debug_log_enabled", false);
        return defaults;
    }

    private void openLatestRelease() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(latestReleaseUrl))); }
        catch (Throwable ignored) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO + "/releases"))); }
    }
}

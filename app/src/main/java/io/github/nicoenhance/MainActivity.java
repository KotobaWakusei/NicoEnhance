package io.github.nicoenhance;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "https://github.com/KotobaWakusei/NicoEnhance";
    private static final String RELEASES_API = "https://api.github.com/repos/KotobaWakusei/NicoEnhance/releases/latest";

    private TextView updateStatus;
    private String currentVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentVersion = "1.0";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        ((TextView) findViewById(R.id.versionInfo)).setText("版本：" + currentVersion);
        updateStatus = findViewById(R.id.updateStatus);

        checkModuleStatus();
        loadTranslationStats();
        setupClickListeners();
    }

    private void setupClickListeners() {
        findViewById(R.id.githubCard).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO));
            startActivity(intent);
        });

        findViewById(R.id.updateCard).setOnClickListener(v -> checkForUpdates());
    }

    private void checkModuleStatus() {
        View statusDot = findViewById(R.id.statusDot);
        TextView statusText = findViewById(R.id.statusText);

        boolean active = isModuleActive();
        if (active) {
            statusDot.setBackgroundResource(R.drawable.circle_green);
            statusText.setText("  LSPosed 模块已启用");
        } else {
            statusDot.setBackgroundResource(R.drawable.circle_red);
            statusText.setText("  LSPosed 模块未激活");
        }
    }

    private void loadTranslationStats() {
        try {
            Properties sp = new Properties();
            Properties ep = new Properties();
            Properties pp = new Properties();
            sp.load(getAssets().open("translations/zh-CN/strings.properties"));
            ep.load(getAssets().open("translations/zh-CN/exact.properties"));
            pp.load(getAssets().open("translations/zh-CN/phrases.properties"));

            ((TextView) findViewById(R.id.statTotal)).setText(String.valueOf(sp.size()));
            ((TextView) findViewById(R.id.statExact)).setText(String.valueOf(ep.size()));
            ((TextView) findViewById(R.id.statPhrase)).setText(String.valueOf(pp.size()));
        } catch (Exception e) {
            ((TextView) findViewById(R.id.statTotal)).setText("?");
            ((TextView) findViewById(R.id.statExact)).setText("?");
            ((TextView) findViewById(R.id.statPhrase)).setText("?");
        }
    }

    private void checkForUpdates() {
        long lastCheck = getPreferences(MODE_PRIVATE).getLong("last_update_check", 0);
        if (System.currentTimeMillis() - lastCheck < 60000) {
            updateStatus.setText("请勿频繁检查");
            return;
        }

        updateStatus.setText("检查中...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(RELEASES_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "NicoEnhance");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> updateStatus.setText("检查失败 (" + code + ")"));
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject release = new JSONObject(sb.toString());
                String latestTag = release.optString("tag_name", "").replaceAll("^v", "");
                String latestName = release.optString("name", "");
                String body = release.optString("body", "");

                getPreferences(MODE_PRIVATE).edit().putLong("last_update_check", System.currentTimeMillis()).apply();

                runOnUiThread(() -> {
                    if (latestTag.isEmpty()) {
                        updateStatus.setText("无发布版本");
                        return;
                    }
                    if (!latestTag.equals(currentVersion)) {
                        updateStatus.setText("发现新版本: v" + latestTag);
                        Toast.makeText(this, "新版本: " + latestName + "\n" + body, Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus.setText("已是最新版本");
                        Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> updateStatus.setText("检查失败" + (msg != null ? ": " + msg : "")));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public boolean isModuleActive() {
        return new java.io.File(getFilesDir(), ".module_active").exists();
    }
}

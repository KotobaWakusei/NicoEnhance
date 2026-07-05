package io.github.nicoenhance;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "https://github.com/KotobaWakusei/NicoEnhance";
    private static final String RELEASES_API = "https://api.github.com/repos/KotobaWakusei/NicoEnhance/releases/latest";

    private TextView updateStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String version = "1.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        ((TextView) findViewById(R.id.versionInfo)).setText("版本：" + version);
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
            android.content.res.AssetManager am = getAssets();
            java.util.Properties sp = new java.util.Properties();
            java.util.Properties ep = new java.util.Properties();
            java.util.Properties pp = new java.util.Properties();
            sp.load(am.open("translations/zh-CN/strings.properties"));
            ep.load(am.open("translations/zh-CN/exact.properties"));
            pp.load(am.open("translations/zh-CN/phrases.properties"));

            ((TextView) findViewById(R.id.statTotal)).setText(String.valueOf(sp.size()));
            ((TextView) findViewById(R.id.statExact)).setText(String.valueOf(ep.size()));
            ((TextView) findViewById(R.id.statPhrase)).setText(String.valueOf(pp.size()));
        } catch (Throwable ignored) {
        }
    }

    private void checkForUpdates() {
        updateStatus.setText("检查中...");
        new Thread(() -> {
            try {
                URL url = new URL(RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> updateStatus.setText("检查失败"));
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject release = new JSONObject(sb.toString());
                String latestTag = release.optString("tag_name", "");
                String latestName = release.optString("name", "");
                String htmlUrl = release.optString("html_url", "");
                String body = release.optString("body", "");

                String currentVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                runOnUiThread(() -> {
                    if (latestTag.isEmpty()) {
                        updateStatus.setText("无发布版本");
                        return;
                    }
                    if (!latestTag.equals(currentVer)) {
                        updateStatus.setText("发现新版本: " + latestTag);
                        Toast.makeText(this, "新版本: " + latestName + "\n" + body, Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus.setText("已是最新版本");
                        Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateStatus.setText("检查失败"));
            }
        }).start();
    }

    public boolean isModuleActive() {
        return new java.io.File(getFilesDir(), ".module_active").exists();
    }
}

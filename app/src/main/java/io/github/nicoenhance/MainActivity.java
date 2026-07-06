package io.github.nicoenhance;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "https://github.com/KotobaWakusei/NicoEnhance";
    private static final String RELEASES_API = "https://api.github.com/repos/KotobaWakusei/NicoEnhance/releases/latest";

    private MaterialTextView updateStatus;
    private MaterialTextView moduleStatusText;
    private CardView moduleStatusCard;
    private String currentVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentVersion = "1.0.0";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        ((MaterialTextView) findViewById(R.id.versionInfo)).setText("v" + currentVersion);
        updateStatus = findViewById(R.id.updateStatus);
        moduleStatusText = findViewById(R.id.statusText);
        moduleStatusCard = findViewById(R.id.statusCard);

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
    }

    private void checkModuleStatus() {
        boolean active = isModuleActive();
        if (active) {
            moduleStatusText.setText("LSPosed 模块已激活");
            moduleStatusCard.setCardBackgroundColor(getColor(R.color.card_background));
        } else {
            moduleStatusText.setText("LSPosed 模块未激活\n请在 LSPosed 中勾选 niconico 并重启手机");
            moduleStatusCard.setCardBackgroundColor(getColor(R.color.card_background));
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

            ((MaterialTextView) findViewById(R.id.statTotal)).setText(String.valueOf(sp.size()));
            ((MaterialTextView) findViewById(R.id.statExact)).setText(String.valueOf(ep.size()));
            ((MaterialTextView) findViewById(R.id.statPhrase)).setText(String.valueOf(pp.size()));
        } catch (Exception e) {
            ((MaterialTextView) findViewById(R.id.statTotal)).setText("?");
            ((MaterialTextView) findViewById(R.id.statExact)).setText("?");
            ((MaterialTextView) findViewById(R.id.statPhrase)).setText("?");
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
                String latestTag = release.optString("tag_name", "");

                getPreferences(MODE_PRIVATE).edit().putLong("last_update_check", System.currentTimeMillis()).apply();

                String result;
                if (latestTag.isEmpty()) {
                    result = "无发布版本";
                } else if (latestTag.equals("v" + currentVersion)) {
                    result = "已是最新版本 (" + latestTag + ")";
                } else {
                    result = "发现新版本: " + latestTag;
                }
                String finalResult = result;
                runOnUiThread(() -> updateStatus.setText(finalResult));
            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> updateStatus.setText("检查失败" + (msg != null ? ": " + msg : "")));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public boolean isModuleActive() {
        return new java.io.File(getFilesDir(), ".module_active").exists()
                || new java.io.File("/data/data/" + getPackageName() + "/files/.module_active").exists();
    }
}

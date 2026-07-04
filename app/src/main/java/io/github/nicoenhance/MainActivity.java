package io.github.nicoenhance;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String version = "1.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        ((TextView) findViewById(R.id.versionInfo)).setText("\u7248\u672C\uFF1A" + version);

        checkModuleStatus();
    }

    private void checkModuleStatus() {
        View statusDot = findViewById(R.id.statusDot);
        TextView statusText = findViewById(R.id.statusText);

        boolean active = isModuleActive();
        if (active) {
            statusDot.setBackgroundResource(R.drawable.circle_green);
            statusText.setText("  LSPosed \u6A21\u5757\u5DF2\u542F\u7528");
        } else {
            statusDot.setBackgroundResource(R.drawable.circle_red);
            statusText.setText("  LSPosed \u6A21\u5757\u672A\u6FC0\u6D3B");
        }
    }

    public boolean isModuleActive() {
        return new java.io.File(getFilesDir(), ".module_active").exists();
    }
}

package com.bawazir.golddaily;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * شاشة أولى: تطلب رابط تطبيق الويب (/exec) وتحفظه، ثم تفتح النموذج.
 * تظهر أيضاً عند الضغط على «تغيير الرابط» من شاشة الخطأ.
 */
public class SetupActivity extends AppCompatActivity {

    private EditText input;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        input = findViewById(R.id.urlInput);
        error = findViewById(R.id.error);
        Button save = findViewById(R.id.save);

        String existing = Prefs.getUrl(this);
        if (!TextUtils.isEmpty(existing)) {
            input.setText(existing);
            input.setSelection(existing.length());
        }

        save.setOnClickListener(v -> onSave());
    }

    private void onSave() {
        String url = input.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            error.setText(R.string.error_empty);
            error.setVisibility(View.VISIBLE);
            return;
        }
        if (!Prefs.isValidUrl(url)) {
            error.setText(R.string.error_url);
            error.setVisibility(View.VISIBLE);
            return;
        }
        error.setVisibility(View.GONE);
        Prefs.setUrl(this, url);

        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_URL, url);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}

package com.winlator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.winlator.core.AppUtils;

public class MainActivity extends AppCompatActivity {
    private static final byte PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.setActivityTheme(this);

        if (!hasStoragePermission()) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
            return;
        }

        // Simplesmente abre a tela de containers do Winlator
        Intent intent = new Intent(this, ContainersFragment.class);
        // Como ContainersFragment não é Activity, abre a MainActivity original
        // Por enquanto, só mostra um toast
        Toast.makeText(this, "Permissão OK!", Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão OK!", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "Permissão necessária", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}

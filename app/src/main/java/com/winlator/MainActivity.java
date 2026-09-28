package com.winlator;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final byte PERMISSION_REQUEST_CODE = 1;
    private static final String PREF_CONTAINER_ID = "game_container_id";
    private static final String PREF_SETUP_DONE = "game_setup_done";

    // CONFIGURACAO DO JOGO
    private static final String GAME_EXE_NAME = "Freedom.exe";
    private static final String GAME_FOLDER = "Freedom/Game Files";
    private static final String GAME_SCREEN_SIZE = "1280x800";
    private static final String GAME_DRIVE_PATH = "/sdcard/Download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.setActivityTheme(this);
        setContentView(new View(this));

        if (!hasStoragePermission()) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
            return;
        }

        start();
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
                start();
            } else {
                Toast.makeText(this, "Permissao necessaria", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void start() {
        RootFSInstaller.installWithCallback(this, success -> {
            if (success) {
                setupAndLaunch();
            } else {
                Toast.makeText(this, "Falha ao instalar arquivos", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void setupAndLaunch() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int savedContainerId = prefs.getInt(PREF_CONTAINER_ID, -1);
        boolean setupDone = prefs.getBoolean(PREF_SETUP_DONE, false);

        ContainerManager manager = new ContainerManager(this);
        Container container = null;

        if (savedContainerId > 0 && setupDone) {
            container = manager.getContainerById(savedContainerId);
        }

        if (container == null) {
            if (manager.getContainers().isEmpty()) {
                container = createGameContainer(manager);
            } else {
                container = manager.getContainers().get(0);
            }

            if (container != null) {
                prefs.edit()
                    .putInt(PREF_CONTAINER_ID, container.id)
                    .putBoolean(PREF_SETUP_DONE, true)
                    .apply();
            }
        }

        if (container == null) {
            Toast.makeText(this, "Erro ao criar container", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        manager.activateContainer(container);

        File gameExe = findGameExe(container);
        if (gameExe == null) {
            Toast.makeText(this,
                "Jogo nao encontrado.\n\nColoque em:\n/sdcard/Download/Freedom/Game Files/Freedom.exe",
                Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", gameExe.getAbsolutePath());
        startActivity(intent);
        finish();
    }

    private Container createGameContainer(ContainerManager manager) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", "Freedom Fighters");
            data.put("screenSize", GAME_SCREEN_SIZE);
            data.put("graphicsDriver", "vortek,gladio");
            data.put("dxwrapper", "wine");
            data.put("audioDriver", "alsa");
            data.put("wincomponents", "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,vcrun2005=0,vcrun2010=1,wmdecoder=1");
            data.put("box64Preset", "COMPATIBILITY");
            data.put("drives", "D:" + GAME_DRIVE_PATH + ",E:/data/data/com.winlator/storage");
            data.put("envVars", "ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1");
            data.put("windowsVersion", "win7");
            return manager.createContainer(data);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private File findGameExe(Container container) {
        File[] paths = {
            new File("/sdcard/Download/" + GAME_FOLDER + "/" + GAME_EXE_NAME),
            new File("/storage/emulated/0/Download/" + GAME_FOLDER + "/" + GAME_EXE_NAME),
            new File("/sdcard/Download/Freedom/Game Files/Freedom.exe"),
            new File("/sdcard/Android/data/com.winlator/Game Files/Freedom.exe"),
            new File(container.getRootDir(), ".wine/drive_c/Freedom.exe"),
        };

        for (File f : paths) {
            if (f.exists()) return f;
        }

        File exe = findExeRecursive(new File("/sdcard/Download"), "freedom.exe", 0);
        return exe;
    }

    private File findExeRecursive(File dir, String exeName, int depth) {
        if (depth > 4 || dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase(exeName)) return f;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File result = findExeRecursive(f, exeName, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }
}

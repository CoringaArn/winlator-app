package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final boolean DEBUG_MODE = false;
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;

    private static final String PREF_CONTAINER_ID = "game_container_id";
    private static final String GAME_EXE_NAME = "Freedom.exe";
    private static final String GAME_FOLDER = "Freedom/Game Files";
    private static final String GAME_SCREEN_SIZE = "1280x800";
    private static final String GAME_DRIVE_PATH = "/sdcard/Download";

    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(new View(this));

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (!hasStoragePermission()) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
                PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
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
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                start();
            } else {
                Toast.makeText(this, "Permissao necessaria", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void start() {
        try {
            RootFS rootFS = RootFS.find(this);
            if (rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
                setupAndLaunch();
            } else {
                RootFSInstaller.installWithCallback(this, success -> {
                    if (success) {
                        setupAndLaunch();
                    } else {
                        Toast.makeText(this, "Falha ao instalar arquivos", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupAndLaunch() {
        try {
            final ContainerManager manager = new ContainerManager(this);

            int savedContainerId = preferences.getInt(PREF_CONTAINER_ID, -1);
            if (savedContainerId > 0) {
                Container saved = manager.getContainerById(savedContainerId);
                if (saved != null) {
                    launchWithContainer(saved, manager);
                    return;
                }
            }

            if (!manager.getContainers().isEmpty()) {
                Container container = manager.getContainers().get(0);
                preferences.edit().putInt(PREF_CONTAINER_ID, container.id).apply();
                launchWithContainer(container, manager);
                return;
            }

            Toast.makeText(this, "Criando container...", Toast.LENGTH_SHORT).show();

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

            manager.createContainerAsync(data, container -> {
                if (container == null) {
                    Toast.makeText(this, "Erro ao criar container", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                preferences.edit().putInt(PREF_CONTAINER_ID, container.id).apply();
                launchWithContainer(container, manager);
            });

        } catch (JSONException e) {
            Toast.makeText(this, "Erro JSON: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Erro setup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void launchWithContainer(Container container, ContainerManager manager) {
        try {
            manager.activateContainer(container);
        } catch (Exception e) {
            Toast.makeText(this, "Erro activate: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        File gameExe = findGameExe(container);
        if (gameExe == null) {
            Toast.makeText(this,
                "Jogo nao encontrado.\n\nProcurei em:\n/sdcard/Download/Freedom/Game Files/\n\nContainer ID: " + container.id,
                Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent intent = new Intent(this, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", gameExe.getAbsolutePath());
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Erro abrir jogo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File findGameExe(Container container) {
        File[] paths = {
            new File("/sdcard/Download/" + GAME_FOLDER + "/" + GAME_EXE_NAME),
            new File("/storage/emulated/0/Download/" + GAME_FOLDER + "/" + GAME_EXE_NAME),
            new File("/sdcard/Download/Freedom/Game Files/Freedom.exe"),
            new File(container.getRootDir(), ".wine/drive_c/Freedom.exe"),
        };

        for (File f : paths) {
            if (f.exists()) return f;
        }

        return findExeRecursive(new File("/sdcard/Download"), "freedom.exe", 0);
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

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return true;
    }

    public void showFragment(androidx.fragment.app.Fragment fragment) {
    }
}

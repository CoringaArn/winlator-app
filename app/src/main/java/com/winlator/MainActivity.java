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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final boolean DEBUG_MODE = false;
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;

    private static final String GAME_EXE_NAME = "Freedom.exe";
    private static final String GAME_FOLDER = "Freedom/Game Files";

    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;
    private Fragment currentFragment;

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
        // Verifica se o rootfs já está instalado
        try {
            com.winlator.xenvironment.RootFS rootFS = com.winlator.xenvironment.RootFS.find(this);
            if (rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
                // Já instalado → vai direto pro jogo
                setupAndLaunch();
            } else {
                // Precisa instalar
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
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupAndLaunch() {
        try {
            ContainerManager manager = new ContainerManager(this);

            if (manager.getContainers().isEmpty()) {
                Toast.makeText(this,
                    "Nenhum container encontrado.\n\nAbra o Winlator original e crie um container primeiro.",
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Container container = manager.getContainers().get(0);
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

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
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
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
        }
        super.onBackPressed();
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (editInputControls) {
            setResult(RESULT_OK);
            finish();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return true;
    }

    public void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();
        currentFragment = fragment;
    }
            }

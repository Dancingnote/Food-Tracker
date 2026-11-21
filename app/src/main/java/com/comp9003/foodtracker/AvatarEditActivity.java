package com.comp9003.foodtracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;

public class AvatarEditActivity extends AppCompatActivity {
    private static final int REQ_PICK_IMAGE = 5001;
    private static final int REQ_TAKE_PHOTO = 5002;
    private static final int REQ_CAMERA_PERMISSION = 5003;

    private ImageView ivPreview;
    private Uri pendingAvatarUri;
    private Uri beforeCameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        int ui = getWindow().getDecorView().getSystemUiVisibility();
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(ui);
        setContentView(R.layout.activity_avatar_edit);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ivPreview = findViewById(R.id.iv_avatar_preview);
        ImageView btnClose = findViewById(R.id.btn_close);
        LinearLayout rowPick = findViewById(R.id.row_pick_avatar);
        LinearLayout rowSave = findViewById(R.id.row_save_avatar);

        btnClose.setOnClickListener(v -> finish());
        rowPick.setOnClickListener(v -> showImagePicker());
        rowSave.setOnClickListener(v -> saveAvatar());

        String current = ProductRepository.getAvatarUri(this);
        if (current != null) {
            try {
                pendingAvatarUri = Uri.parse(current);
                ivPreview.setImageURI(pendingAvatarUri);
            } catch (Exception ignored) {
            }
        }
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void showImagePicker() {
        String[] options = new String[]{"Take photo", "Choose from gallery", "Cancel"};
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else if (which == 1) {
                        pickFromGallery();
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }

        Uri uri = createCameraOutputUri();
        if (uri == null) {
            Toast.makeText(this, "Unable to open camera", Toast.LENGTH_SHORT).show();
            return;
        }

        beforeCameraUri = pendingAvatarUri;
        pendingAvatarUri = uri;

        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
    }

    private Uri createCameraOutputUri() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = getFilesDir();
            File file = File.createTempFile("avatar_", ".jpg", dir);
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveAvatar() {
        if (pendingAvatarUri == null) {
            Toast.makeText(this, "Please select an avatar first", Toast.LENGTH_SHORT).show();
            return;
        }
        ProductRepository.setAvatarUri(this, pendingAvatarUri.toString());
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode != RESULT_OK || data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }

            pendingAvatarUri = uri;
            try {
                ivPreview.setImageURI(uri);
            } catch (Exception ignored) {
            }
            return;
        }

        if (requestCode == REQ_TAKE_PHOTO) {
            if (resultCode != RESULT_OK) {
                pendingAvatarUri = beforeCameraUri;
                beforeCameraUri = null;
                return;
            }
            beforeCameraUri = null;
            if (pendingAvatarUri == null) return;
            try {
                ivPreview.setImageURI(pendingAvatarUri);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto();
        }
    }
}

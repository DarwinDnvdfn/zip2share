package com.mohan.zip2share;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import android.widget.TextView;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.os.Build;
import android.view.View;
import android.graphics.Color;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_SHARE = 1001;
  private File tempZipFile;
  private ProgressDialog progressDialog; // Add the progress dialog
  private TextView textView;

  private void updateText(String message) { // update text
    runOnUiThread(() -> {
      if (textView != null) {
        textView.setText(message);
      }
    });
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    textView = findViewById(R.id.textView);
    clearAppData();

    // Make status bar transparent
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
      getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    // Initialize the ProgressDialog
    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage("Processing file...");
    progressDialog.setCancelable(false); // Don't allow cancel

    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();

    if (Intent.ACTION_SEND.equals(action) && type != null) {
      Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (fileUri != null) {
        handleSendFile(fileUri);
      } else {
        finish();
      }
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
      ArrayList < Uri > fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (fileUris != null && !fileUris.isEmpty()) {
        handleSendMultipleFiles(fileUris);
      } else {
        finish();
      }
    }
  }

  private void handleSendFile(Uri fileUri) {
    List < Uri > uriList = new ArrayList < > ();
    uriList.add(fileUri);
    createZipFromFiles(uriList);
  }

  private void handleSendMultipleFiles(List < Uri > fileUris) {
    createZipFromFiles(fileUris);
  }

  private void createZipFromFiles(List < Uri > fileUris) {
    // Start the archiving process on a background thread
    new Thread(() -> {
      // Post to the UI thread to show the ProgressDialog
      runOnUiThread(() -> {
        progressDialog.show();
        updateText("Zipping files, please wait...");
      });

      try {
        String zipFileName = "zip2share_" + System.currentTimeMillis() + ".zip";
        tempZipFile = new File(getCacheDir(), zipFileName);
        FileOutputStream fos = new FileOutputStream(tempZipFile);
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));
        byte[] buffer = new byte[1024];

        for (Uri uri: fileUris) {
          InputStream is = getContentResolver().openInputStream(uri);
          if (is == null) continue;

          String fileName = getFileName(uri);
          if (fileName == null) {
            fileName = "file_" + System.currentTimeMillis();
          }

          zos.putNextEntry(new ZipEntry(fileName));
          int count;
          BufferedInputStream bis = new BufferedInputStream(is);
          while ((count = bis.read(buffer)) != -1) {
            zos.write(buffer, 0, count);
          }
          zos.closeEntry();
          bis.close();
        }
        zos.close();

        // After successful archiving, update the UI on the main thread
        runOnUiThread(() -> {
          progressDialog.dismiss();
          updateText("ZIP ready! Launching share...");
          shareZipFile();
        });
      } catch (IOException e) {
        e.printStackTrace();
        runOnUiThread(() -> {
          progressDialog.dismiss();
          finish();
        });
      }
    }).start();
  }

  private String getFileName(Uri uri) {
    String result = null;
    if ("content".equals(uri.getScheme())) {
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (index != -1) result = cursor.getString(index);
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }
    if (result == null) {
      result = uri.getLastPathSegment();
    }
    return result;
  }

  private void shareZipFile() {
    Uri contentUri = FileProvider.getUriForFile(
      this,
      getPackageName() + ".provider",
      tempZipFile
    );

    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("application/zip");
    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    try {
      startActivityForResult(
        Intent.createChooser(shareIntent, "Share ZIP Archive"),
        REQUEST_SHARE
      );
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "No app found to share ZIP", Toast.LENGTH_SHORT).show();
      if (tempZipFile != null && tempZipFile.exists()) {
        tempZipFile.delete();
      }
      finish();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_SHARE) {
      if (tempZipFile != null && tempZipFile.exists()) {
        boolean deleted = tempZipFile.delete();
        if (!deleted) {
          new Handler().postDelayed(() -> {
            if (tempZipFile.exists()) {
              tempZipFile.delete();
            }
          }, 5000);
        }
      }
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void clearAppData() {
    try {
      File dir = new File(getApplicationInfo().dataDir);
      if (dir.isDirectory()) {
        for (File child: dir.listFiles()) {
          deleteDir(child);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean deleteDir(File dir) {
    if (dir.isDirectory()) {
      for (File child: dir.listFiles()) {
        deleteDir(child);
      }
    }
    return dir.delete();
  }

  @Override
  protected void onDestroy() {
    clearAppData();
    super.onDestroy();
  }
}
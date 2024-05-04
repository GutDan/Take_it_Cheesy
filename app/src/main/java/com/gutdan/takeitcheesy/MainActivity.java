package com.gutdan.takeitcheesy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<Intent> camActivityResultLauncher;
    String currentPhotoPath;

    private File photoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        deleteAllImages();

        this.camActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();


                        // launch selection activity
                        /*
                        Intent intent = new Intent(getApplicationContext(), SelectRegionActivity.class);
                        intent.putExtra("image path", currentPhotoPath);
                        startActivity(intent);
                        */
                        Intent intent = new Intent(getApplicationContext(), SelectSkewRegionActivity.class);
                        intent.putExtra("image path", currentPhotoPath);
                        startActivity(intent);

                        //ImageView iv_test = (ImageView) findViewById(R.id.imageView);
                        //iv_test.setImageBitmap(BitmapFactory.decodeFile(currentPhotoPath));

                    }
                }
            }
        );






        Button bt_cam = (Button) findViewById(R.id.open_cam);
        bt_cam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            this.photoFile = null;
            try {
                this.photoFile = createImageFile();
            } catch (IOException ex) {
                // todo
            }
            // Continue only if the File was successfully created
            if (this.photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        this.getApplicationContext().getPackageName() + ".fileprovider",
                        this.photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                camActivityResultLauncher.launch(takePictureIntent);
                //startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void deleteAllImages() {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            Log.e("MainActivity", "cant get Environment.DIRECTORY_PICTURES");
            return;
        }
        for (File f: storageDir.listFiles()) {
            f.delete();
        }
    }

    private File createImageFile() throws IOException {
        String imageFileName = "lastpicture";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".png", storageDir);
        image.deleteOnExit();

        this.currentPhotoPath = image.getAbsolutePath(); //save path
        return image;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.photoFile != null) {
            this.photoFile.delete();
        }
    }
}
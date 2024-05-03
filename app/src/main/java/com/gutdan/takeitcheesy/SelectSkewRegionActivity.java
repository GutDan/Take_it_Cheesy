package com.gutdan.takeitcheesy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SelectSkewRegionActivity extends AppCompatActivity {

    private ImageView iv_pic, iv_pointer_tl, iv_pointer_bl, iv_pointer_tr, iv_pointer_br;
    private ScrollListener scrollListener_tl, scrollListener_bl, scrollListener_tr, scrollListener_br;
    private Matrix matrix;

    private Bitmap bm_iv_pic;
    private ConstraintLayout cl_gesture;

    private Button bt_back, bt_next;

    private int orientation = ExifInterface.ORIENTATION_NORMAL;


    private String image_path;

    private static final int MAX_SIZE = 100 * 1024 * 1024; // 100 MB limit from core.java.android.view.DisplayListCanvas (private)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_select_skew_region);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        this.cl_gesture = findViewById(R.id.gesture);
        this.iv_pic = findViewById(R.id.image);

        // load captured image
        this.image_path = getIntent().getStringExtra("image path");
        try {
            this.bm_iv_pic = BitmapFactory.decodeFile(image_path);

            // i would expect a phones memory should be able to handle any picture from its camera
            // but better safe than sorry
            if (this.bm_iv_pic.getByteCount() > MAX_SIZE) {
                Log.e("SelectRegionActivity", "phone captured bigger image than memory can handle (" + this.bm_iv_pic.getByteCount() + " bytes)");
                // factor to make it fit
                double factor = Math.sqrt( (double) MAX_SIZE / this.bm_iv_pic.getAllocationByteCount() ) * 0.9;
                // scale by factor
                this.bm_iv_pic = Bitmap.createScaledBitmap(
                        this.bm_iv_pic,
                        (int) (this.bm_iv_pic.getWidth() * factor),
                        (int) (this.bm_iv_pic.getHeight() * factor),
                        false
                );
                //check again
                if (this.bm_iv_pic.getByteCount() > MAX_SIZE) {
                    throw new RuntimeException(); // should be unreachable
                }
            }
            iv_pic.setImageBitmap(this.bm_iv_pic);
        } catch (Exception | Error e) { //cant really test this with my phone :|
            throw new RuntimeException("file too big");
        }
        iv_pic.setScaleType(ImageView.ScaleType.MATRIX);
        matrix = iv_pic.getMatrix();

        //center, scale and rotate
        cl_gesture.post(new Runnable() {
            @Override
            public void run() {
                resetPicOrientation();
            }
        });

        //drag and drop
        this.iv_pointer_tl = findViewById(R.id.pointer_tl);
        this.iv_pointer_bl = findViewById(R.id.pointer_bl);
        this.iv_pointer_tr = findViewById(R.id.pointer_tr);
        this.iv_pointer_br = findViewById(R.id.pointer_br);

        this.scrollListener_tl = new ScrollListener(this.iv_pointer_tl);
        this.scrollListener_bl = new ScrollListener(this.iv_pointer_bl);
        this.scrollListener_tr = new ScrollListener(this.iv_pointer_tr);
        this.scrollListener_br = new ScrollListener(this.iv_pointer_br);

        //noinspection AndroidLintClickableViewAccessibility
        this.iv_pointer_tl.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scrollListener_tl.handleEvent(event);
                return true;
            }
        });
        //noinspection AndroidLintClickableViewAccessibility
        this.iv_pointer_bl.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scrollListener_bl.handleEvent(event);
                return true;
            }
        });
        //noinspection AndroidLintClickableViewAccessibility
        this.iv_pointer_tr.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scrollListener_tr.handleEvent(event);
                return true;
            }
        });
        //noinspection AndroidLintClickableViewAccessibility
        this.iv_pointer_br.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scrollListener_br.handleEvent(event);
                return true;
            }
        });

        // title bar buttons
        this.bt_back = findViewById(R.id.back);
        this.bt_next = findViewById(R.id.next);
        this.bt_next.setOnClickListener(v -> nextScreen());
        this.bt_back.setOnClickListener(v -> finish());
    }

    private void resetPicOrientation() { //reset picture to center and sets default rotation
        iv_pic.setImageMatrix(null);
        this.matrix = iv_pic.getMatrix();

        int iHeight = iv_pic.getDrawable().getIntrinsicHeight();
        int iWidth = iv_pic.getDrawable().getIntrinsicWidth();
        int gHeight = cl_gesture.getHeight();
        int gWidth = cl_gesture.getWidth();

        //center
        this.matrix.postTranslate(-iWidth/2f, -iHeight/2f);
        this.matrix.postTranslate(gWidth/2f, gHeight/2f);



        iv_pic.setImageMatrix(this.matrix);

        //scale
        float scaleFactor = 1;
        //rotation
        try {
            if (image_path != null) {
                ExifInterface ei = new ExifInterface(image_path);
                this.orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

                switch (this.orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90, gWidth/2f, gHeight/2f);
                        scaleFactor = Math.min(((float) gWidth)/iHeight, ((float) gHeight)/iWidth);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180, gWidth/2f, gHeight/2f);
                        scaleFactor = Math.min(((float) gWidth)/iWidth, ((float) gHeight)/iHeight);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270, gWidth/2f, gHeight/2f);
                        scaleFactor = Math.min(((float) gWidth)/iHeight, ((float) gHeight)/iWidth);
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        scaleFactor = Math.min(((float) gWidth)/iWidth, ((float) gHeight)/iHeight);
                        break;
                }

                matrix.postScale(scaleFactor, scaleFactor, gWidth/2f, gHeight/2f);
                iv_pic.setImageMatrix(matrix);
            }
        } catch (IOException ignored) {}
    }

    private Bitmap cutOutPic() {
        try {
            Matrix inv_matrix = new Matrix();
            this.matrix.invert(inv_matrix); // fun (affine) linear algebra is about to happen

            float[] src = new float[]{
                this.iv_pointer_tl.getX(),
                this.iv_pointer_tl.getY(),
                this.iv_pointer_tr.getX()+iv_pointer_tr.getWidth(),
                this.iv_pointer_tr.getY(),
                this.iv_pointer_br.getX()+iv_pointer_br.getWidth(),
                this.iv_pointer_br.getY(),
                this.iv_pointer_bl.getX(),
                this.iv_pointer_bl.getY(),
            };
            inv_matrix.mapPoints(src); //map to original (inverse) image space

            //get dpi independent values of the hexagrid drawable (should be 924 1000)
            float density = getApplicationContext().getResources().getDisplayMetrics().density;
            int intrinsicWidth = (int) (getDrawable(R.drawable.hexagrid).getIntrinsicWidth() / density);
            int intrinsicHeight = (int) (getDrawable(R.drawable.hexagrid).getIntrinsicHeight() / density);


            // prepare canvas
            Bitmap ret = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(ret);
            canvas.drawColor(0xff010101); //color of out of bounds space

            // crop the selection
            Matrix skewMatrix = new Matrix();

            float[] dst = new float[]{58, 198, 866, 198, 866, 801, 58, 801};
            skewMatrix.setPolyToPoly(src,0, dst, 0, 4);


            canvas.drawBitmap(this.bm_iv_pic, skewMatrix, null);

            // 100 MB limit from core.java.android.view.DisplayListCanvas
            // should never happen but will not be caught in this try as error happens in an ImageView later
            if (ret.getByteCount() > MAX_SIZE) {
                throw new RuntimeException("Canvas: trying to draw too large bitmap.");
            }
            return ret;
        } catch (OutOfMemoryError | RuntimeException e) {
            Log.e("GD","aaaaaaaaaaaaaaaaaaaaaaaaa");
            Toast.makeText(getApplicationContext(), "Zoomed in too much",Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void nextScreen() {
        Bitmap cutout_bm = cutOutPic();
        if (cutout_bm != null) {
            // save cut out bm
            try {
                String imageFileName = "cutout";
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File image = File.createTempFile(imageFileName, ".png", storageDir);
                FileOutputStream fos = new FileOutputStream(image);
                cutout_bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                cutout_bm.recycle();
                image.deleteOnExit(); //todo onDestroy

                // launch calibration activity
                Intent intent = new Intent(getApplicationContext(), CalibrateColorActivity.class);
                intent.putExtra("cut path", image.getAbsolutePath()); //pass path
                startActivity(intent);
            } catch (IOException ex) {
                Log.e("SelectSkewRegionActivity", "IOExeption: cant save file");
                Toast.makeText(getApplicationContext(), "IOExeption: cant save file",Toast.LENGTH_SHORT).show();
            }
        }
    }

    // from here: gesture controls
    private class ScrollListener {

        ImageView iv;
        private int dx, dy;

        public ScrollListener(ImageView iv) {
            this.iv = iv;
        }

        public void handleEvent(MotionEvent event) {
            final int x = (int) event.getRawX();
            final int y = (int) event.getRawY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    ViewGroup.MarginLayoutParams lParams = (ViewGroup.MarginLayoutParams) this.iv.getLayoutParams();
                    dx = x - lParams.leftMargin;
                    dy = y - lParams.topMargin;
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    break;
                case MotionEvent.ACTION_MOVE:
                    ViewGroup.MarginLayoutParams lParams_ = (ViewGroup.MarginLayoutParams) this.iv.getLayoutParams();
                    lParams_.leftMargin = Math.max(Math.min(x - dx, cl_gesture.getWidth()-this.iv.getWidth()),0);
                    lParams_.topMargin = Math.max(Math.min(y - dy, cl_gesture.getHeight()-this.iv.getHeight()),0);
                    this.iv.setLayoutParams(lParams_);
                    break;
            }
        }
    }
}
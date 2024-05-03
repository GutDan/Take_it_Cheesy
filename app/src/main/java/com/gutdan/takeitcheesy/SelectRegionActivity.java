package com.gutdan.takeitcheesy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SelectRegionActivity extends AppCompatActivity {

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Matrix matrix;
    private ImageView iv_pic, iv_hexagrid;

    private Bitmap bm_iv_pic;
    private ConstraintLayout cl_gesture;

    private int orientation = ExifInterface.ORIENTATION_NORMAL;

    private String image_path;

    private static final int MAX_SIZE = 100 * 1024 * 1024; // 100 MB limit from core.java.android.view.DisplayListCanvas (private)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_select_region);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        this.cl_gesture = findViewById(R.id.gesture);
        this.iv_hexagrid = findViewById(R.id.hexagrid);
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

        //center and rotate
        cl_gesture.post(new Runnable() {
            @Override
            public void run() {
                resetPicOrientation();
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        gestureDetector = new GestureDetector(this, new ScrollListener());

        //noinspection AndroidLintClickableViewAccessibility
        this.cl_gesture.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

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

        //rotation
        try {
            if (image_path != null) {
                ExifInterface ei = new ExifInterface(image_path);
                this.orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

                switch (this.orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90, gWidth/2f, gHeight/2f);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180, gWidth/2f, gHeight/2f);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270, gWidth/2f, gHeight/2f);
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        break;
                }
                iv_pic.setImageMatrix(matrix);
            }
        } catch (IOException ignored) {}
    }

    private Bitmap cutOutPic() {
        try {
            Matrix inv_matrix = new Matrix();
            this.matrix.invert(inv_matrix); // fun (affine) linear algebra is about to happen

            RectF rect_hexagrid = new RectF( //rectangle of selection on screen space
                    this.iv_hexagrid.getX(),
                    this.iv_hexagrid.getY(),
                    this.iv_hexagrid.getWidth() + this.iv_hexagrid.getX(),
                    this.iv_hexagrid.getHeight() + this.iv_hexagrid.getY()
            );
            inv_matrix.mapRect(rect_hexagrid); //rectangle of selection on original (inverse) image space

            //figure out rotated dimensions
            int rotatedWidth, rotatedHeight;
            //get dpi independent values of the hexagrid drawable (should be 924 1000)
            float density = getApplicationContext().getResources().getDisplayMetrics().density;
            int intrinsicWidth = (int) (getDrawable(R.drawable.hexagrid).getIntrinsicWidth() / density);
            int intrinsicHeight = (int) (getDrawable(R.drawable.hexagrid).getIntrinsicHeight() / density);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotatedWidth = intrinsicHeight;
                    rotatedHeight = intrinsicWidth;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotatedWidth = intrinsicWidth;
                    rotatedHeight = intrinsicHeight;
                    break;
            }

            // prepare canvas with correct (but possibly rotated) dimensions
            Bitmap bm_new = Bitmap.createBitmap(rotatedWidth, rotatedHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm_new);
            canvas.drawColor(0xff010101); //color of out of bounds space

            // crop the selection
            Rect src = new Rect();
            rect_hexagrid.round(src);
            Rect dst = new Rect(0, 0, rotatedWidth, rotatedHeight);
            canvas.drawBitmap(this.bm_iv_pic, src, dst, null); // this scales back to same value as screen space

            // lastly: rotation required to reach screen space again
            Matrix matrixBack = new Matrix();
            switch (this.orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrixBack.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrixBack.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrixBack.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    break;
            }
            Bitmap ret = Bitmap.createBitmap(bm_new, 0, 0, bm_new.getWidth(), bm_new.getHeight(), matrixBack, true); //rotation

            bm_new.recycle(); //free memory

            // 100 MB limit from core.java.android.view.DisplayListCanvas
            // should never happen but will not be caught in this try as error happens in an ImageView later
            if (ret.getByteCount() > MAX_SIZE) {
                throw new RuntimeException("Canvas: trying to draw too large bitmap.");
            }
            return ret;
        } catch (OutOfMemoryError | RuntimeException e) {
            Toast.makeText(getApplicationContext(), "Zoomed in too much",Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    // from here: gesture controls
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            Matrix scaleMatrix = new Matrix();
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            scaleMatrix.postTranslate(-focusX, -focusY); //move center
            scaleMatrix.postScale(detector.getScaleFactor(), detector.getScaleFactor()); //do scaling
            scaleMatrix.postTranslate(focusX, focusY); //move center back
            matrix.postConcat(scaleMatrix);
            iv_pic.setImageMatrix(matrix);
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
        }
    }

    private class ScrollListener implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            matrix.postTranslate(-distanceX, -distanceY);
            iv_pic.setImageMatrix(matrix);
            return true;
        }

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            return false;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            return false;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
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
                    Log.e("SelectRegionActivity", "IOExeption: cant save file");
                    Toast.makeText(getApplicationContext(), "IOExeption: cant save file",Toast.LENGTH_SHORT).show();
                }
            }

            //resetPicOrientation();
            return false;
        }

        @Override
        public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
            return false;
        }
    }
}
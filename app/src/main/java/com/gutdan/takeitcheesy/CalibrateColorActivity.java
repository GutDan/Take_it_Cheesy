package com.gutdan.takeitcheesy;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class CalibrateColorActivity extends AppCompatActivity {

    private Bitmap cut_bm;

    private ImageView iv_cut;

    private ConstraintLayout cl_buttonarray;

    private Button[][] bts_cal;

    private ProgressBar pb_cal;

    private TextView tv_cal;

    private Button bt_back, bt_next, bt_undo, bt_redo;

    private int progress;
    private int[][] calibrationSquares;


    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_calibrate_color);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // title bar buttons
        this.bt_back = findViewById(R.id.back);
        this.bt_next = findViewById(R.id.next);
        this.bt_next.setOnClickListener(v -> nextScreen());
        this.bt_back.setOnClickListener(v -> finish());

        try {
            this.cut_bm = BitmapFactory.decodeFile(getIntent().getStringExtra("cut path"));
        } catch (Exception e) {
            Log.e("CalibrateColorActivity", "IOExeption: cant load file");
        }
        if (this.cut_bm == null) { //todo remove
            this.cut_bm = BitmapFactory.decodeResource(getResources(), R.drawable.testimagecrop);
        }


        this.iv_cut = findViewById(R.id.cutout);
        if (this.cut_bm != null) {
            this.iv_cut.setImageBitmap(this.cut_bm);
        }

        this.cl_buttonarray = findViewById(R.id.buttonarray);

        //init Button array
        this.iv_cut.post(new Runnable() {
            @Override
            public void run() {
                initButtonArray(); // needs dimensions
            }
        });

        // progress text and bar
        this.pb_cal = findViewById(R.id.calib_progress);
        this.tv_cal = findViewById(R.id.text_progress);

        pb_cal.setMax(9);
        resetProgress();

        // undo redo buttons
        this.bt_undo = findViewById(R.id.undo);
        this.bt_redo = findViewById(R.id.redo);

        this.bt_redo.setOnClickListener(v -> resetProgress());

        this.bt_undo.setOnClickListener(v -> undoProgress());
    }

    private void initButtonArray() {
        bts_cal = new Button[5][5];

        /* hexagonal coordinates in 2d array
        l<-------------     -------------->r
                        0,0
                    0,1     1,0
                0,2     1,1     2,0
            ...     1,2     2,1     ...
        ...     1,3     2,2     3,1     ...
            ...     2,3     3,2     ...
                2,4     3,3     4,2
                    3,4     4,3
                        4,4
         */

        // get dimensions
        final int TOTALWIDTH = iv_cut.getWidth();
        final int TOTALHEIGHT = iv_cut.getHeight();
        // geometric constants of hexagonal grid
        final int HORIZOFFSET = (int) (174f/924 * TOTALWIDTH);
        final int BUTTONWIDTH = (int) (0.2f * TOTALHEIGHT);
        final int BUTTONHEIGHT = (int) (0.2f * TOTALHEIGHT);
        final int TILEWIDTH = (int) (229f/924 * TOTALWIDTH);

        for (int l = 0; l <= 4; l++) {
            for (int r = Math.max(0, l-2); r <= Math.min(4, 2+l); r++) {
                Button bt_cur = new Button(getApplicationContext());
                bt_cur.setBackground(getDrawable(R.drawable.circle));
                this.cl_buttonarray.addView(bt_cur, 0);

                // set constrains
                ConstraintLayout.LayoutParams cParams = new ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.WRAP_CONTENT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                );
                cParams.topToTop = R.id.cutout;
                cParams.leftToLeft = R.id.cutout;
                bt_cur.setLayoutParams(cParams);

                // set position
                ViewGroup.MarginLayoutParams mParams = (ViewGroup.MarginLayoutParams) bt_cur.getLayoutParams();
                mParams.setMargins((TOTALWIDTH-BUTTONWIDTH)/2+HORIZOFFSET*(r-l), BUTTONHEIGHT*(l+r)/2,0,0);
                bt_cur.setLayoutParams(mParams);

                // set dimensions
                ViewGroup.LayoutParams lParams = bt_cur.getLayoutParams();
                lParams.height = BUTTONHEIGHT;
                lParams.width = BUTTONWIDTH;
                bt_cur.setLayoutParams(lParams);

                // set text properties
                bt_cur.setTextColor(0xffffffff);


                // set on click
                int curL = l;
                int curR = r;
                bt_cur.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (progress < 9) {
                            calibrationSquares[progress] = new int[]{curL, curR};
                            progress++;
                            bt_cur.setText(bt_cur.getText().toString() + progress);
                            updateProgress();
                        }

                        //todo remove
                        // get dimensions
                        final int TOTALWIDTH2 = cut_bm.getWidth();
                        final int TOTALHEIGHT2 = cut_bm.getHeight();
                        // geometric constants of hexagonal grid
                        final int HORIZOFFSET2 = (int) (174f/924 * TOTALWIDTH2);
                        final int TILEHEIGHT2 = (int) (0.2f * TOTALHEIGHT2);
                        final int TILEWIDTH2 = (int) (229f/924 * TOTALWIDTH2);

                        Bitmap bm_curr = Bitmap.createBitmap(TILEWIDTH2, TILEHEIGHT2, Bitmap.Config.ARGB_8888);
                        Canvas c = new Canvas(bm_curr);
                        c.drawBitmap(
                                cut_bm,
                                -((TOTALWIDTH2-TILEWIDTH2)/2f+HORIZOFFSET2*(curR-curL)),
                                -TILEHEIGHT2*(curL+curR)/2f,
                                null
                        );

                        //((ImageView) findViewById(R.id.testimage)).setImageBitmap(bm_curr);


                    }
                });

                bts_cal[l][r] = bt_cur;
            }
        }
    }

    private void updateProgress() {
        if (progress < 9) {
            tv_cal.setText(String.format(getString(R.string.click_on_a), progress+1));
            bt_next.setText(getString(R.string.skip));
        } else {
            tv_cal.setText(getString(R.string.done));
            bt_next.setText(getString(R.string.next));
        }
        pb_cal.setProgress(progress, true);
    }

    private void resetProgress() {
        if (this.calibrationSquares != null) {
            for (int[] bt: this.calibrationSquares) {
                if (bt != null) {
                    this.bts_cal[bt[0]][bt[1]].setText("");
                }
            }
        }
        this.calibrationSquares = new int[9][2];
        this.progress = 0;
        updateProgress();
    }

    private void undoProgress() {
        if (this.progress >= 1) {
            Button bt_last = this.bts_cal[this.calibrationSquares[this.progress-1][0]][this.calibrationSquares[this.progress-1][1]];
            bt_last.setText(bt_last.getText().toString().substring(0, bt_last.getText().toString().length()-1)); // :)
            this.calibrationSquares[this.progress-1] = null;
            this.progress--;
            updateProgress();
        }
    }

    private void nextScreen() {
        //Log.e("GD", String.valueOf(Runtime.getRuntime().availableProcessors()));
        if (this.progress != 9) {
            Log.e("GD","skip");
            return;
        }

        //color detection

        // get dimensions
        final int TOTALWIDTH = cut_bm.getWidth();
        final int TOTALHEIGHT = cut_bm.getHeight();
        // geometric constants of hexagonal grid
        final int HORIZOFFSET = (int) (174f/924 * TOTALWIDTH);
        final int TILEHEIGHT = (int) (0.2f * TOTALHEIGHT);
        final int TILEWIDTH = (int) (229f/924 * TOTALWIDTH);

        ColorDetector[][] cds = new ColorDetector[5][5];
        Color[] cCalib = new Color[9];

        // initialize ColorDetector and get cCalib
        for (int l = 0; l <= 4; l++) {
            for (int r = Math.max(0, l-2); r <= Math.min(4, 2+l); r++) {
                /*
                Bitmap bm_curr = Bitmap.createBitmap(this.cut_bm,
                        (TOTALWIDTH-TILEWIDTH)/2+HORIZOFFSET*(r-l),
                        TILEHEIGHT*(l+r)/2,
                        TILEWIDTH,
                        TILEHEIGHT
                );*/
                Bitmap bm_curr = Bitmap.createBitmap(TILEWIDTH, TILEHEIGHT, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm_curr);
                c.drawBitmap(
                    this.cut_bm,
                    -((TOTALWIDTH-TILEWIDTH)/2f+HORIZOFFSET*(r-l)),
                    -TILEHEIGHT*(l+r)/2f,
                    null
                );

                ColorDetector cd_curr = new ColorDetector(bm_curr);
                cds[l][r] = cd_curr;
                cd_curr.calculateAverageColors();

                //get calibration from selected tiles
                for (int i = 1; i <= this.calibrationSquares.length; i++) {
                    int[] coords = this.calibrationSquares[i-1];
                    if (l == coords[0] && r == coords[1]) {
                        if (i == 1 || i == 5 || i == 9) {
                            cCalib[i-1] = cd_curr.getColorForCalib(ColorDetector.Dir.VERT);
                        } else if (i == 2 || i == 6 || i == 7) {
                            cCalib[i-1] = cd_curr.getColorForCalib(ColorDetector.Dir.POSD);
                        } else {  // 3, 4, 8
                            cCalib[i-1] = cd_curr.getColorForCalib(ColorDetector.Dir.NEGD);
                        }
                    }
                }
            }
        }

        // todo write cCalib to file

        int[][] vert = new int[5][5];
        int[][] posd = new int[5][5];
        int[][] negd = new int[5][5];


        // set calibration and get tiles
        for (int l = 0; l <= 4; l++) {
            for (int r = Math.max(0, l-2); r <= Math.min(4, 2+l); r++) {
                cds[l][r].setCalibration(cCalib);
                int[] vpn = cds[l][r].getTile();
                vert[l][r] = vpn[0];
                posd[l][r] = vpn[1];
                negd[l][r] = vpn[2];
                this.bts_cal[l][r].setText(Arrays.toString(vpn));
            }
        }

        // score calculation
        int acc = 0;
        int curNum;

        // vert
        lineLoop:
        for (int d = -2; d <= 2; d++) {
            int lineNum = 0;
            int a;
            for(a = 0; a < 5-Math.abs(d); a++) {
                curNum = vert[Math.max(0,-d)+a][Math.max(0, d)+a];
                if (lineNum == 0) {
                    lineNum = curNum;
                } else if (lineNum != curNum) {
                    continue lineLoop;
                }
            }
            acc += a * lineNum;
        }

        //posd
        lineLoop:
        for (int r = 0; r <= 4; r++) {
            int lineNum = 0;
            int a = 0;
            for (int l = Math.max(0, r-2); l <= Math.min(4, 2+r); l++) {
                a++;
                curNum = posd[l][r];
                if (lineNum == 0) {
                    lineNum = curNum;
                } else if (lineNum != curNum) {
                    continue lineLoop;
                }
            }
            acc += a * lineNum;
        }

        //negd
        lineLoop:
        for (int l = 0; l <= 4; l++) {
            int lineNum = 0;
            int a = 0;
            for (int r = Math.max(0, l-2); r <= Math.min(4, 2+l); r++) {
                a++;
                curNum = negd[l][r];
                if (lineNum == 0) {
                    lineNum = curNum;
                } else if (lineNum != curNum) {
                    continue lineLoop;
                }
            }
            acc += a * lineNum;
        }

        this.tv_cal.setText(String.format("Score: %d", acc));

        Log.e("GD","next");
    }
}
package com.gutdan.takeitcheesy;

import android.graphics.Bitmap;

import java.util.Arrays;

public class ColorDetector {

    public enum Dir {
        VERT,
        POSD,
        NEGD;
    }

    //downscaling
    private static final int WIDTH = 100;
    private static final int HEIGHT = 87;
    private static final int TL_X = 28;
    private static final int TL_Y = 30;
    private static final int TR_X = 73;
    private static final int TR_Y = 34;
    private static final int BM_X = 50;
    private static final int BM_Y = 72;
    private static final int CUTOUT_RADIUS = 10;

    private static final int[] sVert = {1, 5, 9};
    private static final int[] sPosD = {2, 6, 7};
    private static final int[] sNegD = {3, 4, 8};

    private Color[] cVert, cPosD, cNegD; // vertical, positive/negative diagonal
    //{new Color(0xaaa7b5), new Color(0x068d9e), new Color(0xebcb67)};
    //{new Color(0xdfbacf), new Color(0xc42e34), new Color(0xa7b764)};
    //{new Color(0xdc76b3), new Color(0x04a6d6), new Color(0xe7912a)};

    private Bitmap bm_tl, bm_tr, bm_bm;  // top left/right, bottom mid

    private Color[] colors;

    public ColorDetector(Bitmap bm_unscaled) {
        Bitmap bm_scaled = Bitmap.createScaledBitmap(bm_unscaled, WIDTH, HEIGHT, false);
        bm_unscaled.recycle();

        this.bm_tl = Bitmap.createBitmap(bm_scaled, TL_X-CUTOUT_RADIUS, TL_Y-CUTOUT_RADIUS, 2*CUTOUT_RADIUS, 2*CUTOUT_RADIUS);
        this.bm_tr = Bitmap.createBitmap(bm_scaled, TR_X-CUTOUT_RADIUS, TR_Y-CUTOUT_RADIUS, 2*CUTOUT_RADIUS, 2*CUTOUT_RADIUS);
        this.bm_bm = Bitmap.createBitmap(bm_scaled, BM_X-CUTOUT_RADIUS, BM_Y-CUTOUT_RADIUS, 2*CUTOUT_RADIUS, 2*CUTOUT_RADIUS);

        bm_scaled.recycle();
    }

    public void calculateAverageColors() {
        if (this.bm_bm == null) {
            throw new RuntimeException("calculateAverageColors was called for a second time");
        }
        this.colors = new Color[]{
            getAverageRGBthresholdedL(this.bm_bm),
            getAverageRGBthresholdedL(this.bm_tr),
            getAverageRGBthresholdedL(this.bm_tl)
        };
        this.bm_bm.recycle();
        this.bm_tr.recycle();
        this.bm_tl.recycle();
    }

    public Color getColorForCalib(Dir dir) {
        return this.colors[dir.ordinal()];
    }

    public void setCalibration(Color[] cCalib) {
        // if only java had actually usable functional programming tools ._.
        this.cVert = Arrays.stream(sVert).mapToObj(v -> cCalib[v-1]).toArray(Color[]::new);
        this.cPosD = Arrays.stream(sPosD).mapToObj(v -> cCalib[v-1]).toArray(Color[]::new);
        this.cNegD = Arrays.stream(sNegD).mapToObj(v -> cCalib[v-1]).toArray(Color[]::new);
    }

    public int[] getTile() { // returns the detected tile in vert posd negd order
        if (cVert == null || colors == null) {
            throw new RuntimeException("call calculateAverageColors and setCalibration first");
        }
        // alonzo church rotating in his grave right now
        return Arrays.stream(new int[]{0,1,2}).map(v -> getClosestColor(this.colors[v], Dir.values()[v])).toArray();
    }

    private static double getAverageL(Bitmap bm_cutout) {  // calculate average luminance
        double acc = 0;
        Color c;
        int height = bm_cutout.getHeight();
        int width = bm_cutout.getWidth();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                c = new Color(bm_cutout.getPixel(i,j));
                acc += calcLuminance(c.r, c.g, c.b);
            }
        }
        return acc/(height*width);
    }

    private static double calcLuminance(int r, int g, int b) {
        return r/255d * 0.2126d + g/255d * 0.7152d + b/255d * 0.0722d; // technically wrong. values should be linearized first
    }

    private static Color getAverageRGBthresholdedL(Bitmap bm_cutout) {  // get average color of all pixels with sufficient luminance
        double FACTOR_DELTA = 0.9; // give some leeway

        int accR = 0;
        int accG = 0;
        int accB = 0;
        int accCount = 0;

        int height = bm_cutout.getHeight();
        int width = bm_cutout.getWidth();

        double L = getAverageL(bm_cutout);
        Color c;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                c = new Color(bm_cutout.getPixel(i,j));
                //threshold
                if (calcLuminance(c.r, c.g, c.b) >= FACTOR_DELTA * L) {
                    accCount++;
                    accR += c.r;
                    accG += c.g;
                    accB += c.b;
                }
            }
        }

        return new Color(accR / accCount, accG / accCount, accB / accCount);
    }

    private static double distRGB(Color c1, Color c2) {
        return Math.sqrt(
            Math.pow(c1.r-c2.r, 2) +
            Math.pow(c1.g-c2.g, 2) +
            Math.pow(c1.b-c2.b, 2)
        );
    }

    private static double distHSV(Color c1, Color c2) {
        c1.calcHSV();
        c2.calcHSV();

        double dH = Math.min((c1.h-c2.h + 1080) % 360, (c2.h-c1.h + 1080) % 360);
        double dS = (c1.s-c2.s) * 360;
        double dV = (c1.s-c2.s) * 360;

        return  Math.sqrt(dH*dH + dS*dS + dV*dV);
    }



    private int getClosestColor(Color c, Dir dir) {
        if (dir == Dir.VERT) {
            return findClosestColorIn(c, cVert, sVert);
        } else if (dir == Dir.POSD) {
            return findClosestColorIn(c, cPosD, sPosD);
        } else if (dir == Dir.NEGD) {
            return findClosestColorIn(c, cNegD, sNegD);
        }
        return -1;
    }

    private int findClosestColorIn(Color c, Color[] cs, int[] ss) {
        double minDist = distRGB(c, cs[0]);
        int minIndex = 0;
        for (int i = 1; i < cs.length; i++) {
            double curDist = Math.min(distHSV(c, cs[i]), distRGB(c, cs[i])); //todo normalize
            if (curDist <= minDist) {
                minDist = curDist;
                minIndex = i;
            }
        }
        return ss[minIndex];
    }
}

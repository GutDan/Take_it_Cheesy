package com.gutdan.takeitcheesy;

import androidx.annotation.NonNull;

public class Color {
    public int r, g, b;

    private boolean hsv = false;
    public double h, s, v;


    public Color(int srgbInt) {
        this.r = (srgbInt >> 16) & 0xff;
        this.g = (srgbInt >>  8) & 0xff;
        this.b = srgbInt & 0xff;
    }

    public Color(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public int getSRGBint(){
        return 0xff000000 | (this.r & 0xff) << 16 | (this.g & 0xff) << 8 | (this.b & 0xff);
    }

    public void calcHSV(){
        if (hsv) {
            return;
        }
        double r = this.r / 255d;
        double g = this.g / 255d;
        double b = this.b / 255d;

        double cmax = Math.max(r, Math.max(g, b));
        double cmin = Math.min(r, Math.min(g, b));
        double delta = cmax - cmin;

        this.h = 0;
        if (delta != 0) {
            if (cmax == r) {
                this.h = 60 * (((g - b) / delta) % 6);
            } else if (cmax == g) {
                this.h = 60 * (((b - r) / delta) + 2);
            } else if (cmax == b) {
                this.h = 60 * (((r - g) / delta) + 4);
            }
        }

        this.s = (cmax == 0) ? 0 : (delta / cmax);
        this.v = cmax;
        this.hsv = true;
    }

    public String getHexString(){
        return Integer.toHexString(this.getSRGBint() & 0x00ffffff);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("Color(%s)", getHexString());
    }
}

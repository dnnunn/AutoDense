package autodense.color;

import ij.process.ColorProcessor;
import java.awt.Color;

public final class HsvOps {
    public static float[] rgbToHsv(int r, int g, int b){
        float[] hsv = Color.RGBtoHSB(r,g,b,null); // H[0..1], S[0..1], V[0..1]
        hsv[0] *= 360f; hsv[1]*=100f; hsv[2]*=100f;
        return hsv;
    }
    
    public static float[] pixelHSV(ColorProcessor cp, int x, int y){
        int c = cp.get(x,y); 
        // Explicit RGB channel extraction with proper parentheses for clarity
        int r = (c & 0xff0000) >> 16;
        int g = (c & 0xff00) >> 8;
        int b = c & 0xff;
        return rgbToHsv(r,g,b);
    }
}
package com.betterdairy.autodense.analysis;

/**
 * Single source of truth for blue-ness quantification using CIELAB color space.
 * Consolidates multiple HSV/b* implementations into one scientifically accurate helper.
 */
public final class BlueIndex {
    
    private BlueIndex() {} // Utility class
    
    /**
     * Calculate blue index from RGB using CIELAB b* channel.
     * More negative b* values indicate higher blue-ness.
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255) 
     * @param b Blue component (0-255)
     * @return Blue index (0.0-1.0, higher = more blue)
     */
    public static double blueIndex(int r, int g, int b) {
        // Convert RGB to approximate CIELAB b*
        double[] lab = rgbToLab(r, g, b);
        double bStar = lab[2]; // b* channel: negative = blue, positive = yellow
        
        // Convert to 0-1 scale where higher values = more blue
        // Typical b* range for blue colors: -30 to -5
        // Clamp and normalize to [0,1] range
        double blueIndex = Math.max(0.0, -bStar / 30.0);
        return Math.min(1.0, blueIndex);
    }
    
    /**
     * Calculate blue index from RGB using CIELAB b* channel (float version).
     * Uses proper rounding instead of truncation for better precision.
     */
    public static double blueIndex(float r, float g, float b) {
        return blueIndex(Math.round(r), Math.round(g), Math.round(b));
    }
    
    /**
     * Calculate raw CIELAB b* value from RGB.
     * Negative values indicate blue-ness, positive values indicate yellow-ness.
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return CIELAB b* value (typically -50 to +50)
     */
    public static double bStar(int r, int g, int b) {
        double[] lab = rgbToLab(r, g, b);
        return lab[2];
    }
    
    /**
     * Calculate b* delta between colony and background colors.
     * This is the standard metric for X-gal blue/white screening.
     * 
     * @param colonyR Colony red component (0-255)
     * @param colonyG Colony green component (0-255)
     * @param colonyB Colony blue component (0-255)
     * @param bgR Background red component (0-255)
     * @param bgG Background green component (0-255)
     * @param bgB Background blue component (0-255)
     * @return b* delta (colony b* - background b*)
     */
    public static double bStarDelta(int colonyR, int colonyG, int colonyB,
                                   int bgR, int bgG, int bgB) {
        double colonyBStar = bStar(colonyR, colonyG, colonyB);
        double bgBStar = bStar(bgR, bgG, bgB);
        return colonyBStar - bgBStar;
    }
    
    /**
     * Convert RGB to CIELAB color space.
     * Uses D65 illuminant and sRGB color space.
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return [L*, a*, b*] values
     */
    private static double[] rgbToLab(int r, int g, int b) {
        // First convert RGB to XYZ
        double[] xyz = rgbToXyz(r, g, b);
        
        // Then XYZ to LAB
        return xyzToLab(xyz[0], xyz[1], xyz[2]);
    }
    
    /**
     * Convert sRGB to XYZ color space (D65 illuminant).
     */
    private static double[] rgbToXyz(int r, int g, int b) {
        // Normalize to 0-1 range
        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;
        
        // Apply gamma correction (sRGB)
        rNorm = (rNorm > 0.04045) ? Math.pow((rNorm + 0.055) / 1.055, 2.4) : rNorm / 12.92;
        gNorm = (gNorm > 0.04045) ? Math.pow((gNorm + 0.055) / 1.055, 2.4) : gNorm / 12.92;
        bNorm = (bNorm > 0.04045) ? Math.pow((bNorm + 0.055) / 1.055, 2.4) : bNorm / 12.92;
        
        // Convert to XYZ using sRGB matrix (D65)
        double x = rNorm * 0.4124564 + gNorm * 0.3575761 + bNorm * 0.1804375;
        double y = rNorm * 0.2126729 + gNorm * 0.7151522 + bNorm * 0.0721750;
        double z = rNorm * 0.0193339 + gNorm * 0.1191920 + bNorm * 0.9503041;
        
        return new double[]{x, y, z};
    }
    
    /**
     * Convert XYZ to CIELAB color space (D65 illuminant).
     */
    private static double[] xyzToLab(double x, double y, double z) {
        // D65 white point
        double xn = 0.95047;
        double yn = 1.00000;
        double zn = 1.08883;
        
        // Normalize by white point
        x /= xn;
        y /= yn;  
        z /= zn;
        
        // Apply Lab transformation with precise CIE constants
        double epsilon = 0.008856451679; // (6/29)^3 - precise CIE epsilon  
        double kappa = 903.2962963; // (29/3)^3 - precise CIE kappa
        
        x = (x > epsilon) ? Math.pow(x, 1.0/3.0) : (kappa * x + 16.0) / 116.0;
        y = (y > epsilon) ? Math.pow(y, 1.0/3.0) : (kappa * y + 16.0) / 116.0;
        z = (z > epsilon) ? Math.pow(z, 1.0/3.0) : (kappa * z + 16.0) / 116.0;
        
        double L = 116 * y - 16;
        double a = 500 * (x - y);
        double b = 200 * (y - z);
        
        return new double[]{L, a, b};
    }
    
    /**
     * Check if a color is considered "blue" based on b* threshold.
     * Typical thresholds: -6.0 (standard), -4.0 (sensitive), -8.0 (stringent)
     */
    public static boolean isBlue(int r, int g, int b, double bStarThreshold) {
        return bStar(r, g, b) < bStarThreshold;
    }
    
    /**
     * Check if colony is blue relative to background (X-gal screening).
     */
    public static boolean isBlueColony(int colonyR, int colonyG, int colonyB,
                                      int bgR, int bgG, int bgB,
                                      double deltaThreshold) {
        return bStarDelta(colonyR, colonyG, colonyB, bgR, bgG, bgB) < deltaThreshold;
    }
}
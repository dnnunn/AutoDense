package com.betterdairy.autodense.model;

public final class Models {
    private Models() {}
    public static record Lane(int index, int xStart, int xEnd) {}
    public static record Band(int index, int y, double area, double baseline, double mwKDa) {}
    public static record CalibrationModel(double a, double b, double r2) {}
    
    // Colony analysis models
    public static record Plate(
        String handle, 
        double centerX, double centerY, 
        double radius, double pixelsPerMm,
        double eccentricity, boolean wasDescewed,
        double rimMaskWidthMm, String thresholdMethod
    ) {}
    
    public static record EllipseFit(
        double centerX, double centerY,
        double majorAxis, double minorAxis, 
        double angle, double eccentricity
    ) {}
    
    // Enhanced colony model with comprehensive features
    public static record Colony(
        int index,
        double x, double y,                    // Centroid coordinates
        double area,                          // Area in pixels
        double diameter,                      // Equivalent diameter in pixels
        double diameterMm,                    // Equivalent diameter in mm
        double circularity,                   // 4π×area/perimeter² (1.0 = perfect circle)
        double solidity,                      // area/convex_hull_area
        double meanIntensity,                 // Mean grayscale intensity
        ColonyColor colorClass,
        double colorConfidence,
        ColonySize sizeClass,
        String binCategory
    ) {}
    
    public enum ColonyColor {
        WHITE, BLUE, PINK, YELLOW, GREEN, OTHER
    }
    
    public enum ColonySize {
        SMALL, MEDIUM, LARGE
    }
    
    // Enhanced color features in Lab space with background comparison
    public static record LabPhotometry(
        double meanL, double medianL,         // L* (lightness) statistics
        double meanA, double medianA,         // a* (green-red) statistics  
        double meanB, double medianB,         // b* (blue-yellow) statistics
        double backgroundL, double backgroundA, double backgroundB,  // Background ring values
        double deltaE,                        // ΔE color difference from background
        double bDelta,                        // b*_colony - b*_background (X-gal indicator)
        double snrL                           // Signal-to-noise ratio in L* channel
    ) {}
    
    // Geometric measurements
    public static record GeometricFeatures(
        double area, double perimeter,
        double equivalentDiameter,
        double circularity, double solidity,
        double aspectRatio,
        double convexHullArea
    ) {}
    
    // X-gal classification result
    public static record XGalClassification(
        boolean isPositive,
        double bDelta,                        // b*_colony - b*_background
        double snrL,                          // L* signal-to-noise ratio
        double confidence,                    // Classification confidence
        String classification                 // "positive", "negative", "uncertain"
    ) {}
    
    // Comprehensive colony analysis
    public static record ColonyAnalysis(
        Colony colony,
        GeometricFeatures geometry,
        LabPhotometry photometry,
        XGalClassification xgalResult
    ) {}
    
    public static record ColonyBins(
        int smallCount, int mediumCount, int largeCount,
        int positiveSmall, int positiveMedium, int positiveLarge,
        int negativeSmall, int negativeMedium, int negativeLarge,
        double[] sizeBinsMm
    ) {}
    
    // Simplified colony classification for practical screening
    public static record ColonyClassification(
        int colonyIndex,
        String label,                         // "xgal_pos", "xgal_neg", "uncertain", or kmeans cluster
        double confidence,                    // 0.0 to 1.0
        double bDelta,                       // b* colony - b* background (for xgal)
        double snrL                          // Signal-to-noise ratio in L* channel
    ) {}
}

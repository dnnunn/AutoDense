package com.betterdairy.autodense.analysis;

/**
 * Mutable colony representation for efficient classification and binning.
 * Contains all essential fields for streamlined processing.
 */
public final class MutableColony {
    
    // Basic identification and geometry
    public final int id;
    public final double x, y;              // Centroid in pixels
    public final double eqDiamPx;          // Equivalent diameter in pixels
    
    // Lab color measurements (populated by classifier)
    public double L, a, b;                 // Colony Lab values
    public double Lbg, abg, bbg;           // Background Lab values
    public double bDelta;                  // b* colony - b* background
    public double dEbg;                    // ΔE76 colony vs background
    public double snrL;                    // Signal-to-noise ratio L*
    
    // Output classifications (populated by classifier and binner)
    public String xgalBinary;              // "pos", "neg", "uncertain"
    public String xgalGrade;               // "light", "medium", "dark", null
    public String sizeBin;                 // "tiny", "small", "medium", "large"
    public String label;                   // Combined: e.g., "dark+large"
    public double confidence;              // 0-1 confidence score
    
    public MutableColony(int id, double x, double y, double eqDiamPx) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.eqDiamPx = eqDiamPx;
        
        // Initialize color measurements to defaults
        this.L = this.a = this.b = 0.0;
        this.Lbg = this.abg = this.bbg = 0.0;
        this.bDelta = this.dEbg = this.snrL = 0.0;
        
        // Initialize classifications to defaults
        this.xgalBinary = "unclassified";
        this.xgalGrade = null;
        this.sizeBin = "unknown";
        this.label = "unclassified+unknown";
        this.confidence = 0.0;
    }
    
    /**
     * Convert from immutable Models.Colony to mutable MutableColony
     */
    public static MutableColony fromColony(com.betterdairy.autodense.model.Models.Colony colony) {
        return new MutableColony(
            colony.index(),
            colony.x(),
            colony.y(), 
            colony.diameter()
        );
    }
    
    /**
     * Convert list of immutable colonies to mutable colonies
     */
    public static java.util.List<MutableColony> fromColonies(
            java.util.List<com.betterdairy.autodense.model.Models.Colony> colonies) {
        return colonies.stream()
            .map(MutableColony::fromColony)
            .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public String toString() {
        return String.format("Colony[id=%d, x=%.1f, y=%.1f, diam=%.1f, %s]", 
            id, x, y, eqDiamPx, label);
    }
}
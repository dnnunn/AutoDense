package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.CalibrationModel;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public final class Calibrator {
    private Calibrator() {}

    /**
     * Standard curve fitter with enhanced statistical analysis
     */
    public static class StandardCurveFitter {
        
        /**
         * Enhanced calibration model with statistical metrics
         */
        public static class Model {
            private final double a;        // Slope coefficient
            private final double b;        // Intercept coefficient 
            private final double r2;       // R-squared goodness of fit
            private final List<Double> residuals; // Residuals for CV calculation
            private final List<Double> concentrations; // Standard concentrations
            private final List<Double> responses;      // Measured responses
            private final double loq;      // Limit of quantification
            private final double lloq;     // Lower limit of quantification
            
            public Model(double a, double b, double r2, List<Double> concentrations, List<Double> responses) {
                this.a = a;
                this.b = b;
                this.r2 = r2;
                this.concentrations = new ArrayList<>(concentrations);
                this.responses = new ArrayList<>(responses);
                this.residuals = calculateResiduals();
                this.loq = calculateLOQ();
                this.lloq = calculateLLOQ();
            }
            
            /**
             * Calculate residuals for each standard point
             */
            private List<Double> calculateResiduals() {
                List<Double> residuals = new ArrayList<>();
                for (int i = 0; i < concentrations.size(); i++) {
                    double predicted = predict(concentrations.get(i));
                    double actual = responses.get(i);
                    residuals.add(actual - predicted);
                }
                return residuals;
            }
            
            /**
             * Predict response for given concentration
             */
            public double predict(double concentration) {
                return a * concentration + b;
            }
            
            /**
             * Calculate %CV at a given concentration using residual standard error
             */
            public double calculateCVPercent(double concentration) {
                if (concentration <= 0) return Double.NaN;
                
                double predicted = predict(concentration);
                if (predicted <= 0) return Double.NaN;
                
                // Calculate residual standard error (RSE)
                double sumSquaredResiduals = 0.0;
                for (double residual : residuals) {
                    sumSquaredResiduals += residual * residual;
                }
                
                int degreesOfFreedom = Math.max(1, residuals.size() - 2); // n - 2 for linear regression
                double rse = Math.sqrt(sumSquaredResiduals / degreesOfFreedom);
                
                // %CV = (RSE / predicted) * 100
                return (rse / predicted) * 100.0;
            }
            
            /**
             * Calculate LOQ where %CV crosses the specified threshold (default 20%)
             */
            private double calculateLOQ() {
                return calculateLimitAtCV(20.0); // Standard 20% CV threshold
            }
            
            /**
             * Calculate LLOQ (typically 3x the detection limit or where CV = 30%)
             */
            private double calculateLLOQ() {
                return calculateLimitAtCV(30.0); // Conservative 30% CV threshold for LLOQ
            }
            
            /**
             * Find concentration where %CV crosses specified threshold
             */
            private double calculateLimitAtCV(double cvThreshold) {
                if (concentrations.isEmpty()) return Double.NaN;
                
                // Find concentration range
                double minConc = Collections.min(concentrations);
                double maxConc = Collections.max(concentrations);
                
                // Binary search for CV crossing point
                double low = minConc * 0.1;  // Start below lowest standard
                double high = maxConc;
                double tolerance = (maxConc - minConc) * 0.001; // 0.1% tolerance
                
                while (high - low > tolerance) {
                    double mid = (low + high) / 2.0;
                    double cvAtMid = calculateCVPercent(mid);
                    
                    if (Double.isNaN(cvAtMid)) {
                        low = mid;
                        continue;
                    }
                    
                    if (cvAtMid > cvThreshold) {
                        low = mid;  // CV too high, need higher concentration
                    } else {
                        high = mid; // CV acceptable, can go lower
                    }
                }
                
                return (low + high) / 2.0;
            }
            
            /**
             * Get %CV predictions at in-range midpoints for validation
             */
            public List<CVPrediction> getCVPredictions() {
                List<CVPrediction> predictions = new ArrayList<>();
                
                if (concentrations.size() < 2) return predictions;
                
                // Calculate CV at midpoints between standards
                Collections.sort(concentrations);
                for (int i = 0; i < concentrations.size() - 1; i++) {
                    double conc1 = concentrations.get(i);
                    double conc2 = concentrations.get(i + 1);
                    double midpoint = (conc1 + conc2) / 2.0;
                    
                    double cvPercent = calculateCVPercent(midpoint);
                    predictions.add(new CVPrediction(midpoint, cvPercent));
                }
                
                // Also add predictions at LOQ and LLOQ
                if (!Double.isNaN(lloq)) {
                    predictions.add(new CVPrediction(lloq, calculateCVPercent(lloq), "LLOQ"));
                }
                if (!Double.isNaN(loq)) {
                    predictions.add(new CVPrediction(loq, calculateCVPercent(loq), "LOQ"));
                }
                
                return predictions;
            }
            
            /**
             * Check if a concentration is below LOQ
             */
            public boolean isBelowLOQ(double concentration) {
                return !Double.isNaN(loq) && concentration < loq;
            }
            
            /**
             * Check if a concentration is below LLOQ
             */
            public boolean isBelowLLOQ(double concentration) {
                return !Double.isNaN(lloq) && concentration < lloq;
            }
            
            // Getters
            public double getSlope() { return a; }
            public double getIntercept() { return b; }
            public double getR2() { return r2; }
            public double getLOQ() { return loq; }
            public double getLLOQ() { return lloq; }
            public List<Double> getConcentrations() { return new ArrayList<>(concentrations); }
            public List<Double> getResponses() { return new ArrayList<>(responses); }
        }
        
        /**
         * %CV prediction at a specific concentration
         */
        public static class CVPrediction {
            public final double concentration;
            public final double cvPercent;
            public final String label;
            
            public CVPrediction(double concentration, double cvPercent) {
                this(concentration, cvPercent, "");
            }
            
            public CVPrediction(double concentration, double cvPercent, String label) {
                this.concentration = concentration;
                this.cvPercent = cvPercent;
                this.label = label;
            }
            
            @Override
            public String toString() {
                return String.format("%s%.3f μg/mL: %.1f%% CV", 
                                   label.isEmpty() ? "" : label + " ", 
                                   concentration, cvPercent);
            }
        }
        
        /**
         * Fit standard curve from concentration-response data
         */
        public static Model fit(List<Double> concentrations, List<Double> responses) {
            if (concentrations.size() != responses.size() || concentrations.size() < 2) {
                throw new IllegalArgumentException("Need at least 2 matched concentration-response pairs");
            }
            
            // Linear regression: response = a * concentration + b
            int n = concentrations.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
            
            for (int i = 0; i < n; i++) {
                double x = concentrations.get(i);
                double y = responses.get(i);
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXX += x * x;
            }
            
            double meanX = sumX / n;
            double meanY = sumY / n;
            
            // Calculate slope and intercept
            double denominator = sumXX - n * meanX * meanX;
            if (Math.abs(denominator) < 1e-10) {
                throw new IllegalArgumentException("Cannot fit curve: concentrations have no variance");
            }
            
            double a = (sumXY - n * meanX * meanY) / denominator;
            double b = meanY - a * meanX;
            
            // Calculate R-squared
            double ssTotal = 0, ssResidual = 0;
            for (int i = 0; i < n; i++) {
                double y = responses.get(i);
                double yPred = a * concentrations.get(i) + b;
                ssTotal += (y - meanY) * (y - meanY);
                ssResidual += (y - yPred) * (y - yPred);
            }
            
            double r2 = ssTotal > 0 ? 1.0 - (ssResidual / ssTotal) : 0.0;
            
            return new Model(a, b, r2, concentrations, responses);
        }
    }

    public static CalibrationModel fit(/* ladder lane, ladder spec */) {
        // Placeholder - maintains backward compatibility
        return new CalibrationModel(0.0, 0.0, 0.0);
    }

    public static double assignMw(CalibrationModel m, double distancePx) {
        return Math.pow(10, m.a() * distancePx + m.b());
    }
}

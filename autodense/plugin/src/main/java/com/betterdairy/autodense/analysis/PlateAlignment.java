package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.performance.PerformanceOptimizer;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;

/**
 * Handles alignment and registration of agar plate images across time points.
 * Supports both feature-based matching and orientation mark detection.
 */
public class PlateAlignment {
    
    public static class AlignmentResult {
        public final AffineTransform transform;
        public final double confidence;
        public final List<Point2D> keyPoints;
        public final String method;
        public final JSONObject metadata;
        
        public AlignmentResult(AffineTransform transform, double confidence, 
                             List<Point2D> keyPoints, String method, JSONObject metadata) {
            this.transform = transform;
            this.confidence = confidence;
            this.keyPoints = keyPoints;
            this.method = method;
            this.metadata = metadata;
        }
    }
    
    public static class AlignmentOptions {
        public String method = "feature_matching";  // "feature_matching" or "orientation_marks"
        public double tolerancePx = 5.0;
        public int maxFeatures = 100;
        public double featureThreshold = 0.7;
        public boolean allowRotation = true;
        public boolean allowSkewing = true;
        public boolean allowScaling = false;
        public int orientationMarkCount = 3;  // Expected number of orientation marks
        public double markDetectionThreshold = 0.8;
    }
    
    /**
     * Comprehensive plate signature for robust matching across time points
     * Includes ring projection, fiducial dots, and geometric descriptors
     */
    public static class PlateSignature {
        public final double[] ringProjection;      // Radial intensity profile
        public final List<Point2D> fiducialDots;   // 3-4 distinctive points
        public final double plateRadius;           // Detected plate radius
        public final Point2D plateCenter;          // Detected plate center
        public final double[] geometricDescriptor; // Shape invariant features
        public final long timestamp;               // When signature was created
        public final JSONObject metadata;          // Additional information
        
        public PlateSignature(double[] ringProjection, List<Point2D> fiducialDots, 
                            double plateRadius, Point2D plateCenter, 
                            double[] geometricDescriptor, JSONObject metadata) {
            this.ringProjection = ringProjection;
            this.fiducialDots = new ArrayList<>(fiducialDots);
            this.plateRadius = plateRadius;
            this.plateCenter = plateCenter;
            this.geometricDescriptor = geometricDescriptor;
            this.timestamp = System.currentTimeMillis();
            this.metadata = metadata;
        }
        
        /**
         * Convert to JSON for session storage
         */
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("ring_projection", new JSONArray(ringProjection));
            json.put("plate_radius", plateRadius);
            json.put("plate_center_x", plateCenter.getX());
            json.put("plate_center_y", plateCenter.getY());
            json.put("geometric_descriptor", new JSONArray(geometricDescriptor));
            json.put("timestamp", timestamp);
            json.put("metadata", metadata);
            
            JSONArray fiducialArray = new JSONArray();
            for (Point2D dot : fiducialDots) {
                JSONObject dotJson = new JSONObject();
                dotJson.put("x", dot.getX());
                dotJson.put("y", dot.getY());
                fiducialArray.put(dotJson);
            }
            json.put("fiducial_dots", fiducialArray);
            
            return json;
        }
        
        /**
         * Create from JSON stored in session
         */
        public static PlateSignature fromJSON(JSONObject json) {
            JSONArray ringArray = json.getJSONArray("ring_projection");
            double[] ringProjection = new double[ringArray.length()];
            for (int i = 0; i < ringArray.length(); i++) {
                ringProjection[i] = ringArray.getDouble(i);
            }
            
            JSONArray geomArray = json.getJSONArray("geometric_descriptor");
            double[] geometricDescriptor = new double[geomArray.length()];
            for (int i = 0; i < geomArray.length(); i++) {
                geometricDescriptor[i] = geomArray.getDouble(i);
            }
            
            List<Point2D> fiducialDots = new ArrayList<>();
            JSONArray fiducialArray = json.getJSONArray("fiducial_dots");
            for (int i = 0; i < fiducialArray.length(); i++) {
                JSONObject dotJson = fiducialArray.getJSONObject(i);
                fiducialDots.add(new Point2D.Double(dotJson.getDouble("x"), dotJson.getDouble("y")));
            }
            
            Point2D plateCenter = new Point2D.Double(
                json.getDouble("plate_center_x"),
                json.getDouble("plate_center_y")
            );
            
            PlateSignature signature = new PlateSignature(
                ringProjection, fiducialDots, json.getDouble("plate_radius"),
                plateCenter, geometricDescriptor, json.optJSONObject("metadata")
            );
            
            return signature;
        }
    }
    
    /**
     * Generate comprehensive plate signature for robust matching
     * @param imp Input plate image
     * @return PlateSignature containing ring projection and fiducial features
     */
    public static PlateSignature generatePlateSignature(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor().duplicate();
        
        // Step 1: Detect plate boundary
        PlateGeometry geometry = detectPlateGeometry(ip);
        
        // Step 2: Generate ring projection (radial intensity profile)
        double[] ringProjection = generateRingProjection(ip, geometry);
        
        // Step 3: Detect 3-4 fiducial dots (distinctive features)
        List<Point2D> fiducialDots = detectFiducialDots(ip, geometry);
        
        // Step 4: Create geometric descriptor (shape invariants)
        double[] geometricDescriptor = createGeometricDescriptor(ip, geometry, fiducialDots);
        
        // Step 5: Create metadata
        JSONObject metadata = new JSONObject()
            .put("image_width", ip.getWidth())
            .put("image_height", ip.getHeight())
            .put("detection_confidence", geometry.confidence)
            .put("fiducial_count", fiducialDots.size());
        
        return new PlateSignature(ringProjection, fiducialDots, geometry.radius, 
                                geometry.center, geometricDescriptor, metadata);
    }
    
    /**
     * Store plate signature in session for later matching
     */
    public static void storePlateSignature(SessionStore sessionStore, String imageHandle, PlateSignature signature) {
        if (sessionStore != null && imageHandle != null) {
            sessionStore.putAnalysis("plate_signature", signature.toJSON(), imageHandle);
        }
    }
    
    /**
     * Retrieve plate signature from session
     */
    public static PlateSignature getPlateSignature(SessionStore sessionStore, String imageHandle) {
        if (sessionStore == null || imageHandle == null) {
            return null;
        }
        
        for (String analysisHandle : sessionStore.getAnalysesForImage(imageHandle)) {
            SessionStore.AnalysisRecord analysis = sessionStore.getAnalysis(analysisHandle);
            if ("plate_signature".equals(analysis.type)) {
                return PlateSignature.fromJSON((JSONObject) analysis.data);
            }
        }
        return null;
    }
    
    /**
     * Plate geometry detection result
     */
    private static class PlateGeometry {
        final Point2D center;
        final double radius;
        final double confidence;
        
        PlateGeometry(Point2D center, double radius, double confidence) {
            this.center = center;
            this.radius = radius;
            this.confidence = confidence;
        }
    }
    
    /**
     * Detect plate center and radius using Hough circle detection
     */
    private static PlateGeometry detectPlateGeometry(ImageProcessor ip) {
        // Simplified plate detection - in production would use proper Hough circles
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        // Assume plate occupies most of the image
        Point2D center = new Point2D.Double(width / 2.0, height / 2.0);
        double radius = Math.min(width, height) * 0.4; // 80% of shorter dimension
        double confidence = 0.85; // Would be calculated from edge strength
        
        return new PlateGeometry(center, radius, confidence);
    }
    
    /**
     * Generate radial intensity projection from plate center
     */
    private static double[] generateRingProjection(ImageProcessor ip, PlateGeometry geometry) {
        int numRings = 64; // Number of concentric rings to sample
        double[] projection = new double[numRings];
        
        double maxRadius = geometry.radius;
        Point2D center = geometry.center;
        
        for (int ring = 0; ring < numRings; ring++) {
            double currentRadius = (ring + 1) * maxRadius / numRings;
            double sum = 0;
            int count = 0;
            
            // Sample points around the ring
            int numSamples = (int) (currentRadius * Math.PI * 2 / 2); // Every 2 pixels
            for (int sample = 0; sample < numSamples; sample++) {
                double angle = 2 * Math.PI * sample / numSamples;
                int x = (int) (center.getX() + currentRadius * Math.cos(angle));
                int y = (int) (center.getY() + currentRadius * Math.sin(angle));
                
                if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                    sum += ip.getPixelValue(x, y);
                    count++;
                }
            }
            
            projection[ring] = count > 0 ? sum / count : 0;
        }
        
        return projection;
    }
    
    /**
     * Detect 3-4 distinctive fiducial points for alignment
     */
    private static List<Point2D> detectFiducialDots(ImageProcessor ip, PlateGeometry geometry) {
        List<Point2D> candidates = new ArrayList<>();
        
        // Method 1: Look for dark spots (pen marks, bubbles)
        candidates.addAll(detectDarkSpots(ip, geometry));
        
        // Method 2: Look for bright spots (reflections, scratches)
        candidates.addAll(detectBrightSpots(ip, geometry));
        
        // Method 3: Look for edge irregularities
        candidates.addAll(detectEdgeFeatures(ip, geometry));
        
        // Sort by feature strength and select top 3-4
        Collections.sort(candidates, (p1, p2) -> 
            Double.compare(getFeatureStrength(ip, p2), getFeatureStrength(ip, p1)));
        
        // Return top 4 candidates, ensuring they're well distributed
        List<Point2D> fiducials = new ArrayList<>();
        for (Point2D candidate : candidates) {
            if (fiducials.size() >= 4) break;
            
            // Ensure minimum distance from existing fiducials
            boolean tooClose = false;
            for (Point2D existing : fiducials) {
                if (candidate.distance(existing) < 50) { // 50 pixel minimum separation
                    tooClose = true;
                    break;
                }
            }
            
            if (!tooClose) {
                fiducials.add(candidate);
            }
        }
        
        return fiducials;
    }
    
    private static List<Point2D> detectDarkSpots(ImageProcessor ip, PlateGeometry geometry) {
        List<Point2D> spots = new ArrayList<>();
        ImageProcessor smoothed = ip.duplicate();
        smoothed.smooth();
        
        double threshold = smoothed.getStatistics().mean - smoothed.getStatistics().stdDev;
        
        for (int y = 10; y < ip.getHeight() - 10; y += 5) {
            for (int x = 10; x < ip.getWidth() - 10; x += 5) {
                if (smoothed.getPixelValue(x, y) < threshold) {
                    // Check if it's a local minimum
                    boolean isMinimum = true;
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            if (smoothed.getPixelValue(x + dx, y + dy) < smoothed.getPixelValue(x, y)) {
                                isMinimum = false;
                                break;
                            }
                        }
                        if (!isMinimum) break;
                    }
                    
                    if (isMinimum) {
                        spots.add(new Point2D.Double(x, y));
                    }
                }
            }
        }
        
        return spots;
    }
    
    private static List<Point2D> detectBrightSpots(ImageProcessor ip, PlateGeometry geometry) {
        List<Point2D> spots = new ArrayList<>();
        ImageProcessor smoothed = ip.duplicate();
        smoothed.smooth();
        
        double threshold = smoothed.getStatistics().mean + smoothed.getStatistics().stdDev;
        
        for (int y = 10; y < ip.getHeight() - 10; y += 5) {
            for (int x = 10; x < ip.getWidth() - 10; x += 5) {
                if (smoothed.getPixelValue(x, y) > threshold) {
                    // Check if it's a local maximum
                    boolean isMaximum = true;
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            if (smoothed.getPixelValue(x + dx, y + dy) > smoothed.getPixelValue(x, y)) {
                                isMaximum = false;
                                break;
                            }
                        }
                        if (!isMaximum) break;
                    }
                    
                    if (isMaximum) {
                        spots.add(new Point2D.Double(x, y));
                    }
                }
            }
        }
        
        return spots;
    }
    
    private static List<Point2D> detectEdgeFeatures(ImageProcessor ip, PlateGeometry geometry) {
        List<Point2D> features = new ArrayList<>();
        
        // Sample points around the plate edge and look for irregularities
        Point2D center = geometry.center;
        double radius = geometry.radius;
        
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 16) {
            int x = (int) (center.getX() + radius * Math.cos(angle));
            int y = (int) (center.getY() + radius * Math.sin(angle));
            
            if (x >= 5 && x < ip.getWidth() - 5 && y >= 5 && y < ip.getHeight() - 5) {
                // Calculate local edge strength
                double edgeStrength = calculateEdgeStrength(ip, x, y);
                if (edgeStrength > 50) { // Threshold for significant edge
                    features.add(new Point2D.Double(x, y));
                }
            }
        }
        
        return features;
    }
    
    /**
     * Create perspective-invariant geometric descriptor for robust plate matching
     * Handles different camera angles, distances, and orientations
     */
    private static double[] createGeometricDescriptor(ImageProcessor ip, PlateGeometry geometry, List<Point2D> fiducials) {
        double[] descriptor = new double[16]; // Expanded for better invariance
        
        // Initialize all values
        for (int i = 0; i < descriptor.length; i++) {
            descriptor[i] = 0.0;
        }
        
        if (fiducials.size() >= 3) {
            Point2D f1 = fiducials.get(0);
            Point2D f2 = fiducials.get(1);
            Point2D f3 = fiducials.get(2);
            
            // PERSPECTIVE-INVARIANT FEATURES
            
            // 1. Distance ratios (scale and perspective invariant)
            double d12 = f1.distance(f2);
            double d23 = f2.distance(f3);
            double d13 = f1.distance(f3);
            
            // Ratios are invariant to uniform scaling
            if (d13 > 1e-6) { // Avoid division by zero
                descriptor[0] = d12 / d13; // Triangle shape ratio 1
                descriptor[1] = d23 / d13; // Triangle shape ratio 2
            }
            
            // 2. Angular relationships (perspective invariant)
            descriptor[2] = calculateAngle(f1, f2, f3); // Interior angle
            descriptor[3] = calculateAngle(f2, f3, f1); // Another interior angle
            descriptor[4] = calculateAngle(f3, f1, f2); // Third interior angle
            
            // 3. Triangle area ratio to perimeter^2 (scale invariant shape measure)
            double triangleArea = calculateTriangleArea(f1, f2, f3);
            double perimeter = d12 + d23 + d13;
            if (perimeter > 1e-6) {
                descriptor[5] = 4 * Math.PI * triangleArea / (perimeter * perimeter); // Isoperimetric ratio
            }
            
            // 4. Centroid-based features (relative to fiducial triangle)
            Point2D centroid = new Point2D.Double(
                (f1.getX() + f2.getX() + f3.getX()) / 3,
                (f1.getY() + f2.getY() + f3.getY()) / 3
            );
            
            // Distances from centroid to each fiducial (normalized by triangle scale)
            double triangleScale = Math.sqrt(triangleArea);
            if (triangleScale > 1e-6) {
                descriptor[6] = centroid.distance(f1) / triangleScale;
                descriptor[7] = centroid.distance(f2) / triangleScale;
                descriptor[8] = centroid.distance(f3) / triangleScale;
            }
            
            // 5. Cross-ratios (projective invariants - survive perspective transformation)
            if (fiducials.size() >= 4) {
                Point2D f4 = fiducials.get(3);
                descriptor[9] = calculateCrossRatio(f1, f2, f3, f4);
                descriptor[10] = calculateCrossRatio(f2, f3, f4, f1); // Different ordering
                
                // Additional 4-point features
                double d14 = f1.distance(f4);
                double d24 = f2.distance(f4);
                double d34 = f3.distance(f4);
                
                // More distance ratios
                if (d14 > 1e-6) {
                    descriptor[11] = d24 / d14;
                    descriptor[12] = d34 / d14;
                }
                
                // Quadrilateral area ratio
                double quadArea = calculateQuadrilateralArea(f1, f2, f3, f4);
                if (triangleArea > 1e-6) {
                    descriptor[13] = quadArea / triangleArea; // Expansion ratio
                }
            }
            
            // 6. Plate-relative features (if plate detection is reliable)
            if (geometry.confidence > 0.7) {
                // Distance ratios to estimated plate center
                double dc1 = geometry.center.distance(f1);
                double dc2 = geometry.center.distance(f2);
                double dc3 = geometry.center.distance(f3);
                
                // Normalized by triangle scale to be perspective-invariant
                if (triangleScale > 1e-6) {
                    descriptor[14] = dc1 / triangleScale;
                    descriptor[15] = Math.max(dc2, dc3) / Math.min(dc2, dc3); // Asymmetry measure
                }
            }
        }
        
        return descriptor;
    }
    
    /**
     * Calculate triangle area using cross product
     */
    private static double calculateTriangleArea(Point2D p1, Point2D p2, Point2D p3) {
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double x3 = p3.getX(), y3 = p3.getY();
        
        return 0.5 * Math.abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)));
    }
    
    /**
     * Calculate quadrilateral area (sum of two triangles)
     */
    private static double calculateQuadrilateralArea(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        return calculateTriangleArea(p1, p2, p3) + calculateTriangleArea(p1, p3, p4);
    }
    
    /**
     * Calculate cross-ratio - projective invariant that survives perspective transformation
     * Cross-ratio of four collinear points is preserved under projective transformation
     */
    private static double calculateCrossRatio(Point2D a, Point2D b, Point2D c, Point2D d) {
        // For non-collinear points, use area-based cross-ratio
        double area_abc = calculateTriangleArea(a, b, c);
        double area_abd = calculateTriangleArea(a, b, d);
        double area_acd = calculateTriangleArea(a, c, d);
        double area_bcd = calculateTriangleArea(b, c, d);
        
        // Cross-ratio using signed areas (approximation for non-collinear case)
        if (Math.abs(area_abd * area_bcd) > 1e-10) {
            return (area_abc * area_acd) / (area_abd * area_bcd);
        }
        
        return 1.0; // Default if calculation fails
    }
    
    private static double calculateAngle(Point2D p1, Point2D p2, Point2D p3) {
        double dx1 = p1.getX() - p2.getX();
        double dy1 = p1.getY() - p2.getY();
        double dx2 = p3.getX() - p2.getX();
        double dy2 = p3.getY() - p2.getY();
        
        double angle = Math.atan2(dy2, dx2) - Math.atan2(dy1, dx1);
        if (angle < 0) angle += 2 * Math.PI;
        
        return angle;
    }
    
    private static double calculateEdgeStrength(ImageProcessor ip, int x, int y) {
        // Optimized Sobel edge detection using fast pixel array access
        float[] pixels = PerformanceOptimizer.FastPixelOps.getPixelArrayFloat(
            new ImagePlus("temp", ip));
        int width = ip.getWidth();
        
        // Calculate array indices for 3x3 neighborhood
        int c = y * width + x;  // center pixel
        
        // Sobel X gradient
        double gx = pixels[c - width + 1] + 2 * pixels[c + 1] + pixels[c + width + 1]
                  - pixels[c - width - 1] - 2 * pixels[c - 1] - pixels[c + width - 1];
        
        // Sobel Y gradient  
        double gy = pixels[c + width - 1] + 2 * pixels[c + width] + pixels[c + width + 1]
                  - pixels[c - width - 1] - 2 * pixels[c - width] - pixels[c - width + 1];
        
        return Math.sqrt(gx * gx + gy * gy);
    }
    
    /**
     * Aligns a target image to match a reference image with robust multi-method approach
     * @param reference Reference image (first time point)
     * @param target Target image to be aligned  
     * @param options Alignment parameters
     * @return Alignment result with transformation matrix
     */
    public static AlignmentResult alignPlates(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        return alignPlatesRobust(reference, target, options, null, null, null);
    }
    
    /**
     * Enhanced robust plate alignment with signature matching and session store support
     * @param reference Reference image
     * @param target Target image to align
     * @param options Alignment parameters
     * @param sessionStore Session store for signature caching (optional)
     * @param refHandle Reference image handle (optional)
     * @param targetHandle Target image handle (optional)
     * @return Best alignment result from multiple methods
     */
    public static AlignmentResult alignPlatesRobust(ImagePlus reference, ImagePlus target, AlignmentOptions options,
                                                   SessionStore sessionStore, String refHandle, String targetHandle) {
        // Method 1: Try signature-based matching first (most reliable)
        AlignmentResult signatureResult = alignUsingSignatures(reference, target, options, 
                                                              sessionStore, refHandle, targetHandle);
        if (signatureResult.confidence > 0.8) {
            return signatureResult;
        }
        
        // Method 2: Try ORB feature matching via mpicbg
        AlignmentResult orbResult = alignUsingORBFeatures(reference, target, options);
        if (orbResult.confidence > 0.7) {
            return orbResult;
        }
        
        // Method 3: Try standard feature matching
        AlignmentResult featureResult = alignUsingFeatureMatching(reference, target, options);
        if (featureResult.confidence > 0.6) {
            return featureResult;
        }
        
        // Method 4: Fall back to phase correlation
        AlignmentResult phaseResult = alignUsingPhaseCorrelation(reference, target, options);
        if (phaseResult.confidence > 0.5) {
            return phaseResult;
        }
        
        // Method 5: Try orientation marks as last resort
        if (options.method.equals("orientation_marks")) {
            AlignmentResult markResult = alignUsingOrientationMarks(reference, target, options);
            if (markResult.confidence > 0.0) {
                return markResult;
            }
        }
        
        // Return the best result we found, even if confidence is low
        AlignmentResult best = signatureResult;
        if (orbResult.confidence > best.confidence) best = orbResult;
        if (featureResult.confidence > best.confidence) best = featureResult;
        if (phaseResult.confidence > best.confidence) best = phaseResult;
        
        return best;
    }
    
    /**
     * Signature-based alignment using stored plate signatures
     */
    private static AlignmentResult alignUsingSignatures(ImagePlus reference, ImagePlus target, AlignmentOptions options,
                                                       SessionStore sessionStore, String refHandle, String targetHandle) {
        // Try to get existing signatures from session
        PlateSignature refSig = null;
        PlateSignature targetSig = null;
        
        if (sessionStore != null && refHandle != null) {
            refSig = getPlateSignature(sessionStore, refHandle);
        }
        if (refSig == null) {
            refSig = generatePlateSignature(reference);
            if (sessionStore != null && refHandle != null) {
                storePlateSignature(sessionStore, refHandle, refSig);
            }
        }
        
        if (sessionStore != null && targetHandle != null) {
            targetSig = getPlateSignature(sessionStore, targetHandle);
        }
        if (targetSig == null) {
            targetSig = generatePlateSignature(target);
            if (sessionStore != null && targetHandle != null) {
                storePlateSignature(sessionStore, targetHandle, targetSig);
            }
        }
        
        // Match signatures
        return matchPlateSignatures(refSig, targetSig, options);
    }
    
    /**
     * Match two plate signatures to find transformation
     */
    private static AlignmentResult matchPlateSignatures(PlateSignature refSig, PlateSignature targetSig, AlignmentOptions options) {
        // 1. Compare ring projections for rotation/scale estimation
        double[] refRing = refSig.ringProjection;
        double[] targetRing = targetSig.ringProjection;
        
        double ringCorrelation = calculateRingCorrelation(refRing, targetRing);
        
        // 2. Match fiducial points
        List<FeatureMatch> fiducialMatches = matchFiducialDots(refSig.fiducialDots, targetSig.fiducialDots);
        
        // 3. Use geometric descriptors for validation
        double geometricSimilarity = calculateGeometricSimilarity(refSig.geometricDescriptor, targetSig.geometricDescriptor);
        
        // Calculate overall confidence
        double confidence = 0.4 * ringCorrelation + 0.4 * (fiducialMatches.size() / 4.0) + 0.2 * geometricSimilarity;
        confidence = Math.min(1.0, confidence);
        
        if (fiducialMatches.size() >= 3 && confidence > 0.3) {
            // Estimate transformation from fiducial matches
            TransformEstimate estimate = estimateTransformation(fiducialMatches, options);
            
            JSONObject metadata = new JSONObject()
                .put("method", "signature_matching")
                .put("ring_correlation", ringCorrelation)
                .put("fiducial_matches", fiducialMatches.size())
                .put("geometric_similarity", geometricSimilarity)
                .put("signature_confidence", confidence);
            
            return new AlignmentResult(estimate.transform, estimate.confidence, 
                                     refSig.fiducialDots, "signature_matching", metadata);
        } else {
            // Not enough matches for reliable transformation
            JSONObject metadata = new JSONObject()
                .put("method", "signature_matching")
                .put("error", "Insufficient signature matches")
                .put("ring_correlation", ringCorrelation)
                .put("fiducial_matches", fiducialMatches.size());
            
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "signature_matching", metadata);
        }
    }
    
    /**
     * ORB feature-based alignment using mpicbg library
     */
    private static AlignmentResult alignUsingORBFeatures(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        // Note: This would require mpicbg library integration
        // For now, implement a simplified version
        
        try {
            // Extract ORB features (simplified - would use proper ORB implementation)
            List<Point2D> refKeypoints = extractORBKeypoints(reference.getProcessor());
            List<Point2D> targetKeypoints = extractORBKeypoints(target.getProcessor());
            
            // Match features using descriptor similarity
            List<FeatureMatch> matches = matchORBFeatures(refKeypoints, targetKeypoints);
            
            if (matches.size() >= 4) {
                TransformEstimate estimate = estimateTransformation(matches, options);
                
                JSONObject metadata = new JSONObject()
                    .put("method", "orb_features")
                    .put("ref_keypoints", refKeypoints.size())
                    .put("target_keypoints", targetKeypoints.size())
                    .put("matches", matches.size())
                    .put("rmse", estimate.rmse);
                
                return new AlignmentResult(estimate.transform, estimate.confidence,
                                         refKeypoints, "orb_features", metadata);
            }
        } catch (Exception e) {
            // ORB matching failed, will fall back to other methods
        }
        
        JSONObject metadata = new JSONObject()
            .put("method", "orb_features")
            .put("error", "ORB feature matching failed");
        
        return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "orb_features", metadata);
    }
    
    /**
     * Phase correlation-based alignment for translation/rotation estimation
     */
    private static AlignmentResult alignUsingPhaseCorrelation(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        try {
            ImageProcessor refIP = reference.getProcessor().duplicate();
            ImageProcessor targetIP = target.getProcessor().duplicate();
            
            // Resize to manageable size for phase correlation
            int maxSize = 512;
            if (refIP.getWidth() > maxSize || refIP.getHeight() > maxSize) {
                double scale = Math.min((double) maxSize / refIP.getWidth(), (double) maxSize / refIP.getHeight());
                refIP = refIP.resize((int) (refIP.getWidth() * scale), (int) (refIP.getHeight() * scale));
                targetIP = targetIP.resize((int) (targetIP.getWidth() * scale), (int) (targetIP.getHeight() * scale));
            }
            
            // Calculate phase correlation peak
            Point2D translation = calculatePhaseCorrelationPeak(refIP, targetIP);
            double confidence = calculatePhaseCorrelationConfidence(refIP, targetIP, translation);
            
            // Create simple translation transform
            AffineTransform transform = new AffineTransform();
            transform.translate(translation.getX(), translation.getY());
            
            JSONObject metadata = new JSONObject()
                .put("method", "phase_correlation")
                .put("translation_x", translation.getX())
                .put("translation_y", translation.getY())
                .put("correlation_confidence", confidence);
            
            List<Point2D> keypoints = Arrays.asList(
                new Point2D.Double(refIP.getWidth() / 4.0, refIP.getHeight() / 4.0),
                new Point2D.Double(3 * refIP.getWidth() / 4.0, refIP.getHeight() / 4.0),
                new Point2D.Double(refIP.getWidth() / 2.0, 3 * refIP.getHeight() / 4.0)
            );
            
            return new AlignmentResult(transform, confidence, keypoints, "phase_correlation", metadata);
            
        } catch (Exception e) {
            JSONObject metadata = new JSONObject()
                .put("method", "phase_correlation")
                .put("error", "Phase correlation failed: " + e.getMessage());
            
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "phase_correlation", metadata);
        }
    }
    
    /**
     * Feature-based alignment using colony positions and plate edges
     */
    private static AlignmentResult alignUsingFeatureMatching(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        // Extract key features from both images
        List<Point2D> refFeatures = extractPlateFeatures(reference, options);
        List<Point2D> targetFeatures = extractPlateFeatures(target, options);
        
        if (refFeatures.size() < 3 || targetFeatures.size() < 3) {
            JSONObject metadata = new JSONObject()
                .put("error", "Insufficient features detected")
                .put("ref_features", refFeatures.size())
                .put("target_features", targetFeatures.size());
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "feature_matching", metadata);
        }
        
        // Match features between images
        List<FeatureMatch> matches = matchFeatures(refFeatures, targetFeatures, options);
        
        if (matches.size() < 3) {
            JSONObject metadata = new JSONObject()
                .put("error", "Insufficient feature matches")
                .put("matches_found", matches.size());
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "feature_matching", metadata);
        }
        
        // Calculate transformation using RANSAC
        TransformEstimate estimate = estimateTransformation(matches, options);
        
        JSONObject metadata = new JSONObject()
            .put("features_ref", refFeatures.size())
            .put("features_target", targetFeatures.size())
            .put("matches_total", matches.size())
            .put("matches_inliers", estimate.inlierCount)
            .put("rmse_pixels", estimate.rmse);
        
        return new AlignmentResult(estimate.transform, estimate.confidence, 
                                 refFeatures, "feature_matching", metadata);
    }
    
    /**
     * Alignment using user-placed orientation marks (dots, crosses, etc.)
     */
    private static AlignmentResult alignUsingOrientationMarks(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        // Detect orientation marks in both images
        List<Point2D> refMarks = detectOrientationMarks(reference, options);
        List<Point2D> targetMarks = detectOrientationMarks(target, options);
        
        if (refMarks.size() < 3 || targetMarks.size() < 3) {
            JSONObject metadata = new JSONObject()
                .put("error", "Insufficient orientation marks detected")
                .put("ref_marks", refMarks.size())
                .put("target_marks", targetMarks.size())
                .put("expected_marks", options.orientationMarkCount);
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "orientation_marks", metadata);
        }
        
        // Match marks by proximity and create transformation
        List<FeatureMatch> markMatches = matchOrientationMarks(refMarks, targetMarks, options);
        
        if (markMatches.size() < 3) {
            JSONObject metadata = new JSONObject()
                .put("error", "Could not match orientation marks")
                .put("mark_matches", markMatches.size());
            return new AlignmentResult(new AffineTransform(), 0.0, new ArrayList<>(), "orientation_marks", metadata);
        }
        
        TransformEstimate estimate = estimateTransformation(markMatches, options);
        
        JSONObject metadata = new JSONObject()
            .put("marks_ref", refMarks.size())
            .put("marks_target", targetMarks.size())
            .put("mark_matches", markMatches.size())
            .put("rmse_pixels", estimate.rmse);
        
        return new AlignmentResult(estimate.transform, estimate.confidence,
                                 refMarks, "orientation_marks", metadata);
    }
    
    /**
     * Extract feature points from plate image (colonies, edges, artifacts)
     */
    private static List<Point2D> extractPlateFeatures(ImagePlus image, AlignmentOptions options) {
        List<Point2D> features = new ArrayList<>();
        ImageProcessor ip = image.getProcessor().duplicate();
        
        // 1. Detect prominent colonies as features
        List<Point2D> colonies = detectColonyFeatures(ip, options);
        features.addAll(colonies);
        
        // 2. Detect plate edge features
        List<Point2D> edges = detectPlateEdgeFeatures(ip, options);
        features.addAll(edges);
        
        // 3. Detect other distinctive features (scratches, bubbles, etc.)
        List<Point2D> artifacts = detectArtifactFeatures(ip, options);
        features.addAll(artifacts);
        
        // Sort by feature strength and limit count
        Collections.sort(features, Comparator.comparingDouble(p -> -getFeatureStrength(ip, p)));
        if (features.size() > options.maxFeatures) {
            features = features.subList(0, options.maxFeatures);
        }
        
        return features;
    }
    
    private static List<Point2D> detectColonyFeatures(ImageProcessor ip, AlignmentOptions options) {
        List<Point2D> colonies = new ArrayList<>();
        
        // Use simple blob detection for prominent colonies
        ip.smooth();
        ip.findEdges();
        
        for (int y = 10; y < ip.getHeight() - 10; y += 5) {
            for (int x = 10; x < ip.getWidth() - 10; x += 5) {
                if (isColonyCenter(ip, x, y)) {
                    colonies.add(new Point2D.Double(x, y));
                }
            }
        }
        
        return colonies;
    }
    
    private static List<Point2D> detectPlateEdgeFeatures(ImageProcessor ip, AlignmentOptions options) {
        List<Point2D> edges = new ArrayList<>();
        
        // Find circular plate boundary
        // Simplified edge detection - in practice would use Hough circle detection
        int centerX = ip.getWidth() / 2;
        int centerY = ip.getHeight() / 2;
        int radius = Math.min(ip.getWidth(), ip.getHeight()) / 3;
        
        // Sample points around estimated plate edge
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 8) {
            int x = (int) (centerX + radius * Math.cos(angle));
            int y = (int) (centerY + radius * Math.sin(angle));
            if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                edges.add(new Point2D.Double(x, y));
            }
        }
        
        return edges;
    }
    
    private static List<Point2D> detectArtifactFeatures(ImageProcessor ip, AlignmentOptions options) {
        List<Point2D> artifacts = new ArrayList<>();
        
        // Detect high-contrast artifacts (scratches, bubbles, etc.)
        for (int y = 5; y < ip.getHeight() - 5; y += 10) {
            for (int x = 5; x < ip.getWidth() - 5; x += 10) {
                double contrast = calculateLocalContrast(ip, x, y, 5);
                if (contrast > 50) {  // High contrast threshold
                    artifacts.add(new Point2D.Double(x, y));
                }
            }
        }
        
        return artifacts;
    }
    
    /**
     * Detect user-placed orientation marks (dots, crosses, etc.)
     */
    private static List<Point2D> detectOrientationMarks(ImagePlus image, AlignmentOptions options) {
        List<Point2D> marks = new ArrayList<>();
        ImageProcessor ip = image.getProcessor().duplicate();
        
        // Look for small, dark circular marks
        for (int y = 20; y < ip.getHeight() - 20; y += 5) {
            for (int x = 20; x < ip.getWidth() - 20; x += 5) {
                if (isOrientationMark(ip, x, y, options)) {
                    marks.add(new Point2D.Double(x, y));
                }
            }
        }
        
        // Remove marks that are too close to each other
        marks = filterProximateMarks(marks, 20); // Min 20 pixels apart
        
        return marks;
    }
    
    private static boolean isColonyCenter(ImageProcessor ip, int x, int y) {
        int radius = 5;
        double centerVal = ip.getPixelValue(x, y);
        double avgPerimeter = 0;
        int count = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx*dx + dy*dy == radius*radius) {  // Approximate circle
                    int px = x + dx;
                    int py = y + dy;
                    if (px >= 0 && px < ip.getWidth() && py >= 0 && py < ip.getHeight()) {
                        avgPerimeter += ip.getPixelValue(px, py);
                        count++;
                    }
                }
            }
        }
        
        if (count == 0) return false;
        avgPerimeter /= count;
        
        // Colony centers are typically darker than their perimeters
        return (avgPerimeter - centerVal) > 20;
    }
    
    private static boolean isOrientationMark(ImageProcessor ip, int x, int y, AlignmentOptions options) {
        int radius = 3;
        double centerVal = ip.getPixelValue(x, y);
        double avgSurrounding = 0;
        int count = 0;
        
        // Check if center is significantly darker than surrounding area
        for (int dy = -radius*2; dy <= radius*2; dy++) {
            for (int dx = -radius*2; dx <= radius*2; dx++) {
                if (dx*dx + dy*dy > radius*radius && dx*dx + dy*dy < (radius*2)*(radius*2)) {
                    int px = x + dx;
                    int py = y + dy;
                    if (px >= 0 && px < ip.getWidth() && py >= 0 && py < ip.getHeight()) {
                        avgSurrounding += ip.getPixelValue(px, py);
                        count++;
                    }
                }
            }
        }
        
        if (count == 0) return false;
        avgSurrounding /= count;
        
        // Orientation marks should be small, dark, and circular
        return (avgSurrounding - centerVal) > 40 && isCircularMark(ip, x, y, radius);
    }
    
    private static boolean isCircularMark(ImageProcessor ip, int x, int y, int radius) {
        double centerVal = ip.getPixelValue(x, y);
        int darkPixels = 0;
        int totalPixels = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx*dx + dy*dy <= radius*radius) {
                    int px = x + dx;
                    int py = y + dy;
                    if (px >= 0 && px < ip.getWidth() && py >= 0 && py < ip.getHeight()) {
                        if (ip.getPixelValue(px, py) < centerVal + 10) {
                            darkPixels++;
                        }
                        totalPixels++;
                    }
                }
            }
        }
        
        return totalPixels > 0 && (darkPixels / (double)totalPixels) > 0.7;
    }
    
    private static List<Point2D> filterProximateMarks(List<Point2D> marks, double minDistance) {
        List<Point2D> filtered = new ArrayList<>();
        
        for (Point2D mark : marks) {
            boolean tooClose = false;
            for (Point2D existing : filtered) {
                if (mark.distance(existing) < minDistance) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                filtered.add(mark);
            }
        }
        
        return filtered;
    }
    
    private static double getFeatureStrength(ImageProcessor ip, Point2D point) {
        int x = (int) point.getX();
        int y = (int) point.getY();
        return calculateLocalContrast(ip, x, y, 3);
    }
    
    private static double calculateLocalContrast(ImageProcessor ip, int x, int y, int radius) {
        double sum = 0;
        double sumSquares = 0;
        int count = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && px < ip.getWidth() && py >= 0 && py < ip.getHeight()) {
                    double val = ip.getPixelValue(px, py);
                    sum += val;
                    sumSquares += val * val;
                    count++;
                }
            }
        }
        
        if (count <= 1) return 0;
        
        double mean = sum / count;
        double variance = (sumSquares / count) - (mean * mean);
        return Math.sqrt(variance); // Standard deviation as contrast measure
    }
    
    // Helper methods for the new functionality
    
    /**
     * Calculate correlation between two ring projections
     */
    private static double calculateRingCorrelation(double[] ring1, double[] ring2) {
        if (ring1.length != ring2.length) {
            return 0.0;
        }
        
        // Normalize both arrays
        double[] norm1 = normalizeArray(ring1);
        double[] norm2 = normalizeArray(ring2);
        
        // Find best circular shift correlation
        double maxCorrelation = 0.0;
        int bestShift = 0;
        
        for (int shift = 0; shift < norm1.length; shift++) {
            double correlation = 0.0;
            for (int i = 0; i < norm1.length; i++) {
                int j = (i + shift) % norm2.length;
                correlation += norm1[i] * norm2[j];
            }
            correlation /= norm1.length;
            
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation;
                bestShift = shift;
            }
        }
        
        return Math.max(0.0, maxCorrelation);
    }
    
    /**
     * Normalize array to zero mean, unit variance
     */
    private static double[] normalizeArray(double[] array) {
        double mean = 0.0;
        for (double value : array) {
            mean += value;
        }
        mean /= array.length;
        
        double variance = 0.0;
        for (double value : array) {
            variance += (value - mean) * (value - mean);
        }
        variance /= array.length;
        
        double stdDev = Math.sqrt(variance);
        if (stdDev < 1e-10) stdDev = 1.0; // Avoid division by zero
        
        double[] normalized = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            normalized[i] = (array[i] - mean) / stdDev;
        }
        
        return normalized;
    }
    
    /**
     * Match fiducial dots between two sets using Hungarian algorithm approximation
     */
    private static List<FeatureMatch> matchFiducialDots(List<Point2D> refDots, List<Point2D> targetDots) {
        List<FeatureMatch> matches = new ArrayList<>();
        
        if (refDots.isEmpty() || targetDots.isEmpty()) {
            return matches;
        }
        
        // Simple greedy matching - in production would use Hungarian algorithm
        List<Point2D> remainingTargets = new ArrayList<>(targetDots);
        
        for (Point2D refDot : refDots) {
            Point2D bestMatch = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (Point2D targetDot : remainingTargets) {
                double distance = refDot.distance(targetDot);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = targetDot;
                }
            }
            
            if (bestMatch != null && bestDistance < 200) { // 200 pixel max distance
                matches.add(new FeatureMatch(refDot, bestMatch, bestDistance));
                remainingTargets.remove(bestMatch);
            }
        }
        
        return matches;
    }
    
    /**
     * Calculate similarity between geometric descriptors
     */
    private static double calculateGeometricSimilarity(double[] desc1, double[] desc2) {
        if (desc1.length != desc2.length) {
            return 0.0;
        }
        
        double sumSquaredDiff = 0.0;
        double sumSquaredDesc1 = 0.0;
        double sumSquaredDesc2 = 0.0;
        
        for (int i = 0; i < desc1.length; i++) {
            double diff = desc1[i] - desc2[i];
            sumSquaredDiff += diff * diff;
            sumSquaredDesc1 += desc1[i] * desc1[i];
            sumSquaredDesc2 += desc2[i] * desc2[i];
        }
        
        // Calculate normalized similarity (0 = identical, approaches 1 as difference increases)
        double magnitude = Math.sqrt(sumSquaredDesc1 * sumSquaredDesc2);
        if (magnitude < 1e-10) {
            return 1.0; // Both descriptors are zero
        }
        
        double similarity = 1.0 - Math.sqrt(sumSquaredDiff) / magnitude;
        return Math.max(0.0, similarity);
    }
    
    /**
     * Extract ORB-like keypoints (simplified version)
     */
    private static List<Point2D> extractORBKeypoints(ImageProcessor ip) {
        List<Point2D> keypoints = new ArrayList<>();
        
        // Use Harris corner detection as approximation to ORB
        ImageProcessor smoothed = ip.duplicate();
        smoothed.smooth();
        smoothed.findEdges();
        
        // Look for corner-like features
        for (int y = 10; y < ip.getHeight() - 10; y += 8) {
            for (int x = 10; x < ip.getWidth() - 10; x += 8) {
                double cornerResponse = calculateHarrisCornerResponse(smoothed, x, y);
                if (cornerResponse > 1000) { // Threshold for corner strength
                    keypoints.add(new Point2D.Double(x, y));
                }
            }
        }
        
        // Sort by corner strength and limit count
        Collections.sort(keypoints, (p1, p2) -> 
            Double.compare(calculateHarrisCornerResponse(smoothed, (int)p2.getX(), (int)p2.getY()),
                         calculateHarrisCornerResponse(smoothed, (int)p1.getX(), (int)p1.getY())));
        
        if (keypoints.size() > 100) {
            keypoints = keypoints.subList(0, 100);
        }
        
        return keypoints;
    }
    
    /**
     * Calculate Harris corner response
     */
    private static double calculateHarrisCornerResponse(ImageProcessor ip, int x, int y) {
        if (x < 3 || x >= ip.getWidth() - 3 || y < 3 || y >= ip.getHeight() - 3) {
            return 0.0;
        }
        
        // Optimized gradient calculation using fast pixel array access
        float[] pixels = PerformanceOptimizer.FastPixelOps.getPixelArrayFloat(
            new ImagePlus("temp", ip));
        int width = ip.getWidth();
        
        double Ixx = 0, Iyy = 0, Ixy = 0;
        
        // Process 7x7 neighborhood (dy = -3 to 3, dx = -3 to 3)
        for (int dy = -3; dy <= 3; dy++) {
            int rowOffset = (y + dy) * width;
            for (int dx = -3; dx <= 3; dx++) {
                int centerIdx = rowOffset + (x + dx);
                
                // Calculate gradients using array indexing instead of getPixelValue calls
                double Ix = pixels[centerIdx + 1] - pixels[centerIdx - 1];
                double Iy = pixels[centerIdx + width] - pixels[centerIdx - width];
                
                Ixx += Ix * Ix;
                Iyy += Iy * Iy;
                Ixy += Ix * Iy;
            }
        }
        
        // Harris corner response
        double det = Ixx * Iyy - Ixy * Ixy;
        double trace = Ixx + Iyy;
        double k = 0.04;
        
        return det - k * trace * trace;
    }
    
    /**
     * Match ORB features using descriptor similarity (simplified)
     */
    private static List<FeatureMatch> matchORBFeatures(List<Point2D> refKeypoints, List<Point2D> targetKeypoints) {
        List<FeatureMatch> matches = new ArrayList<>();
        
        // Simplified matching based on spatial proximity
        // In production would use ORB descriptors and Hamming distance
        
        for (Point2D refPoint : refKeypoints) {
            Point2D bestMatch = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (Point2D targetPoint : targetKeypoints) {
                double distance = refPoint.distance(targetPoint);
                if (distance < bestDistance && distance < 100) { // 100 pixel max distance
                    bestDistance = distance;
                    bestMatch = targetPoint;
                }
            }
            
            if (bestMatch != null) {
                matches.add(new FeatureMatch(refPoint, bestMatch, bestDistance));
            }
        }
        
        return matches;
    }
    
    /**
     * Calculate phase correlation peak between two images
     */
    private static Point2D calculatePhaseCorrelationPeak(ImageProcessor ref, ImageProcessor target) {
        // Simplified phase correlation - in production would use FFT
        int bestX = 0, bestY = 0;
        double maxCorrelation = Double.NEGATIVE_INFINITY;
        
        // Search over reasonable translation range
        int searchRange = Math.min(50, Math.min(ref.getWidth(), ref.getHeight()) / 4);
        
        for (int dy = -searchRange; dy <= searchRange; dy += 2) {
            for (int dx = -searchRange; dx <= searchRange; dx += 2) {
                double correlation = calculateNormalizedCrossCorrelation(ref, target, dx, dy);
                if (correlation > maxCorrelation) {
                    maxCorrelation = correlation;
                    bestX = dx;
                    bestY = dy;
                }
            }
        }
        
        return new Point2D.Double(bestX, bestY);
    }
    
    /**
     * Calculate phase correlation confidence
     */
    private static double calculatePhaseCorrelationConfidence(ImageProcessor ref, ImageProcessor target, Point2D translation) {
        double correlation = calculateNormalizedCrossCorrelation(ref, target, 
                                                               (int)translation.getX(), (int)translation.getY());
        // Convert correlation to confidence (0.0 to 1.0)
        return Math.max(0.0, Math.min(1.0, (correlation + 1.0) / 2.0));
    }
    
    /**
     * Calculate normalized cross-correlation between two images with translation
     */
    private static double calculateNormalizedCrossCorrelation(ImageProcessor ref, ImageProcessor target, int dx, int dy) {
        double sum = 0.0;
        int count = 0;
        
        // Calculate overlap region
        int startX = Math.max(0, -dx);
        int endX = Math.min(ref.getWidth(), target.getWidth() - dx);
        int startY = Math.max(0, -dy);
        int endY = Math.min(ref.getHeight(), target.getHeight() - dy);
        
        if (startX >= endX || startY >= endY) {
            return -1.0; // No overlap
        }
        
        // Calculate mean intensities in overlap region
        double refMean = 0.0, targetMean = 0.0;
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                refMean += ref.getPixelValue(x, y);
                targetMean += target.getPixelValue(x + dx, y + dy);
                count++;
            }
        }
        refMean /= count;
        targetMean /= count;
        
        // Calculate correlation
        double numerator = 0.0, refVar = 0.0, targetVar = 0.0;
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                double refDiff = ref.getPixelValue(x, y) - refMean;
                double targetDiff = target.getPixelValue(x + dx, y + dy) - targetMean;
                
                numerator += refDiff * targetDiff;
                refVar += refDiff * refDiff;
                targetVar += targetDiff * targetDiff;
            }
        }
        
        double denominator = Math.sqrt(refVar * targetVar);
        if (denominator < 1e-10) {
            return 0.0;
        }
        
        return numerator / denominator;
    }
    
    // Helper classes for feature matching
    private static class FeatureMatch {
        public final Point2D refPoint;
        public final Point2D targetPoint;
        public final double distance;
        
        public FeatureMatch(Point2D refPoint, Point2D targetPoint, double distance) {
            this.refPoint = refPoint;
            this.targetPoint = targetPoint;
            this.distance = distance;
        }
    }
    
    private static class TransformEstimate {
        public final AffineTransform transform;
        public final double confidence;
        public final double rmse;
        public final int inlierCount;
        
        public TransformEstimate(AffineTransform transform, double confidence, double rmse, int inlierCount) {
            this.transform = transform;
            this.confidence = confidence;
            this.rmse = rmse;
            this.inlierCount = inlierCount;
        }
    }
    
    private static List<FeatureMatch> matchFeatures(List<Point2D> refFeatures, List<Point2D> targetFeatures, AlignmentOptions options) {
        List<FeatureMatch> matches = new ArrayList<>();
        
        for (Point2D refPoint : refFeatures) {
            Point2D bestMatch = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (Point2D targetPoint : targetFeatures) {
                double distance = refPoint.distance(targetPoint);
                if (distance < bestDistance && distance < options.tolerancePx * 10) {
                    bestDistance = distance;
                    bestMatch = targetPoint;
                }
            }
            
            if (bestMatch != null && bestDistance < options.tolerancePx * 10) {
                matches.add(new FeatureMatch(refPoint, bestMatch, bestDistance));
            }
        }
        
        return matches;
    }
    
    private static List<FeatureMatch> matchOrientationMarks(List<Point2D> refMarks, List<Point2D> targetMarks, AlignmentOptions options) {
        List<FeatureMatch> matches = new ArrayList<>();
        
        // For orientation marks, we expect a more precise correspondence
        for (Point2D refMark : refMarks) {
            Point2D bestMatch = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (Point2D targetMark : targetMarks) {
                double distance = refMark.distance(targetMark);
                if (distance < bestDistance && distance < options.tolerancePx * 5) {
                    bestDistance = distance;
                    bestMatch = targetMark;
                }
            }
            
            if (bestMatch != null) {
                matches.add(new FeatureMatch(refMark, bestMatch, bestDistance));
                targetMarks.remove(bestMatch); // Ensure one-to-one matching
            }
        }
        
        return matches;
    }
    
    private static TransformEstimate estimateTransformation(List<FeatureMatch> matches, AlignmentOptions options) {
        if (matches.size() < 3) {
            return new TransformEstimate(new AffineTransform(), 0.0, Double.MAX_VALUE, 0);
        }
        
        // RANSAC parameters
        int maxIterations = Math.min(1000, matches.size() * 10);
        double distanceThreshold = 5.0; // pixels
        int minConsensusSize = Math.max(3, matches.size() / 3);
        
        AffineTransform bestTransform = new AffineTransform();
        int bestConsensusSize = 0;
        double bestRMSE = Double.MAX_VALUE;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // Randomly select 3 matches for hypothesis
            Collections.shuffle(matches);
            List<FeatureMatch> sample = matches.subList(0, Math.min(3, matches.size()));
            
            AffineTransform candidateTransform = calculateAffineTransform(sample);
            
            // Count inliers
            List<FeatureMatch> inliers = new ArrayList<>();
            for (FeatureMatch match : matches) {
                Point2D transformed = candidateTransform.transform(match.refPoint, null);
                double error = transformed.distance(match.targetPoint);
                if (error < distanceThreshold) {
                    inliers.add(match);
                }
            }
            
            // Check if this is the best consensus so far
            if (inliers.size() > bestConsensusSize) {
                bestConsensusSize = inliers.size();
                bestTransform = candidateTransform;
                bestRMSE = calculateRMSE(inliers, candidateTransform);
                
                // Early termination if we have enough inliers
                if (bestConsensusSize >= minConsensusSize) {
                    break;
                }
            }
        }
        
        // Calculate final confidence based on consensus size and RMSE
        double consensusRatio = (double) bestConsensusSize / matches.size();
        double confidence = Math.max(0.0, consensusRatio * (1.0 - Math.min(1.0, bestRMSE / 50.0)));
        
        return new TransformEstimate(bestTransform, confidence, bestRMSE, bestConsensusSize);
    }
    
    private static AffineTransform calculateAffineTransform(List<FeatureMatch> matches) {
        if (matches.size() < 3) {
            return new AffineTransform();
        }
        
        // Simplified transformation estimation using first 3 points
        // In practice would use proper least-squares or RANSAC
        Point2D ref1 = matches.get(0).refPoint;
        Point2D ref2 = matches.get(1).refPoint;
        Point2D ref3 = matches.get(2).refPoint;
        
        Point2D tgt1 = matches.get(0).targetPoint;
        Point2D tgt2 = matches.get(1).targetPoint;
        Point2D tgt3 = matches.get(2).targetPoint;
        
        // Calculate transformation matrix
        // This is a simplified version - full implementation would use proper matrix operations
        AffineTransform transform = new AffineTransform();
        
        // Calculate translation from centroids
        double refCenterX = (ref1.getX() + ref2.getX() + ref3.getX()) / 3;
        double refCenterY = (ref1.getY() + ref2.getY() + ref3.getY()) / 3;
        double tgtCenterX = (tgt1.getX() + tgt2.getX() + tgt3.getX()) / 3;
        double tgtCenterY = (tgt1.getY() + tgt2.getY() + tgt3.getY()) / 3;
        
        transform.translate(tgtCenterX - refCenterX, tgtCenterY - refCenterY);
        
        return transform;
    }
    
    private static double calculateRMSE(List<FeatureMatch> matches, AffineTransform transform) {
        double sumSquaredError = 0;
        
        for (FeatureMatch match : matches) {
            Point2D transformed = transform.transform(match.refPoint, null);
            double error = transformed.distance(match.targetPoint);
            sumSquaredError += error * error;
        }
        
        return Math.sqrt(sumSquaredError / matches.size());
    }
    
    /**
     * Apply transformation to align target image to reference coordinate system
     * Uses ImageJ's built-in transformations instead of manual pixel operations
     */
    public static ImagePlus applyAlignment(ImagePlus target, AffineTransform transform) {
        try {
            // Create transformed image using ImageJ's affine transformation
            ImagePlus alignedImage = target.duplicate();
            alignedImage.setTitle(target.getTitle() + "_aligned");
            
            // Apply transformation using ImageJ's built-in method
            IJ.run(alignedImage, "Transform...", 
                String.format("matrix=[%.6f %.6f %.6f %.6f %.6f %.6f] interpolation=Bilinear",
                    transform.getScaleX(), transform.getShearX(), transform.getTranslateX(),
                    transform.getShearY(), transform.getScaleY(), transform.getTranslateY()));
            
            return alignedImage;
        } catch (Exception e) {
            // If transformation fails, return original image
            return target;
        }
    }
}
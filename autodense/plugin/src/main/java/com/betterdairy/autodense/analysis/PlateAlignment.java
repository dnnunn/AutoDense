package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.json.JSONObject;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

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
     * Aligns a target image to match a reference image
     * @param reference Reference image (first time point)
     * @param target Target image to be aligned
     * @param options Alignment parameters
     * @return Alignment result with transformation matrix
     */
    public static AlignmentResult alignPlates(ImagePlus reference, ImagePlus target, AlignmentOptions options) {
        if (options.method.equals("orientation_marks")) {
            return alignUsingOrientationMarks(reference, target, options);
        } else {
            return alignUsingFeatureMatching(reference, target, options);
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
        
        // Simple least-squares estimation (in practice would use RANSAC)
        // For now, use the first 3 matches to estimate transformation
        AffineTransform transform = calculateAffineTransform(matches.subList(0, Math.min(3, matches.size())));
        
        // Calculate RMSE and confidence
        double rmse = calculateRMSE(matches, transform);
        double confidence = Math.max(0.0, 1.0 - (rmse / 50.0)); // Normalize RMSE to confidence
        
        return new TransformEstimate(transform, confidence, rmse, matches.size());
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
     */
    public static ImagePlus applyAlignment(ImagePlus target, AffineTransform transform) {
        ImageProcessor ip = target.getProcessor();
        ImageProcessor aligned = ip.createProcessor(ip.getWidth(), ip.getHeight());
        
        // Apply inverse transformation to map pixels from aligned space to original space
        try {
            AffineTransform inverse = transform.createInverse();
            
            for (int y = 0; y < aligned.getHeight(); y++) {
                for (int x = 0; x < aligned.getWidth(); x++) {
                    Point2D srcPoint = inverse.transform(new Point2D.Double(x, y), null);
                    int srcX = (int) Math.round(srcPoint.getX());
                    int srcY = (int) Math.round(srcPoint.getY());
                    
                    if (srcX >= 0 && srcX < ip.getWidth() && srcY >= 0 && srcY < ip.getHeight()) {
                        aligned.putPixel(x, y, ip.getPixel(srcX, srcY));
                    }
                }
            }
        } catch (Exception e) {
            // If transformation fails, return original image
            return target;
        }
        
        ImagePlus alignedImage = new ImagePlus(target.getTitle() + "_aligned", aligned);
        alignedImage.setCalibration(target.getCalibration());
        return alignedImage;
    }
}
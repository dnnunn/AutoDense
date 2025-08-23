package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.geom.Point2D;
import java.util.*;
import java.time.LocalDateTime;

/**
 * Tracks individual colonies across multiple time points for growth analysis.
 * Handles colony matching, growth rate calculation, and morphology changes.
 */
public class TimeSeriesColonyTracker {
    
    public static class TimePoint {
        public final LocalDateTime timestamp;
        public final ImagePlus image;
        public final List<Colony> colonies;
        public final JSONObject metadata;
        
        public TimePoint(LocalDateTime timestamp, ImagePlus image, List<Colony> colonies) {
            this.timestamp = timestamp;
            this.image = image;
            this.colonies = colonies;
            this.metadata = new JSONObject()
                .put("timestamp", timestamp.toString())
                .put("colony_count", colonies.size());
        }
    }
    
    public static class Colony {
        public final String id;
        public final Point2D center;
        public final double area;  // in mm²
        public final double diameter;  // in mm
        public final ColonyMorphology morphology;
        public final double blueness;  // X-gal blueness score (0-1)
        public final double confidence;
        public final JSONObject rawData;
        
        public Colony(String id, Point2D center, double area, double diameter, 
                     ColonyMorphology morphology, double blueness, double confidence, 
                     JSONObject rawData) {
            this.id = id;
            this.center = center;
            this.area = area;
            this.diameter = diameter;
            this.morphology = morphology;
            this.blueness = blueness;
            this.confidence = confidence;
            this.rawData = rawData;
        }
    }
    
    public static class ColonyMorphology {
        public final double circularity;  // 0-1, 1 = perfect circle
        public final double solidity;     // ratio of area to convex hull area
        public final double aspectRatio;  // major axis / minor axis
        public final double textureVariance; // measure of surface roughness
        public final String edgeType;     // "smooth", "rough", "irregular"
        
        public ColonyMorphology(double circularity, double solidity, double aspectRatio,
                               double textureVariance, String edgeType) {
            this.circularity = circularity;
            this.solidity = solidity;
            this.aspectRatio = aspectRatio;
            this.textureVariance = textureVariance;
            this.edgeType = edgeType;
        }
        
        public JSONObject toJSON() {
            return new JSONObject()
                .put("circularity", circularity)
                .put("solidity", solidity)
                .put("aspect_ratio", aspectRatio)
                .put("texture_variance", textureVariance)
                .put("edge_type", edgeType);
        }
    }
    
    public static class ColonyTrack {
        public final String trackId;
        public final List<Colony> timePoints;
        public final Map<String, Double> growthRates;  // growth rate per hour
        public final JSONObject statistics;
        
        public ColonyTrack(String trackId) {
            this.trackId = trackId;
            this.timePoints = new ArrayList<>();
            this.growthRates = new HashMap<>();
            this.statistics = new JSONObject();
        }
        
        public void addTimePoint(Colony colony) {
            timePoints.add(colony);
            updateStatistics();
        }
        
        private void updateStatistics() {
            if (timePoints.size() < 2) return;
            
            // Calculate various growth metrics
            Colony first = timePoints.get(0);
            Colony last = timePoints.get(timePoints.size() - 1);
            
            double totalAreaGrowth = last.area - first.area;
            double totalDiameterGrowth = last.diameter - first.diameter;
            double maxArea = timePoints.stream().mapToDouble(c -> c.area).max().orElse(0);
            double avgCircularity = timePoints.stream().mapToDouble(c -> c.morphology.circularity).average().orElse(0);
            
            statistics.put("total_area_growth_mm2", totalAreaGrowth);
            statistics.put("total_diameter_growth_mm", totalDiameterGrowth);
            statistics.put("max_area_mm2", maxArea);
            statistics.put("avg_circularity", avgCircularity);
            statistics.put("time_points_count", timePoints.size());
            
            // Calculate blueness progression if X-gal analysis enabled
            if (timePoints.stream().anyMatch(c -> c.blueness > 0)) {
                double maxBlueness = timePoints.stream().mapToDouble(c -> c.blueness).max().orElse(0);
                double finalBlueness = last.blueness;
                statistics.put("max_blueness", maxBlueness);
                statistics.put("final_blueness", finalBlueness);
            }
        }
        
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("track_id", trackId);
            json.put("statistics", statistics);
            
            JSONArray timePointsArray = new JSONArray();
            for (Colony colony : timePoints) {
                JSONObject colonyJson = new JSONObject()
                    .put("id", colony.id)
                    .put("center_x", colony.center.getX())
                    .put("center_y", colony.center.getY())
                    .put("area_mm2", colony.area)
                    .put("diameter_mm", colony.diameter)
                    .put("morphology", colony.morphology.toJSON())
                    .put("blueness", colony.blueness)
                    .put("confidence", colony.confidence);
                timePointsArray.put(colonyJson);
            }
            json.put("time_points", timePointsArray);
            
            return json;
        }
    }
    
    public static class TrackingOptions {
        public double maxMatchingDistance = 10.0;  // pixels
        public double sizeChangeThreshold = 2.0;   // max diameter ratio between time points
        public double morphologyThreshold = 0.3;   // max morphology change
        public boolean trackNewColonies = true;    // track colonies that appear mid-series
        public boolean allowColonyMerging = false; // handle merging colonies
        public boolean allowColonySplitting = false; // handle splitting colonies
        public int maxGapFrames = 2;               // max frames a colony can disappear
    }
    
    private final List<TimePoint> timePoints;
    private final Map<String, ColonyTrack> tracks;
    private final TrackingOptions options;
    private int nextTrackId = 1;
    
    public TimeSeriesColonyTracker(TrackingOptions options) {
        this.timePoints = new ArrayList<>();
        this.tracks = new HashMap<>();
        this.options = options != null ? options : new TrackingOptions();
    }
    
    /**
     * Add a new time point with detected colonies
     */
    public void addTimePoint(LocalDateTime timestamp, ImagePlus image, List<Colony> colonies) {
        TimePoint timePoint = new TimePoint(timestamp, image, colonies);
        timePoints.add(timePoint);
        
        if (timePoints.size() == 1) {
            // First time point - initialize tracks
            initializeTracks(colonies);
        } else {
            // Match colonies with existing tracks
            matchColoniesToTracks(colonies);
        }
        
        updateGrowthRates();
    }
    
    private void initializeTracks(List<Colony> colonies) {
        for (Colony colony : colonies) {
            String trackId = "track_" + (nextTrackId++);
            ColonyTrack track = new ColonyTrack(trackId);
            track.addTimePoint(colony);
            tracks.put(trackId, track);
        }
    }
    
    private void matchColoniesToTracks(List<Colony> colonies) {
        List<Colony> unmatchedColonies = new ArrayList<>(colonies);
        List<String> unmatchedTracks = new ArrayList<>(tracks.keySet());
        
        // Create cost matrix for Hungarian algorithm (simplified version)
        List<TrackMatch> matches = findBestMatches(unmatchedTracks, unmatchedColonies);
        
        // Apply matches
        for (TrackMatch match : matches) {
            tracks.get(match.trackId).addTimePoint(match.colony);
            unmatchedColonies.remove(match.colony);
            unmatchedTracks.remove(match.trackId);
        }
        
        // Handle unmatched colonies (new colonies)
        if (options.trackNewColonies) {
            for (Colony colony : unmatchedColonies) {
                String trackId = "track_" + (nextTrackId++);
                ColonyTrack track = new ColonyTrack(trackId);
                track.addTimePoint(colony);
                tracks.put(trackId, track);
            }
        }
        
        // Handle unmatched tracks (disappeared colonies)
        // For now, keep tracks - could implement gap handling here
    }
    
    private List<TrackMatch> findBestMatches(List<String> trackIds, List<Colony> colonies) {
        List<TrackMatch> matches = new ArrayList<>();
        
        for (String trackId : trackIds) {
            ColonyTrack track = tracks.get(trackId);
            if (track.timePoints.isEmpty()) continue;
            
            Colony lastColony = track.timePoints.get(track.timePoints.size() - 1);
            Colony bestMatch = null;
            double bestScore = Double.MAX_VALUE;
            
            for (Colony colony : colonies) {
                double score = calculateMatchScore(lastColony, colony);
                if (score < bestScore && score < options.maxMatchingDistance) {
                    bestScore = score;
                    bestMatch = colony;
                }
            }
            
            if (bestMatch != null) {
                matches.add(new TrackMatch(trackId, bestMatch, bestScore));
            }
        }
        
        // Sort by score and resolve conflicts (one colony per track)
        matches.sort(Comparator.comparingDouble(m -> m.score));
        List<TrackMatch> resolvedMatches = new ArrayList<>();
        Set<Colony> usedColonies = new HashSet<>();
        
        for (TrackMatch match : matches) {
            if (!usedColonies.contains(match.colony)) {
                resolvedMatches.add(match);
                usedColonies.add(match.colony);
            }
        }
        
        return resolvedMatches;
    }
    
    private double calculateMatchScore(Colony previous, Colony current) {
        // Distance score
        double distance = previous.center.distance(current.center);
        if (distance > options.maxMatchingDistance) {
            return Double.MAX_VALUE;
        }
        
        // Size change score
        double sizeRatio = current.diameter / previous.diameter;
        if (sizeRatio > options.sizeChangeThreshold || sizeRatio < (1.0/options.sizeChangeThreshold)) {
            return Double.MAX_VALUE;
        }
        
        // Morphology change score
        double morphologyChange = calculateMorphologyChange(previous.morphology, current.morphology);
        if (morphologyChange > options.morphologyThreshold) {
            return Double.MAX_VALUE;
        }
        
        // Combined score (weighted sum)
        return distance + (Math.abs(sizeRatio - 1.0) * 10) + (morphologyChange * 20);
    }
    
    private double calculateMorphologyChange(ColonyMorphology prev, ColonyMorphology curr) {
        double circularityChange = Math.abs(prev.circularity - curr.circularity);
        double solidityChange = Math.abs(prev.solidity - curr.solidity);
        double aspectChange = Math.abs(prev.aspectRatio - curr.aspectRatio);
        
        return (circularityChange + solidityChange + aspectChange) / 3.0;
    }
    
    private void updateGrowthRates() {
        if (timePoints.size() < 2) return;
        
        for (ColonyTrack track : tracks.values()) {
            if (track.timePoints.size() < 2) continue;
            
            calculateGrowthRatesForTrack(track);
        }
    }
    
    private void calculateGrowthRatesForTrack(ColonyTrack track) {
        List<Colony> colonies = track.timePoints;
        
        for (int i = 1; i < colonies.size(); i++) {
            Colony prev = colonies.get(i-1);
            Colony curr = colonies.get(i);
            
            // Calculate time difference (assuming sequential time points)
            double hoursElapsed = 1.0; // Placeholder - would calculate from timestamps
            
            double areaGrowthRate = (curr.area - prev.area) / hoursElapsed;
            double diameterGrowthRate = (curr.diameter - prev.diameter) / hoursElapsed;
            
            track.growthRates.put("area_growth_rate_mm2_h", areaGrowthRate);
            track.growthRates.put("diameter_growth_rate_mm_h", diameterGrowthRate);
        }
    }
    
    /**
     * Get all colony tracks
     */
    public Map<String, ColonyTrack> getTracks() {
        return new HashMap<>(tracks);
    }
    
    /**
     * Get tracks that show significant growth
     */
    public List<ColonyTrack> getGrowingTracks(double minGrowthRate) {
        List<ColonyTrack> growingTracks = new ArrayList<>();
        
        for (ColonyTrack track : tracks.values()) {
            if (track.timePoints.size() < 2) continue;
            
            Colony first = track.timePoints.get(0);
            Colony last = track.timePoints.get(track.timePoints.size() - 1);
            
            double totalGrowth = last.area - first.area;
            if (totalGrowth > minGrowthRate) {
                growingTracks.add(track);
            }
        }
        
        return growingTracks;
    }
    
    /**
     * Export time-series data as JSON
     */
    public JSONObject exportData() {
        JSONObject export = new JSONObject();
        
        // Time points metadata
        JSONArray timePointsArray = new JSONArray();
        for (TimePoint tp : timePoints) {
            timePointsArray.put(tp.metadata);
        }
        export.put("time_points", timePointsArray);
        
        // Colony tracks
        JSONArray tracksArray = new JSONArray();
        for (ColonyTrack track : tracks.values()) {
            tracksArray.put(track.toJSON());
        }
        export.put("tracks", tracksArray);
        
        // Summary statistics
        JSONObject summary = new JSONObject();
        summary.put("total_tracks", tracks.size());
        summary.put("total_time_points", timePoints.size());
        summary.put("growing_tracks", getGrowingTracks(0.1).size());
        
        if (!tracks.isEmpty()) {
            double avgFinalArea = tracks.values().stream()
                .filter(t -> !t.timePoints.isEmpty())
                .mapToDouble(t -> t.timePoints.get(t.timePoints.size()-1).area)
                .average().orElse(0);
            summary.put("avg_final_area_mm2", avgFinalArea);
        }
        
        export.put("summary", summary);
        
        return export;
    }
    
    // Helper class for tracking matches
    private static class TrackMatch {
        public final String trackId;
        public final Colony colony;
        public final double score;
        
        public TrackMatch(String trackId, Colony colony, double score) {
            this.trackId = trackId;
            this.colony = colony;
            this.score = score;
        }
    }
    
    /**
     * Analyze colonies in a single image to create Colony objects
     */
    public static List<Colony> analyzeColonies(ImagePlus image, boolean measureMorphology, 
                                             boolean measureXgalBlueness) {
        List<Colony> colonies = new ArrayList<>();
        
        // Simplified colony detection - in practice would use more sophisticated methods
        ImageProcessor ip = image.getProcessor();
        
        // Basic blob detection
        for (int y = 20; y < ip.getHeight() - 20; y += 10) {
            for (int x = 20; x < ip.getWidth() - 20; x += 10) {
                if (isColonyCenter(ip, x, y)) {
                    Colony colony = createColonyFromCenter(ip, x, y, measureMorphology, measureXgalBlueness);
                    if (colony != null) {
                        colonies.add(colony);
                    }
                }
            }
        }
        
        return colonies;
    }
    
    private static boolean isColonyCenter(ImageProcessor ip, int x, int y) {
        // Simple colony detection based on local minima
        int radius = 5;
        double centerVal = ip.getPixelValue(x, y);
        double avgSurrounding = 0;
        int count = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx*dx + dy*dy <= radius*radius) {
                    int px = x + dx;
                    int py = y + dy;
                    if (px >= 0 && px < ip.getWidth() && py >= 0 && py < ip.getHeight()) {
                        avgSurrounding += ip.getPixelValue(px, py);
                        count++;
                    }
                }
            }
        }
        
        avgSurrounding /= count;
        return (avgSurrounding - centerVal) > 15; // Colony is darker than surroundings
    }
    
    private static Colony createColonyFromCenter(ImageProcessor ip, int x, int y, 
                                               boolean measureMorphology, boolean measureXgalBlueness) {
        String id = "colony_" + x + "_" + y;
        Point2D center = new Point2D.Double(x, y);
        
        // Measure colony area and diameter
        double area = measureColonyArea(ip, x, y);
        double diameter = 2 * Math.sqrt(area / Math.PI); // Assuming circular
        
        // Measure morphology if requested
        ColonyMorphology morphology = measureMorphology ? 
            measureColonyMorphology(ip, x, y) : 
            new ColonyMorphology(1.0, 1.0, 1.0, 0.0, "unknown");
        
        // Measure X-gal blueness if requested
        double blueness = measureXgalBlueness ? measureColonyBlueness(ip, x, y) : 0.0;
        
        double confidence = 0.8; // Placeholder confidence
        JSONObject rawData = new JSONObject()
            .put("detection_method", "simple_blob")
            .put("pixel_coordinates", new JSONArray().put(x).put(y));
        
        return new Colony(id, center, area, diameter, morphology, blueness, confidence, rawData);
    }
    
    private static double measureColonyArea(ImageProcessor ip, int centerX, int centerY) {
        // Simple area measurement using connected components
        // In practice would use more sophisticated segmentation
        int radius = 10;
        int pixelCount = 0;
        double threshold = ip.getPixelValue(centerX, centerY) + 20;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                    if (ip.getPixelValue(x, y) < threshold) {
                        pixelCount++;
                    }
                }
            }
        }
        
        // Convert pixels to mm² (assuming calibration)
        double pixelAreaMm2 = 0.01; // Placeholder calibration
        return pixelCount * pixelAreaMm2;
    }
    
    private static ColonyMorphology measureColonyMorphology(ImageProcessor ip, int centerX, int centerY) {
        // Simplified morphology analysis
        double circularity = 0.85;  // Placeholder
        double solidity = 0.90;     // Placeholder
        double aspectRatio = 1.1;   // Placeholder
        double textureVariance = calculateTextureVariance(ip, centerX, centerY);
        String edgeType = textureVariance > 20 ? "rough" : "smooth";
        
        return new ColonyMorphology(circularity, solidity, aspectRatio, textureVariance, edgeType);
    }
    
    private static double calculateTextureVariance(ImageProcessor ip, int centerX, int centerY) {
        int radius = 5;
        double sum = 0;
        double sumSquares = 0;
        int count = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx*dx + dy*dy <= radius*radius) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                        double val = ip.getPixelValue(x, y);
                        sum += val;
                        sumSquares += val * val;
                        count++;
                    }
                }
            }
        }
        
        if (count <= 1) return 0;
        double mean = sum / count;
        return (sumSquares / count) - (mean * mean);
    }
    
    private static double measureColonyBlueness(ImageProcessor ip, int centerX, int centerY) {
        // Simplified X-gal blueness measurement
        // In practice would use proper color analysis
        int radius = 5;
        double avgBlueness = 0;
        int count = 0;
        
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx*dx + dy*dy <= radius*radius) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                        // Simple blueness based on pixel intensity (would use RGB analysis)
                        double intensity = ip.getPixelValue(x, y);
                        double blueness = Math.max(0, (255 - intensity) / 255.0);
                        avgBlueness += blueness;
                        count++;
                    }
                }
            }
        }
        
        return count > 0 ? avgBlueness / count : 0;
    }
}
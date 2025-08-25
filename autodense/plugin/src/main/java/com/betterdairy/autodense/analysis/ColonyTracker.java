package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.session.SessionStore;
import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Stable colony ID tracking across time points using Kalman filtering
 * Maintains consistent colony identities for growth analysis
 */
public class ColonyTracker {
    
    /**
     * Colony track with Kalman filtering for position prediction
     */
    public static class ColonyTrack {
        public final int trackId;
        public final List<TrackPoint> timePoints;
        public final KalmanFilter positionFilter;
        public double confidence;
        public boolean isActive;
        
        public ColonyTrack(int trackId, Colony initialColony, int timePoint) {
            this.trackId = trackId;
            this.timePoints = new ArrayList<>();
            this.positionFilter = new KalmanFilter(initialColony.x(), initialColony.y());
            this.confidence = 1.0;
            this.isActive = true;
            
            // Add initial observation
            addObservation(initialColony, timePoint);
        }
        
        public void addObservation(Colony colony, int timePoint) {
            // Update Kalman filter with new position
            positionFilter.update(colony.x(), colony.y());
            
            // Add to track history
            TrackPoint point = new TrackPoint(colony, timePoint, 
                                            positionFilter.getStateX(), positionFilter.getStateY(),
                                            positionFilter.getUncertainty());
            timePoints.add(point);
            
            // Update confidence based on prediction accuracy
            double predictionError = Math.sqrt(
                Math.pow(colony.x() - positionFilter.getPredictionX(), 2) + 
                Math.pow(colony.y() - positionFilter.getPredictionY(), 2)
            );
            
            // Confidence decreases with prediction error
            double errorFactor = Math.exp(-predictionError / 20.0); // 20 pixel tolerance
            confidence = 0.9 * confidence + 0.1 * errorFactor;
        }
        
        public Point2D getPredictedPosition() {
            return new Point2D.Double(positionFilter.getPredictionX(), positionFilter.getPredictionY());
        }
        
        public Colony getLatestColony() {
            if (timePoints.isEmpty()) return null;
            return timePoints.get(timePoints.size() - 1).colony;
        }
        
        public int getLatestTimePoint() {
            if (timePoints.isEmpty()) return -1;
            return timePoints.get(timePoints.size() - 1).timePoint;
        }
    }
    
    /**
     * Point in a colony track with smoothed position
     */
    public static class TrackPoint {
        public final Colony colony;
        public final int timePoint;
        public final double smoothedX;
        public final double smoothedY;
        public final double uncertainty;
        
        public TrackPoint(Colony colony, int timePoint, double smoothedX, double smoothedY, double uncertainty) {
            this.colony = colony;
            this.timePoint = timePoint;
            this.smoothedX = smoothedX;
            this.smoothedY = smoothedY;
            this.uncertainty = uncertainty;
        }
    }
    
    /**
     * Simple Kalman filter for 2D position tracking
     */
    private static class KalmanFilter {
        // State: [x, y, vx, vy] - position and velocity
        private double[] state = new double[4];
        private double[][] P = new double[4][4]; // Covariance matrix
        private final double[][] F = { // State transition matrix
            {1, 0, 1, 0},
            {0, 1, 0, 1},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
        private final double[][] H = { // Observation matrix
            {1, 0, 0, 0},
            {0, 1, 0, 0}
        };
        private final double processNoise = 1.0;
        private final double measurementNoise = 5.0;
        
        public KalmanFilter(double initialX, double initialY) {
            state[0] = initialX;
            state[1] = initialY;
            state[2] = 0; // Initial velocity
            state[3] = 0;
            
            // Initialize covariance matrix
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    P[i][j] = (i == j) ? 10.0 : 0.0;
                }
            }
        }
        
        public void update(double observedX, double observedY) {
            // Predict step
            predict();
            
            // Update step
            double[] observation = {observedX, observedY};
            double[] innovation = new double[2];
            
            // Innovation = observation - H * predicted_state
            innovation[0] = observation[0] - state[0];
            innovation[1] = observation[1] - state[1];
            
            // Kalman gain calculation (simplified)
            double gain = P[0][0] / (P[0][0] + measurementNoise);
            
            // Update state
            state[0] += gain * innovation[0];
            state[1] += gain * innovation[1];
            
            // Update covariance (simplified)
            P[0][0] = (1 - gain) * P[0][0];
            P[1][1] = (1 - gain) * P[1][1];
        }
        
        private void predict() {
            // Predict next state
            double newX = state[0] + state[2]; // x + vx
            double newY = state[1] + state[3]; // y + vy
            
            state[0] = newX;
            state[1] = newY;
            
            // Add process noise to covariance
            P[0][0] += processNoise;
            P[1][1] += processNoise;
        }
        
        public double getStateX() { return state[0]; }
        public double getStateY() { return state[1]; }
        public double getPredictionX() { return state[0] + state[2]; }
        public double getPredictionY() { return state[1] + state[3]; }
        public double getUncertainty() { return Math.sqrt(P[0][0] + P[1][1]); }
    }
    
    /**
     * Colony tracking session maintaining all tracks
     */
    public static class TrackingSession {
        private final List<ColonyTrack> tracks;
        private int nextTrackId;
        private int currentTimePoint;
        private final double maxMatchingDistance;
        
        public TrackingSession(double maxMatchingDistance) {
            this.tracks = new ArrayList<>();
            this.nextTrackId = 1;
            this.currentTimePoint = 0;
            this.maxMatchingDistance = maxMatchingDistance;
        }
        
        /**
         * Add colonies from a new time point and maintain tracking
         */
        public void addTimePoint(List<Colony> colonies) {
            currentTimePoint++;
            
            // Match colonies to existing tracks
            List<Colony> unmatchedColonies = new ArrayList<>(colonies);
            List<ColonyTrack> unmatchedTracks = new ArrayList<>();
            
            for (ColonyTrack track : tracks) {
                if (!track.isActive) continue;
                
                Colony bestMatch = findBestMatch(track, unmatchedColonies);
                if (bestMatch != null) {
                    track.addObservation(bestMatch, currentTimePoint);
                    unmatchedColonies.remove(bestMatch);
                    
                    // Deactivate track if confidence is too low
                    if (track.confidence < 0.1) {
                        track.isActive = false;
                    }
                } else {
                    // No match found - track may have ended
                    track.confidence *= 0.7; // Decay confidence
                    if (track.confidence < 0.1) {
                        track.isActive = false;
                    } else {
                        unmatchedTracks.add(track);
                    }
                }
            }
            
            // Create new tracks for unmatched colonies
            for (Colony colony : unmatchedColonies) {
                ColonyTrack newTrack = new ColonyTrack(nextTrackId++, colony, currentTimePoint);
                tracks.add(newTrack);
            }
        }
        
        /**
         * Find best matching colony for a track using predicted position
         */
        private Colony findBestMatch(ColonyTrack track, List<Colony> candidates) {
            if (candidates.isEmpty()) return null;
            
            Point2D predicted = track.getPredictedPosition();
            Colony bestMatch = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (Colony colony : candidates) {
                double distance = predicted.distance(colony.x(), colony.y());
                if (distance < bestDistance && distance < maxMatchingDistance) {
                    bestDistance = distance;
                    bestMatch = colony;
                }
            }
            
            return bestMatch;
        }
        
        public List<ColonyTrack> getActiveTracks() {
            List<ColonyTrack> activeTracks = new ArrayList<>();
            for (ColonyTrack track : tracks) {
                if (track.isActive && track.confidence > 0.3) {
                    activeTracks.add(track);
                }
            }
            return activeTracks;
        }
        
        public List<ColonyTrack> getAllTracks() {
            return new ArrayList<>(tracks);
        }
        
        public int getCurrentTimePoint() {
            return currentTimePoint;
        }
        
        /**
         * Convert tracking session to JSON for storage
         */
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("next_track_id", nextTrackId);
            json.put("current_time_point", currentTimePoint);
            json.put("max_matching_distance", maxMatchingDistance);
            
            JSONArray tracksArray = new JSONArray();
            for (ColonyTrack track : tracks) {
                JSONObject trackJson = new JSONObject();
                trackJson.put("track_id", track.trackId);
                trackJson.put("confidence", track.confidence);
                trackJson.put("is_active", track.isActive);
                
                JSONArray timePointsArray = new JSONArray();
                for (TrackPoint point : track.timePoints) {
                    JSONObject pointJson = new JSONObject();
                    pointJson.put("time_point", point.timePoint);
                    pointJson.put("x", point.colony.x());
                    pointJson.put("y", point.colony.y());
                    pointJson.put("diameter", point.colony.diameter());
                    pointJson.put("smoothed_x", point.smoothedX);
                    pointJson.put("smoothed_y", point.smoothedY);
                    pointJson.put("uncertainty", point.uncertainty);
                    timePointsArray.put(pointJson);
                }
                trackJson.put("time_points", timePointsArray);
                tracksArray.put(trackJson);
            }
            json.put("tracks", tracksArray);
            
            return json;
        }
    }
    
    /**
     * Store tracking session in SessionStore
     */
    public static void storeTrackingSession(SessionStore sessionStore, String sessionId, TrackingSession session) {
        if (sessionStore != null && sessionId != null) {
            sessionStore.putAnalysis("colony_tracking", session.toJSON(), sessionId);
        }
    }
    
    /**
     * Retrieve tracking session from SessionStore
     */
    public static TrackingSession getTrackingSession(SessionStore sessionStore, String sessionId) {
        if (sessionStore == null || sessionId == null) {
            return null;
        }
        
        for (String analysisHandle : sessionStore.getAnalysesForImage(sessionId)) {
            SessionStore.AnalysisRecord analysis = sessionStore.getAnalysis(analysisHandle);
            if ("colony_tracking".equals(analysis.type)) {
                return loadTrackingSessionFromJSON((JSONObject) analysis.data);
            }
        }
        return null;
    }
    
    /**
     * Load tracking session from JSON
     */
    private static TrackingSession loadTrackingSessionFromJSON(JSONObject json) {
        double maxMatchingDistance = json.getDouble("max_matching_distance");
        TrackingSession session = new TrackingSession(maxMatchingDistance);
        session.nextTrackId = json.getInt("next_track_id");
        session.currentTimePoint = json.getInt("current_time_point");
        
        // Note: Full reconstruction would require restoring Colony objects and Kalman filters
        // This is a simplified version - in production would need complete serialization
        
        return session;
    }
    
    /**
     * Create stable colony IDs for a list of colonies using tracking information
     */
    public static List<Colony> assignStableIds(List<Colony> colonies, TrackingSession trackingSession) {
        List<Colony> stableColonies = new ArrayList<>();
        
        // Map colonies to their stable track IDs
        Map<Colony, Integer> colonyToTrackId = new HashMap<>();
        
        for (ColonyTrack track : trackingSession.getActiveTracks()) {
            Colony latestColony = track.getLatestColony();
            if (latestColony != null) {
                // Find matching colony in current list
                for (Colony colony : colonies) {
                    double distance = Math.sqrt(
                        Math.pow(colony.x() - latestColony.x(), 2) + 
                        Math.pow(colony.y() - latestColony.y(), 2)
                    );
                    if (distance < 10.0) { // Close match
                        colonyToTrackId.put(colony, track.trackId);
                        break;
                    }
                }
            }
        }
        
        // Create new colonies with stable IDs
        for (Colony colony : colonies) {
            Integer trackId = colonyToTrackId.get(colony);
            if (trackId != null) {
                // Use track ID as stable colony index
                Colony stableColony = new Colony(
                    trackId, // Use track ID as stable index
                    colony.x(), colony.y(), colony.area(), colony.diameter(), colony.diameterMm(),
                    colony.circularity(), colony.solidity(), colony.meanIntensity(),
                    colony.colorClass(), colony.colorConfidence(), colony.sizeClass(), colony.binCategory()
                );
                stableColonies.add(stableColony);
            } else {
                // Keep original colony if no tracking match
                stableColonies.add(colony);
            }
        }
        
        return stableColonies;
    }
}
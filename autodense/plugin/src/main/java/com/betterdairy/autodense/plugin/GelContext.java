package com.betterdairy.autodense.plugin;

import org.json.JSONObject;

import java.util.List;

/**
 * @deprecated This context is unused. Use SessionStore via GelAnalysisTools instead.
 * All active functionality is in GeminiOrchestrator → GelAnalysisTools → SessionStore.
 * This class exists only for backward compatibility and will be removed.
 */
@Deprecated
public final class GelContext {
    public void setLadder(int lane, String ladderId) {
        // TODO
    }
    public void detectBands(Object lanes, Object sensitivity, int minPeakDistancePx) {
        // TODO
    }
    public void calibrate(String model, String dyeFrontMode) {
        // TODO
    }
    public void quantify(String baseline, String integration) {
        // TODO
    }
    public void normalize(String mode, JSONObject reference) {
        // TODO
    }
    public void computeDeltas(int referenceLane, String stat) {
        // TODO
    }
    public void export(List<String> formats) {
        // TODO
    }
}

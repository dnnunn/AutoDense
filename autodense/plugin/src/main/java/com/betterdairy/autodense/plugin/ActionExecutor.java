package com.betterdairy.autodense.plugin;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ActionExecutor {
    private ActionExecutor() {}

    public static void execute(JSONObject plan, GelContext ctx) {
        String intent = plan.optString("intent", "");
        if (!"multi_action".equals(intent)) {
            throw new IllegalArgumentException("Unsupported intent: " + intent);
        }
        JSONArray actions = plan.optJSONArray("actions");
        if (actions == null) return;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject a = actions.getJSONObject(i);
            String action = a.optString("action", "");
            switch (action) {
                case "set_ladder" -> ctx.setLadder(a.optInt("lane", 1), a.optString("ladder_id", ""));
                case "detect_bands" -> ctx.detectBands(a.opt("lanes"), a.opt("sensitivity"), a.optInt("min_peak_distance_px", 8));
                case "calibrate_mw" -> ctx.calibrate(a.optString("model", "auto"), a.optString("dye_front_mode", "auto"));
                case "quantify_bands" -> ctx.quantify(a.optString("baseline", "local"), a.optString("integration", "trapezoid"));
                case "normalize" -> ctx.normalize(a.optString("mode", "lane_total"), a.optJSONObject("reference"));
                case "lane_deltas" -> ctx.computeDeltas(a.optInt("reference_lane", 1), a.optString("stat", "delta"));
                case "export" -> ctx.export(null);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }
        }
    }
}

package com.betterdairy.autodense.plugin;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolSchemaValidator {
  private ToolSchemaValidator(){}

  public static void require(JSONObject o, String key) {
    if (!o.has(key) || o.isNull(key)) throw new IllegalArgumentException("Missing "+key);
  }

  public static void requireImageHandle(JSONObject args) {
    if (!args.has("image_handle") || args.isNull("image_handle"))
      throw new IllegalArgumentException("Missing required field: image_handle");
  }

  public static void requireArray(JSONObject args, String key) {
    if (!args.has(key) || !(args.get(key) instanceof JSONArray))
      throw new IllegalArgumentException("Field '"+key+"' must be an array");
  }

  public static void clamp(JSONObject obj, String key, double min, double max) {
    if (!obj.has(key)) return;
    double v = obj.getNumber(key).doubleValue();
    double c = Math.max(min, Math.min(max, v));
    if (c != v) obj.put(key, c);
  }
}

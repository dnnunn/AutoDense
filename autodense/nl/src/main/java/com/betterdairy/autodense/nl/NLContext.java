package com.betterdairy.autodense.nl;

import org.json.JSONObject;

/** Placeholder context passed to NL prompting. Extend as needed. */
public record NLContext(JSONObject metadata) {
    public static NLContext empty() { return new NLContext(new JSONObject()); }
}

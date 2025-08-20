package com.betterdairy.autodense.nl;

public final class Prompts {
    private Prompts() {}

    public static String defaultPrompt(String userText) {
        return "You are AutoDense. Understand the user's gel analysis intent and output a JSON plan conforming to intent.schema.json. User: " + userText;
    }
}

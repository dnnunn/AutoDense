package com.betterdairy.autodense.nl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class JsonSchemas {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile JsonSchema INTENT_SCHEMA;

    private JsonSchemas() {}

    public static synchronized JsonSchema intentSchema() {
        if (INTENT_SCHEMA == null) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            try (InputStream in = JsonSchemas.class.getResourceAsStream("/intent.schema.json")) {
                if (in == null) throw new IllegalStateException("intent.schema.json not found on classpath");
                JsonNode node = MAPPER.readTree(in);
                INTENT_SCHEMA = factory.getSchema(node);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load intent.schema.json", e);
            }
        }
        return INTENT_SCHEMA;
    }

    public static void validate(JSONObject obj) {
        try {
            JsonNode node = MAPPER.readTree(obj.toString().getBytes(StandardCharsets.UTF_8));
            Set<ValidationMessage> msgs = intentSchema().validate(node);
            if (!msgs.isEmpty()) {
                throw new IllegalArgumentException("Plan JSON failed schema: " + msgs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Validation error", e);
        }
    }
}

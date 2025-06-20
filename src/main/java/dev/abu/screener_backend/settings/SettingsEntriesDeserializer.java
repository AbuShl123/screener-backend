package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsEntriesDeserializer extends JsonDeserializer<LinkedHashMap<Double, Integer>> {

    @Override
    public LinkedHashMap<Double, Integer> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);

        if (!node.isObject()) {
            throw new JsonMappingException(p, "Expected JSON object for settings entries");
        }

        LinkedHashMap<Double, Integer> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            try {
                Double key = Double.valueOf(field.getKey());
                Integer value = field.getValue().asInt();
                result.put(key, value);
            } catch (NumberFormatException e) {
                throw new JsonMappingException(p, "Invalid key format: " + field.getKey());
            }
        }

        return result;
    }
}

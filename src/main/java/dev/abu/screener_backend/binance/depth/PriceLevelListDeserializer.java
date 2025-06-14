package dev.abu.screener_backend.binance.depth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PriceLevelListDeserializer extends JsonDeserializer<List<PriceLevel>> {

    @Override
    public List<PriceLevel> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<PriceLevel> result = new ArrayList<>();
        JsonNode node = p.getCodec().readTree(p);

        for (JsonNode level : node) {
            String price = level.get(0).asText();
            String quantity = level.get(1).asText();
            result.add(new PriceLevel(price, quantity));
        }

        return result;
    }
}


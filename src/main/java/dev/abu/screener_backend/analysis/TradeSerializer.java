package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class TradeSerializer extends JsonSerializer<Trade> {

    @Override
    public void serialize(Trade trade, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        gen.writeNumber(trade.getPrice());
        gen.writeNumber(trade.getQuantity());
        gen.writeNumber(trade.getDistance());
        gen.writeNumber(trade.getLevel());
        gen.writeNumber(trade.getLife());
        gen.writeEndArray();
    }
}

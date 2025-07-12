package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

public class TradeSerializer extends JsonSerializer<Trade> {

    @Override
    public void serialize(Trade trade, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        gen.writeString(BigDecimal.valueOf(trade.getPrice()).toPlainString());
        gen.writeString(BigDecimal.valueOf(trade.getQuantity()).toPlainString());
        gen.writeString(BigDecimal.valueOf(trade.getDistance()).toPlainString());
        gen.writeString(BigDecimal.valueOf(trade.getLevel()).toPlainString());
        gen.writeString(BigDecimal.valueOf(trade.getLife()).toPlainString());
        gen.writeEndArray();
    }
}

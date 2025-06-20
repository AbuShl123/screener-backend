package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.LinkedHashMap;

@Converter
public class MapToJsonConverter implements AttributeConverter<LinkedHashMap<Double, Integer>, String> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(LinkedHashMap<Double, Integer> attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize map", e);
        }
    }

    @Override
    public LinkedHashMap<Double, Integer> convertToEntityAttribute(String dbData) {
        try {
            return mapper.readValue(dbData, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize map", e);
        }
    }
}


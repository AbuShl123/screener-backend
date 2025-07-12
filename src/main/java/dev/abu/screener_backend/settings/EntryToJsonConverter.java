package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
public class EntryToJsonConverter implements AttributeConverter<List<SettingsEntry>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SettingsEntry> attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting list to JSON", e);
        }
    }

    @Override
    public List<SettingsEntry> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) {
                return new ArrayList<>();
            }
            JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, SettingsEntry.class);
            return mapper.readValue(dbData, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading JSON from database", e);
        }
    }
}

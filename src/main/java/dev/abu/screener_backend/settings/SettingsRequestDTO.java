package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class SettingsRequestDTO {
    @JsonProperty("mSymbol")
    private String mSymbol;

    @JsonProperty("type")
    private SettingsType type;

    @JsonDeserialize(using = SettingsEntriesDeserializer.class)
    private LinkedHashMap<Double, Integer> entries;
}

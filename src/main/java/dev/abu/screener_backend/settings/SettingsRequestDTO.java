package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Data
public class SettingsRequestDTO {
    @JsonProperty("mSymbol")
    private String mSymbol;

    @JsonProperty("audio")
    private boolean audio;

    @JsonProperty("type")
    private SettingsType type;

    private List<SettingsEntry> entries;
}

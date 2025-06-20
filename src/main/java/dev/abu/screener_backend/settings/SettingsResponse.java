package dev.abu.screener_backend.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;

@Getter
@Setter
public class SettingsResponse {

    private final long id;
    private final String mSymbol;
    private final SettingsType type;
    private final LinkedHashMap<Double, Integer> entries;

    public SettingsResponse(Settings settings) {
        this.id = settings.getId();
        this.mSymbol = settings.getMSymbol();
        this.type = settings.getSettingsType();
        this.entries = settings.getEntries();
    }
}

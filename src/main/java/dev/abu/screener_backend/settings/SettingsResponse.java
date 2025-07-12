package dev.abu.screener_backend.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;

@Getter
@Setter
public class SettingsResponse {

    private final long id;
    private final String mSymbol;
    private final boolean audio;
    private final SettingsType type;
    private final List<SettingsEntry> entries;

    public SettingsResponse(Settings settings) {
        this.id = settings.getId();
        this.mSymbol = settings.getMSymbol();
        this.audio = settings.isAudio();
        this.type = settings.getSettingsType();
        this.entries = settings.getEntries();
    }
}

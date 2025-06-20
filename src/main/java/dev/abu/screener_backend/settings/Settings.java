package dev.abu.screener_backend.settings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Settings {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String mSymbol;

    @Enumerated(EnumType.STRING)
    private SettingsType settingsType;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = MapToJsonConverter.class)
    private LinkedHashMap<Double, Integer> entries;

    @Column(unique = true)
    private String settingsHash;

    public Settings(String mSymbol, SettingsType settingsType, LinkedHashMap<Double, Integer> entries, String settingsHash) {
        this.mSymbol = mSymbol;
        this.settingsType = settingsType;
        this.entries = entries;
        this.settingsHash = settingsHash;
    }

    public void setNewValues(String mSymbol, SettingsType settingsType, LinkedHashMap<Double, Integer> entries, String settingsHash) {
        this.mSymbol = mSymbol;
        this.settingsType = settingsType;
        this.entries = entries;
        this.settingsHash = settingsHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Settings other = (Settings) obj;
        return other.id.longValue() == this.id.longValue();
    }

    @Override
    public int hashCode() {
        return settingsHash.hashCode();
    }
}

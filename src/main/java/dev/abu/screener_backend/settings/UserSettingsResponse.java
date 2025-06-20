package dev.abu.screener_backend.settings;

import java.util.List;

public record UserSettingsResponse(String user, List<SettingsResponse> settings) {}


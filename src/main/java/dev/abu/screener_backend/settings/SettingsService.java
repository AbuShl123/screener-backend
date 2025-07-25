package dev.abu.screener_backend.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.appuser.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final ObjectMapper mapper;
    private final UserSettingsRepository userSettingsRepository;
    private final SettingsRepository settingsRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanUpUnusedSettings() {
        settingsRepository.deleteOrphanedSettings();
        log.info("Deleted unused settings");
    }

    @Transactional
    public void saveSettings(AppUser appUser, SettingsRequestDTO settingsRequest) {
        checkInvariants(settingsRequest);

        List<SettingsEntry> settingsMap = settingsRequest.getEntries();
        String mSymbol = settingsRequest.getMSymbol();
        SettingsType type = settingsRequest.getType();
        boolean audio = settingsRequest.isAudio();
        String settingHash = computeSettingHash(mSymbol, audio, type, settingsMap);

        // scenario 1: user already has a record for a given mSymbol in the UserSettings table
        // scenario 1.1: the requested settings are exactly equal to existing settings
        // scenario 1.2: existing settings are used only by this user
        // scenario 1.3: existing settings are used by many users

        // scenario 2: user has no record for a given mSymbol in the UserSettings table
        // scenario 2.1: no records found for requested settings in the Settings table
        // scenario 2.2: the requested settings already exist in the Settings table but are used by another user(s)

        Optional<UserSettings> currentSettingsOpt = userSettingsRepository.findByAppUserAndSettings_mSymbol(appUser, mSymbol);
        if (currentSettingsOpt.isPresent()) {
            handleExistingRecord(currentSettingsOpt.get(), settingHash, settingsRequest);
        } else {
            handleNewRecord(appUser, settingHash, settingsRequest);
        }
    }

    @Transactional
    public boolean deleteSettings(AppUser appUser, String mSymbol) {
        if (mSymbol == null || mSymbol.isEmpty()) {
            throw new IllegalStateException("Symbol is null or empty.");
        }
        Optional<UserSettings> userSetOpt = userSettingsRepository.findByAppUserAndSettings_mSymbol(appUser, mSymbol);
        if (userSetOpt.isPresent()) {
            UserSettings userSettings = userSetOpt.get();
            deleteUserSettings(userSettings);
            return true;
        }
        return false;
    }

    @Transactional
    public void resetSettings(AppUser appUser) {
        List<UserSettings> allUserSettings = userSettingsRepository.findAllByAppUser(appUser);
        for (UserSettings userSettings : allUserSettings) {
            deleteUserSettings(userSettings);
        }

        Collection<Settings> settings = userSettingsRepository.findAllSettingsByAppUser(appUser);
        List<Settings> allDefaultSettings = settingsRepository.findAllDefaultSettings();
        for (Settings defaultSettings : allDefaultSettings) {
            if (defaultSettings.getSettingsHash().contains("all") && settings.contains(defaultSettings)) continue;
            UserSettings userSettings = new UserSettings(appUser, defaultSettings);
            userSettingsRepository.save(userSettings);
        }
    }

    @Transactional
    public void resetSettings(AppUser appUser, String mSymbol) {
        if (mSymbol == null || mSymbol.isEmpty() || mSymbol.equalsIgnoreCase("all")) return;
        mSymbol = mSymbol.toLowerCase();

        userSettingsRepository.findByAppUserAndSettings_mSymbol(appUser, mSymbol)
                .ifPresent(this::deleteUserSettings);

        settingsRepository.findDefaultSettings(mSymbol).ifPresent(defSettings -> {
            UserSettings userSettings = new UserSettings(appUser, defSettings);
            userSettingsRepository.save(userSettings);
        });
    }

    public UserSettingsResponse getAllSettings(AppUser appUser) {
        List<UserSettings> userSettingsList = userSettingsRepository.findAllByAppUser(appUser);
        List<SettingsResponse> settings = new ArrayList<>();
        userSettingsList.forEach(e -> settings.add(new SettingsResponse(e.getSettings())));
        return new UserSettingsResponse(appUser.getEmail(), settings);
    }

    public Optional<UserSettingsResponse> getSettings(AppUser appUser, String mSymbol) {
        if (mSymbol == null || mSymbol.isEmpty()) {
            throw new IllegalStateException("Symbol is null or empty.");
        }
        UserSettings userSettings = userSettingsRepository.findByAppUserAndSettings_mSymbol(appUser, mSymbol).orElse(null);
        if (userSettings == null) return Optional.empty();
        var settings = new SettingsResponse(userSettings.getSettings());
        return Optional.of(new UserSettingsResponse(appUser.getEmail(), List.of(settings)));
    }

    // scenario 1: user already has a record for a given mSymbol in the UserSettings table
    private void handleExistingRecord(UserSettings userSettings, String requestedSettingsHash, SettingsRequestDTO settingsRequest) {
        Settings existingSettings = userSettings.getSettings();

        // scenario 1.1: the requested settings are exactly equal to existing settings
        if (existingSettings.getSettingsHash().equals(requestedSettingsHash)) {
            return;
        }

        long settingsCount = userSettingsRepository.countBySettings(existingSettings);
        var mSymbol = settingsRequest.getMSymbol();
        var type = settingsRequest.getType();
        var entries = settingsRequest.getEntries();
        var audio = settingsRequest.isAudio();

        // scenario 1.2: existing settings are used only by this user
        if (settingsCount == 1 && !existingSettings.getSettingsHash().contains("default")) {
            existingSettings.setNewValues(mSymbol, audio, type, entries, requestedSettingsHash);
            settingsRepository.save(existingSettings);
            return;
        }

        // scenario 1.3: existing settings are used by many users
        Settings newSettings = constructSettings(mSymbol, audio, type, entries, requestedSettingsHash);
        settingsRepository.save(newSettings);
        userSettings.setSettings(newSettings);
        userSettingsRepository.save(userSettings);
    }

    // scenario 2: user has no record for a given mSymbol in the UserSettings table
    private void handleNewRecord(AppUser appUser, String requestedSettingsHash, SettingsRequestDTO settingsRequest) {
        Settings settings = settingsRepository.findBySettingsHash(requestedSettingsHash)
                .orElseGet(() -> {
                    Settings newSettings = constructSettings(
                            settingsRequest.getMSymbol(),
                            settingsRequest.isAudio(),
                            settingsRequest.getType(),
                            settingsRequest.getEntries(),
                            requestedSettingsHash
                    );
                    return settingsRepository.save(newSettings);
                });
        UserSettings userSettings = new UserSettings(appUser, settings);
        userSettingsRepository.save(userSettings);
    }

    private void deleteUserSettings(UserSettings userSettings) {
        Settings settings = userSettings.getSettings();
        if (settings.getSettingsHash().contains("all")) return;

        userSettingsRepository.delete(userSettings);
        if (settings.getSettingsHash().contains("default")) return;

        long count = userSettingsRepository.countBySettings(settings);
        if (count == 0) {
            settingsRepository.delete(settings);
        }
    }

    private String computeSettingHash(String mSymbol, boolean audio, SettingsType type, List<SettingsEntry> settingsMap) {
        try {
            Map<String, Object> hashSource = new LinkedHashMap<>();
            hashSource.put("mSymbol", mSymbol);
            hashSource.put("audio", audio);
            hashSource.put("entries", settingsMap);
            hashSource.put("type", type);
            String json = mapper.writeValueAsString(hashSource);
            return DigestUtils.sha256Hex(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to compute settings hash", e);
        }
    }

    private Settings constructSettings(String mSymbol, boolean audio, SettingsType type, List<SettingsEntry> settingsMap, String settingsHash) {
        return new Settings(
                mSymbol,
                audio,
                type,
                settingsMap,
                settingsHash
        );
    }

    private void checkInvariants(SettingsRequestDTO settingsRequest) {
        // entries missing
        if (settingsRequest == null || settingsRequest.getEntries() == null || settingsRequest.getEntries().isEmpty()) {
            throw new IllegalStateException("Settings entries are null or empty.");
        }

        // invalid number of entries
        if (settingsRequest.getEntries().size() != 4) {
            throw new IllegalStateException("Settings entries must contain exactly 4 entries.");
        }

        // mSymbol missing
        if (settingsRequest.getMSymbol() == null || settingsRequest.getMSymbol().isEmpty()) {
            throw new IllegalStateException("The mSymbol is missing.");
        }

        // type missing
        if (settingsRequest.getType() == null) {
            throw new IllegalStateException("The settings type is missing.");
        }
    }
}

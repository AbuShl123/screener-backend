package dev.abu.screener_backend.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.appuser.AppUserRepository;
import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.settings.Settings;
import dev.abu.screener_backend.settings.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class SessionManager {

    private final ObjectMapper mapper;
    private final AppUserRepository appUserRepository;
    private final EventDistributor eventDistributor;
    private final UserSettingsRepository userSettingsRepository;
    private final OBService obService;

    private final Map<WebSocketSession, AppUser> sessions = new ConcurrentHashMap<>();
    private final Map<AppUser, UserContainer> containers = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        AppUser user = extractUser(session);
        if (user == null) return;
        sessions.put(session, user);
        setupUser(user, session);
    }

    public void removeSession(WebSocketSession session) {
        AppUser user = sessions.remove(session);
        if (user == null) return;
        UserContainer container = containers.remove(user);
        if (container == null) return;
        eventDistributor.unregisterUser(userSettingsRepository.findAllSettingsByAppUser(user), container);
        deleteUserTL(user);
    }

    public void setupUser(AppUser user, WebSocketSession session) {
        setupUserContainer(user, session);
        setupUserTL(user);
    }

    private void setupUserTL(AppUser user) {
        Collection<Settings> userSettings = userSettingsRepository.findAllSettingsByAppUser(user);
        obService.addUserTL(userSettings);
    }

    private void deleteUserTL(AppUser user) {
        Collection<Settings> userSettings = userSettingsRepository.findAllSettingsByAppUser(user);
        obService.deleteUserTL(userSettings);
    }

    private void setupUserContainer(AppUser user, WebSocketSession session) {
        Collection<Settings> userSettings = userSettingsRepository.findAllSettingsByAppUser(user);

        if (containers.containsKey(user)) {
            eventDistributor.unregisterUser(userSettings, containers.get(user));
        }

        UserContainer userContainer = new UserContainer(user, session, mapper);
        containers.put(user, userContainer);
        eventDistributor.registerUser(userSettings, userContainer);
    }

    private AppUser extractUser(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isEmpty()) {
            closeSession(session);
            return null;
        }
        Optional<AppUser> userOpt = appUserRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            closeSession(session);
            return null;
        }
        return userOpt.get();
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {}
    }

    private Set<String> getSettingHashes(AppUser user) {
        return userSettingsRepository.findAllSettingHashesByAppUser(user);
    }
}
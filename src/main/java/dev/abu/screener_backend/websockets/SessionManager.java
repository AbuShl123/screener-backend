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
import java.util.*;
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

    private final Map<WebSocketSession, AppUser> sessionToUser = new ConcurrentHashMap<>();
    private final Map<AppUser, Set<WebSocketSession>> userToSessions = new ConcurrentHashMap<>();
    private final Map<AppUser, UserContainer> containers = new ConcurrentHashMap<>();
    private final Map<AppUser, Set<Settings>> userToSettings = new ConcurrentHashMap<>();

    public Set<WebSocketSession> getAllSessions() {
        return sessionToUser.keySet();
    }

    public void addSession(WebSocketSession session) {
        AppUser user = extractUser(session);
        if (user == null) return;
        sessionToUser.put(session, user);
        userToSessions.computeIfAbsent(user, u -> ConcurrentHashMap.newKeySet()).add(session);
        if (userToSessions.get(user).size() > 1) {
            containers.get(user).addSession(session);
            return;
        }
        setupUser(user, session);
    }

    public void removeSession(WebSocketSession session) {
        AppUser user = sessionToUser.remove(session);

        if (user == null) {
            log.warn("Trying to remove unknown WebSocketSession: {}", session.getId());
            return;
        }

        Set<WebSocketSession> sessions = userToSessions.get(user);
        if (sessions == null) return;

        sessions.remove(session);
        if (!sessions.isEmpty()) return;

        userToSessions.remove(user);

        UserContainer container = containers.remove(user);
        if (container == null) return;

        Collection<Settings> userSettings = userToSettings.get(user);
        eventDistributor.unregisterUser(userSettings, container);
        obService.deleteUserTL(userSettings);
        userToSettings.remove(user);
    }

    public void setupUser(AppUser user, WebSocketSession session) {
        Collection<Settings> userSettings = userSettingsRepository.findAllSettingsByAppUser(user);
        userToSettings.computeIfAbsent(user, u -> ConcurrentHashMap.newKeySet()).addAll(userSettings);
        setupUserContainer(userSettings, user, session);
        obService.addUserTL(userSettings);
    }

    private void setupUserContainer(Collection<Settings> userSettings, AppUser user, WebSocketSession session) {
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
        AppUser userFromDB = userOpt.get();

        Set<WebSocketSession> sessions = userToSessions.get(userFromDB);
        if (sessions != null && !sessions.isEmpty()) {
            for (WebSocketSession existingSession : sessions) {
                AppUser existingUser = sessionToUser.get(existingSession);
                if (existingUser != null && existingUser.equals(userFromDB)) {
                    return existingUser;
                }
            }
        }

        return userFromDB;
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException e) {
            log.warn("Failed to close WebSocket session: {}", session.getId(), e);
        }
    }
}
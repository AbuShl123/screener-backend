package dev.abu.screener_backend.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.abu.screener_backend.analysis.TradeListDTO;
import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.appuser.AppUserRepository;
import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.settings.Settings;
import dev.abu.screener_backend.settings.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
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
    private final UserSettingsRepository userSettingsRepository;
    private final OBService obService;

    private final Map<String, TradeListDTO> defaultEvents = new ConcurrentHashMap<>();
    private final Map<AppUser, Set<String>> customizedSymbols = new ConcurrentHashMap<>();
    private final Map<String, Set<AppUser>> settingsToUsersMap = new ConcurrentHashMap<>();
    private final Map<AppUser, Map<String, TradeListDTO>> userContainers = new ConcurrentHashMap<>();
    private final Map<AppUser, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Set<AppUser> usersWithNoSettings = ConcurrentHashMap.newKeySet();

    public synchronized void addSession(WebSocketSession session) {
        AppUser user = extractUser(session);
        if (user == null) return;
        setupUser(user, session);
    }

    @Scheduled(fixedDelay = 100L)
    public synchronized void broadcastData() {
        removeClosedSessions();
        sendDefaultData();
        sendCustomData();
    }

    public synchronized void processTradeEvent(String hash, String mSymbol, TradeListDTO data) {
        if (hash.startsWith("default")) {
            handleDefaultEvents(mSymbol, data);
        } else {
            handleNonDefaultEvents(hash, data);
        }
    }

    public synchronized void deleteTradeEvent(String hash, String mSymbol) {
        if (hash.startsWith("default")) {
            deleteDefaultEvent(mSymbol);
        } else {
            deleteNoNDefaultEvent(hash, mSymbol);
        }
    }

    private void removeClosedSessions() {
        var iterator = userSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var session = entry.getValue();
            if (!session.isOpen()) {
                iterator.remove();
                removeUserSession(entry.getKey(), session);
            }
        }
    }

    private void removeUserSession(AppUser user, WebSocketSession session) {
        List<Settings> allUserSettings = userSettingsRepository.findAllSettingsByAppUser(user);
        if (allUserSettings.isEmpty()) {
            usersWithNoSettings.remove(user);
        } else {
            allUserSettings.forEach(settings -> {
                Set<AppUser> users = settingsToUsersMap.get(settings.getSettingsHash());
                users.remove(user);
                if (users.isEmpty()) {
                    obService.removeTL(settings);
                    settingsToUsersMap.remove(settings.getSettingsHash());
                }
            });
            customizedSymbols.remove(user);
            userContainers.remove(user);
        }
    }

    private void sendDefaultData() {
        byte[] defaultEvent = serialize(defaultEvents.values());
        usersWithNoSettings.forEach(u -> broadCastMessage(u, defaultEvent));
    }

    private void sendCustomData() {
        for (Map.Entry<AppUser, Map<String, TradeListDTO>> userAndContainer : userContainers.entrySet()) {
            Map<String, TradeListDTO> container = userAndContainer.getValue();
            var user = userAndContainer.getKey();
            byte[] message = serialize(container.values());
            broadCastMessage(user, message);
        }
    }

    private byte[] serialize(Collection<TradeListDTO> data) {
        try {
            ArrayNode array = mapper.createArrayNode();
            for (TradeListDTO tradeList : data) {
                JsonNode node = mapper.valueToTree(tradeList);
                array.add(node);
            }
            return mapper.writeValueAsBytes(array);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TradeListDTO data", e);
            return null;
        }
    }

    private void broadCastMessage(AppUser user, byte[] message) {
        try {
            var session = userSessions.get(user);
            if (session == null || !session.isOpen()) return;
            session.sendMessage(new BinaryMessage(message));
        } catch (Exception e) {
            log.warn("Broadcast failed for user {}: {}", user.getEmail(), e.getMessage());
        }
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
        } catch (IOException ignored) {
        }
    }

    private void setupUser(AppUser user, WebSocketSession session) {
        userSessions.put(user, session);

        List<Settings> userSettings = userSettingsRepository.findAllSettingsByAppUser(user);
        if (userSettings.isEmpty()) {
            usersWithNoSettings.add(user);
            return;
        }

        userContainers.putIfAbsent(user, new HashMap<>());
        for (Settings settings : userSettings) {
            addCustomizedSymbol(user, settings);
            mapSettingsToUser(user, settings);
            obService.addNewTL(settings);
        }
    }

    private void addCustomizedSymbol(AppUser user, Settings settings) {
        String mSymbol = settings.getMSymbol();
        if (customizedSymbols.containsKey(user)) {
            customizedSymbols.get(user).add(mSymbol);
        } else {
            HashSet<String> mSymbols = new HashSet<>();
            mSymbols.add(mSymbol);
            customizedSymbols.put(user, mSymbols);
        }
    }

    private void mapSettingsToUser(AppUser user, Settings settings) {
        String hash = settings.getSettingsHash();
        if (settingsToUsersMap.containsKey(hash)) {
            settingsToUsersMap.get(hash).add(user);
        } else {
            HashSet<AppUser> set = new HashSet<>();
            set.add(user);
            settingsToUsersMap.put(hash, set);
        }
    }

    private void handleNonDefaultEvents(String hash, TradeListDTO data) {
        Set<AppUser> matchingUsers = settingsToUsersMap.get(hash);
        if (matchingUsers != null) {
            for (AppUser user : matchingUsers) {
                userContainers.get(user).put(data.s(), data);
            }
        }
    }

    private void handleDefaultEvents(String mSymbol, TradeListDTO data) {
        defaultEvents.put(data.s(), data);;
        for (Map.Entry<AppUser, Set<String>> csEntry : customizedSymbols.entrySet()) {
            if (csEntry.getValue().contains(mSymbol)) continue;
            AppUser user = csEntry.getKey();
            userContainers.get(user).put(data.s(), data);;
        }
    }

    private void deleteNoNDefaultEvent(String hash, String mSymbol) {
        settingsToUsersMap.get(hash).forEach(user -> userContainers.get(user).remove(hash));
    }

    private void deleteDefaultEvent(String mSymbol) {
        defaultEvents.remove(mSymbol);
        for (Map.Entry<AppUser, Set<String>> csSymbols : customizedSymbols.entrySet()) {
            if (csSymbols.getValue().contains(mSymbol)) continue;
            userContainers.get(csSymbols.getKey()).remove(mSymbol);
        }
    }
}
package dev.abu.screener_backend.websockets;

import dev.abu.screener_backend.binance.dt.TradeListDTO;
import dev.abu.screener_backend.settings.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class EventDistributor {

    private final Map<String, Set<UserContainer>> users = new ConcurrentHashMap<>();
    private final Map<UserContainer, Set<String>> customizedSymbols = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 100L)
    public void broadcastData() {
        try {
            Set<UserContainer> seen = ConcurrentHashMap.newKeySet();
            users.values().stream()
                    .flatMap(Set::stream)
                    .filter(seen::add)
                    .forEach(UserContainer::broadcastEvents);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    public void distribute(String hash, int level, TradeListDTO tradeListDTO) {
        try {
            if (level == 0) {
                deleteEvents(hash, tradeListDTO);
            } else {
                addEvent(hash, tradeListDTO);
            }
        } catch (Exception e) {
            log.error("Could not distribute event for {}", tradeListDTO.s(), e);
        }
    }

    public void registerUser(Collection<Settings> settings, UserContainer userContainer) {
        for (Settings s : settings) {
            var hash = s.getSettingsHash();
            if (!hash.contains("all")) {
                customizedSymbols.computeIfAbsent(userContainer, k -> ConcurrentHashMap.newKeySet()).add(s.getMSymbol());
            }
            if (users.containsKey(hash)) {
                users.get(hash).add(userContainer);
            } else {
                Set<UserContainer> set = ConcurrentHashMap.newKeySet();
                set.add(userContainer);
                users.put(hash, set);
            }
        }
    }

    public void unregisterUser(Collection<Settings> settings, UserContainer userContainer) {
        customizedSymbols.remove(userContainer);
        for (Settings s : settings) {
            var hash = s.getSettingsHash();
            if (users.containsKey(hash)) {
                Set<UserContainer> containers = users.get(hash);
                containers.remove(userContainer);
                if (containers.isEmpty()) {
                    users.remove(hash);
                }
            }
        }
    }

    private void deleteEvents(String hash, TradeListDTO tradeListDTO) {
        Set<UserContainer> containers = getUsers(hash);
        if (containers == null) return;
        String mSymbol = tradeListDTO.s();

        if (hash.contains("all")) {
            containers.stream()
                    .filter(user -> {
                        Set<String> cs = customizedSymbols.get(user);
                        return cs == null || !cs.contains(mSymbol);
                    })
                    .forEach(u -> u.remove(mSymbol));
        } else {
            containers.forEach(u -> u.remove(mSymbol));
        }
    }

    private void addEvent(String hash, TradeListDTO tradeListDTO) {
        Set<UserContainer> containers = users.get(hash);
        if (containers == null || containers.isEmpty()) return;

        String mSymbol = tradeListDTO.s();

        if (hash.contains("all")) {
            containers.stream()
                    .filter(user -> {
                        Set<String> cs = customizedSymbols.get(user);
                        return cs == null || !cs.contains(mSymbol);
                    })
                    .forEach(user -> user.take(mSymbol, tradeListDTO));
        } else {
            containers.forEach(user -> user.take(mSymbol, tradeListDTO));
        }
    }

    private Set<UserContainer> getUsers(String hash) {
        return users.get(hash);
    }
}

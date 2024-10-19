package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.entity.ScreenerUser;
import dev.abu.screener_backend.entity.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParam;
import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParams;

@Component
@Slf4j
public class WSOrderBookHandler extends TextWebSocketHandler {

    /** Storing all users connected to this websocket */
    private final Set<ScreenerUser> users = new HashSet<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        addSession(session);
        log.info("New Websocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        closeSession(session);
        log.info("Websocket connection closed: {}", session.getId());
    }

    private void addSession(WebSocketSession session) {
        Ticker ticker = Ticker.valueOf( ((String) session.getAttributes().get("ticker")).toUpperCase() );
        String priceSpan = getQueryParam(session, "priceSpan");
        users.add(new ScreenerUser(session, ticker, priceSpan));
    }

    private void closeSession(WebSocketSession session) {
        Iterator<ScreenerUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            ScreenerUser userSession = iterator.next();
            if (userSession.getId().equals(session.getId())) {
                try {
                    userSession.closeConnection();
                } catch (IOException e) {
                    log.error("Failure while closing Websocket session ", e);
                }
                iterator.remove();
            }
        }
    }
}
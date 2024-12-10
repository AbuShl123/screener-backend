package dev.abu.screener_backend.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RequestUtilities {

    private RequestUtilities() {}

    public synchronized static Map<String, String> getQueryParams(WebSocketSession session) {
        String query = Objects.requireNonNull(session.getUri()).getQuery();
        return getQueryParams(query);
    }

    public synchronized static Map<String, String> getQueryParams(HttpServletRequest request) {
        String query = request.getQueryString();
        return getQueryParams(query);
    }

    public synchronized static String getQueryParam(WebSocketSession session, String name) {
        return getQueryParams(session).get(name);
    }

    public synchronized static String getQueryParam(HttpServletRequest request, String name) {
        return getQueryParams(request).get(name);
    }

    public synchronized static Map<String, String> getQueryParams(String query) {
        Map<String, String> queryParams = new HashMap<>();

        if (query == null) return queryParams;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length > 1) {
                queryParams.put(keyValue[0], keyValue[1]);
            }
        }

        return queryParams;
    }

    /**
     * Returns value of 'token' query parameter from websocket request. If the 'token' param is not present, then method will return {@code null}.
     *
     * @param request {@link HttpServletRequest} - request to get query parameters from
     * @return JWT token as {@link String} if successful, {@code null} otherwise
     */
    public synchronized static String getToken(HttpServletRequest request) {
        var params = getQueryParams(request);
        String token = params.get("token");
        return ( token == null || token.isEmpty() ) ? null : token;
    }
}

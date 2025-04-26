package dev.abu.screener_backend.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RequestUtilities {

    public static final String EMAIL_NOT_VALID = "Email is not valid.";
    public static final String EMAIL_TAKEN = "Email is taken.";
    public static final String EMAIL_SENT_SUCCESSFULLY = "Email is sent successfully.";
    public static final String TOKEN_NOT_FOUND = "Token not found.";

    public static final String USER_NOT_FOUND = "User not found.";
    public static final String USER_NOT_ENABLED = "User not enabled.";

    public static final String USER_ALREADY_SUBSCRIBED = "User already has active subscription.";
    public static final String USER_NOT_SUBSCRIBED = "User is not subscribed.";
    public static final String SUBSCRIPTION_PLAN_NOT_FOUND = "Subscription plan not found.";
    public static final String INVALID_QTY_PROVIDED = "Invalid qty amount is provided.";
    public static final String SUBSCRIBED_SUCCESSFULLY = "Subscribed successfully.";
    public static final String RENEWED_SUCCESSFULLY = "Subscription renewed successfully.";

    private RequestUtilities() {}

    public synchronized static Map<String, String> getQueryParams(WebSocketSession session) {
        String query = Objects.requireNonNull(session.getUri()).getQuery();
        return getQueryParams(query);
    }

    public synchronized static Map<String, String> getQueryParams(HttpServletRequest request) {
        String query = request.getQueryString();
        return getQueryParams(query);
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

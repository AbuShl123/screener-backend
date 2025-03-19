package dev.abu.screener_backend.registration;

import lombok.Builder;

import java.util.HashMap;
import java.util.Map;

@Builder
public class RegistrationResponse {

    private String confirmationToken;
    private String message;

    public Map<String, Object> create() {
        var map = new HashMap<String, Object>();
        map.put("message", message);
        map.put("confirmationToken", confirmationToken);
        return map;
    }
}

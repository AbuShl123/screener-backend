package dev.abu.screener_backend.registration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {
    private String token;

    public Map<String, Object> create() {
        var map = new HashMap<String, Object>();
        map.put("token", token);
        return map;
    }
}

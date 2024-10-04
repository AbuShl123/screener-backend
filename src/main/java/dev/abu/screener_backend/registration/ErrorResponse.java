package dev.abu.screener_backend.registration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

    private HttpStatus status;
    private String message;
    private String errorCode;

    public ResponseEntity<?> toResponseEntity() {
        var map = new HashMap<String, Object>();
        map.put("status", status.getReasonPhrase());
        map.put("message", message);
        map.put("errorCode", errorCode);
        return new ResponseEntity<>(map, status);
    }
}

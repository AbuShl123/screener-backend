package dev.abu.screener_backend.controllers;


import dev.abu.screener_backend.registration.AuthenticationRequest;
import dev.abu.screener_backend.registration.AuthenticationService;
import dev.abu.screener_backend.registration.RegisterRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping(path = "api/v1/auth")
public class AuthenticationController {

    public final AuthenticationService service;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request
    ) {
        return service.register(request);
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> register(
            @RequestBody AuthenticationRequest request
    ) {
        return service.authenticate(request);
    }

    @GetMapping("/confirm")
    public ResponseEntity<String> confirm(
            @RequestParam("confirmationToken") String confirmationToken,
            @RequestParam("email") String email
    ) {
        return service.confirmToken(confirmationToken, email);
    }
}

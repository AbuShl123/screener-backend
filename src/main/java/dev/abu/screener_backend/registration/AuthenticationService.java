package dev.abu.screener_backend.registration;

import dev.abu.screener_backend.appuser.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String EMAIL_NOT_VALID_MSG = "Email is not valid.";
    private static final String EMAIL_NOT_VALID = "EMAIL_NOT_VALID";
    private static final String EMAIL_TAKEN_MSG = "Email is taken.";
    private static final String EMAIL_TAKEN_ERROR = "EMAIL_TAKEN_ERROR";

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailValidator emailValidator;
    private final AuthenticationManager authenticationManager;

    public ResponseEntity<?> register(RegisterRequest request) {

        boolean isValidEmail = emailValidator.test(request.getEmail());
        if (!isValidEmail) return emailNotValidResponse();

        var user = request.getAppUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        boolean userExists = repository.findByEmail( user.getEmail() ).isPresent();
        if (userExists) return emailTakenResponse();

        repository.save( user );
        var jwtToken = jwtService.generateToken( user );
        var authResponse = AuthenticationResponse.builder().token( jwtToken ).build();
        return new ResponseEntity<>(authResponse.create(), HttpStatus.OK);

    }

    public ResponseEntity<?> authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = repository.findByEmail( request.getEmail() ).orElseThrow(
                () -> new UsernameNotFoundException(EMAIL_NOT_VALID)
        );

        var jwtToken = jwtService.generateToken( user );
        var authResponse = AuthenticationResponse.builder().token( jwtToken ).build();
        return new ResponseEntity<>(authResponse.create(), HttpStatus.OK);
    }

    public String confirmToken(String token) {
        return null;
    }

    private static ResponseEntity<?> emailNotValidResponse() {
        var errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(EMAIL_NOT_VALID_MSG)
                .errorCode(EMAIL_NOT_VALID)
                .build();
        return errorResponse.toResponseEntity();
    }

    private static ResponseEntity<?> emailTakenResponse() {
        var errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(EMAIL_TAKEN_MSG)
                .errorCode(EMAIL_TAKEN_ERROR)
                .build();
        return errorResponse.toResponseEntity();
    }
}

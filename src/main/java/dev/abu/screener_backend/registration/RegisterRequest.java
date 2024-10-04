package dev.abu.screener_backend.registration;

import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.appuser.AppUserRole;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    private String firstname;
    private String lastname;
    private String email;
    private String password;

    public AppUser getAppUser() {
        return AppUser.builder()
                .firstname(firstname)
                .lastname(lastname)
                .email(email)
                .password(password)
                .userRole(AppUserRole.USER)
                .build();
    }

}

package dev.abu.screener_backend.appuser;

import dev.abu.screener_backend.subscription.SubscriptionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserResponse {

    private long id;
    private String firstname;
    private String lastname;
    private String email;
    private boolean enabled;
    private AppUserRole userRole;
    private SubscriptionResponse subscription;

    public static UserResponse create(AppUser user, SubscriptionResponse subscription) {
        return new UserResponse(
                user.getId(),
                user.getFirstname(),
                user.getLastname(),
                user.getEmail(),
                user.isEnabled(),
                user.getUserRole(),
                subscription
        );
    }
}

package dev.abu.screener_backend.registration;

import dev.abu.screener_backend.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;

@RequiredArgsConstructor
public class SubscriptionPermissionEvaluator implements PermissionEvaluator {

    private final SubscriptionService subscriptionService;

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Object targetDomainObject,
            Object permission
    ) {
        String permissionString = (String) permission;
        if (permissionString.equalsIgnoreCase("SUBSCRIBED")) {
            UserDetails user = (UserDetails) authentication.getPrincipal();
            return subscriptionService.isUserSubscribed(user);
        }
        return true;
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission
    ) {
        String permissionString = (String) permission;
        if (permissionString.equalsIgnoreCase("SUBSCRIBED")) {
            UserDetails user = (UserDetails) authentication.getPrincipal();
            return subscriptionService.isUserSubscribed(user);
        }
        return true;
    }

}

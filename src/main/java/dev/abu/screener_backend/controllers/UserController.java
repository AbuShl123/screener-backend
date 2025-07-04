package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.appuser.AppUserService;
import dev.abu.screener_backend.appuser.UserResponse;
import dev.abu.screener_backend.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/v1/user")
public class UserController {

    private final AppUserService userService;
    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        var user = userService.getUserByEmail(userDetails.getUsername());
        var subscription = subscriptionService.getUserSubscription(user);
        var userResponse = UserResponse.create(user, subscription);
        return ResponseEntity.ok(userResponse);
    }
}

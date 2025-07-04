package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.subscription.SubscriptionResponse;
import dev.abu.screener_backend.subscription.SubscriptionService;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1/subscribe")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping("/{subscriptionPlanId}")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable final long subscriptionPlanId
    ) {
        return subscriptionService.subscribe(appUser, subscriptionPlanId);
    }

    @GetMapping
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @AuthenticationPrincipal AppUser appUser
    ) {
        SubscriptionResponse subscriptionResponse = subscriptionService.getUserSubscription(appUser);
        if (subscriptionResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscriptionResponse);
    }

    @PostMapping("/renew")
    public ResponseEntity<SubscriptionResponse> renew(
            @AuthenticationPrincipal AppUser appUser
    ) {
        var response = subscriptionService.renewSubscription(appUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping()
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal AppUser appUser
    ) {
        subscriptionService.deleteSubscription(appUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> allSubscriptionPlans() {
        return subscriptionPlanService.getAllSubscriptionPlans();
    }
}

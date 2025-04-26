package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.subscription.SubscriptionResponse;
import dev.abu.screener_backend.subscription.SubscriptionService;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1/subscribe")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping("/{subscriptionPlanId}/{email}")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @PathVariable final long subscriptionPlanId,
            @PathVariable final String email
    ) {
        return subscriptionService.subscribe(email, subscriptionPlanId);
    }

    @GetMapping("/{email}")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @PathVariable final String email
    ) {
        SubscriptionResponse subscriptionResponse = subscriptionService.getUserSubscription(email);
        if (subscriptionResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscriptionResponse);
    }

    @PostMapping("/renew/{email}")
    public ResponseEntity<SubscriptionResponse> renew(
            @PathVariable final String email
    ) {
        var response = subscriptionService.renewSubscription(email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable final String email
    ) {
        subscriptionService.deleteSubscription(email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> allSubscriptionPlans() {
        return subscriptionPlanService.getAllSubscriptionPlans();
    }
}

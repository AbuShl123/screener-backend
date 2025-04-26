package dev.abu.screener_backend.subscription.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public ResponseEntity<List<SubscriptionPlan>> getAllSubscriptionPlans() {
        var list = subscriptionPlanRepository.findAll();
        return ResponseEntity.ok(list);
    }
}

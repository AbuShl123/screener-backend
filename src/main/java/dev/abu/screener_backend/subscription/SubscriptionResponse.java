package dev.abu.screener_backend.subscription;

import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Builder
@Getter
@Setter
public class SubscriptionResponse {

    private long id;
    private String message;
    private SubscriptionPlan subscriptionPlan;
    private SubscriptionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

}

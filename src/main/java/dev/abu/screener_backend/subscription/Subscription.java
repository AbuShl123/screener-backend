package dev.abu.screener_backend.subscription;

import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Subscription {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(nullable = false, name = "app_user_id")
    private AppUser appUser;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @ManyToOne
    @JoinColumn(nullable = false, name = "subscription_plan_id")
    private SubscriptionPlan subscriptionPlan;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public Subscription(
            AppUser appUser,
            SubscriptionStatus status,
            SubscriptionPlan subscriptionPlan,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.appUser = appUser;
        this.status = status;
        this.subscriptionPlan = subscriptionPlan;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}

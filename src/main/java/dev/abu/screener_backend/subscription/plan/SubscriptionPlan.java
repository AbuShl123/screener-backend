package dev.abu.screener_backend.subscription.plan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class SubscriptionPlan {

    @Id
    @GeneratedValue
    private long id;

    private String name;
    private String description;
    private double price;
    private String currency;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlanDuration duration;

    public SubscriptionPlan(String name, String description, double price, String currency, SubscriptionPlanDuration duration) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.duration = duration;
    }
}

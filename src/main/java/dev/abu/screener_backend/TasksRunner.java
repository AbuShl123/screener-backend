package dev.abu.screener_backend;

import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanDuration;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class TasksRunner implements CommandLineRunner {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    public void run(String... args) {
    }

    private void addSubscriptionPlans() {
        SubscriptionPlan trialPlan = new SubscriptionPlan(
                "Бесплатный период на неделю",
                "Попробуйте бесплатно перед подпиской – никаких рисков, только преимущества!",
                0,
                "USD",
                SubscriptionPlanDuration.WEEK
        );

        SubscriptionPlan monthlyPlan = new SubscriptionPlan(
                "Ежемесячная подписка",
                "Оформляйте свою подписку каждый месяц и получайте максимум возможностей!",
                5,
                "USD",
                SubscriptionPlanDuration.MONTH
        );

        SubscriptionPlan yearlyPlan = new SubscriptionPlan(
                "Годовая подписка - скидка 20%",
                "Оформите подписку сейчас и наслаждайтесь весь год!",
                48,
                "USD",
                SubscriptionPlanDuration.YEAR
        );

        subscriptionPlanRepository.save(trialPlan);
        subscriptionPlanRepository.save(monthlyPlan);
        subscriptionPlanRepository.save(yearlyPlan);

    }
}
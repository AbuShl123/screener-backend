package dev.abu.screener_backend;

import dev.abu.screener_backend.appuser.AppUserRepository;
import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.settings.*;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanDuration;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class TasksRunner implements CommandLineRunner {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SettingsRepository settingsRepository;
    private final AppUserRepository appUserRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SettingsService settingsService;
    private final OBService obService;

    @Override
    public void run(String... args) {
        addDefaultSettings();
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

    public void resetSettingsForAllUsers() {
        appUserRepository.findAll().forEach(settingsService::resetSettings);
    }

    public void addDefaultSettings() {
        List<Settings> defaultSettings = settingsRepository.findAllDefaultSettings();
        if (!defaultSettings.isEmpty()) return;

        // 0.5, 500_000
        // 1.0, 1_000_000
        // 2.0, 3_000_000
        // 6.0, 10_000_000
        List<SettingsEntry> entries = new ArrayList<>();
        entries.add(new SettingsEntry(0.5, 500_000));
        entries.add(new SettingsEntry(1.0, 1_000_000));
        entries.add(new SettingsEntry(2.0, 3_000_000));
        entries.add(new SettingsEntry(5.0, 10_000_000));

        Settings settings = new Settings("all", true, SettingsType.DOLLAR, entries, "default_all");
        settingsRepository.save(settings);

        var largeTickers = Set.of("btcusdt", "btcusdt.f", "ethusdt", "ethusdt.f", "solusdt", "solusdt.f");
        entries.forEach((entry) -> entry.setValue(entry.getValue() * 10));

        for (String largeTicker : largeTickers) {
            settings = new Settings(largeTicker, true, SettingsType.DOLLAR, entries, "default_" + largeTicker);
            settingsRepository.save(settings);
        }

        log.info("Added default settings");
        resetSettingsForAllUsers();
        log.info("Reset settings for all users");
    }
}
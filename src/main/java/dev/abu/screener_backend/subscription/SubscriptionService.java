package dev.abu.screener_backend.subscription;

import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlan;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanDuration;
import dev.abu.screener_backend.subscription.plan.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static dev.abu.screener_backend.utils.RequestUtilities.*;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void deactivateInactiveSubscriptions() {
        subscriptionRepository.markExpiredSubscriptions(LocalDateTime.now(), SubscriptionStatus.EXPIRED);
    }

    @Transactional
    public ResponseEntity<SubscriptionResponse> subscribe(AppUser user, long subscriptionPlanId) {
        // check if user already has active subscription
        boolean isSubscribedAlready = subscriptionRepository.findByAppUser(user).isPresent();
        if (isSubscribedAlready) {
            throw new IllegalStateException(USER_ALREADY_SUBSCRIBED);
        }

        // find subscription plan, if it doesn't exist then throw error
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(subscriptionPlanId)
                .orElseThrow(() -> new IllegalStateException(SUBSCRIPTION_PLAN_NOT_FOUND));

        // create subscription
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = null;
        SubscriptionStatus status = SubscriptionStatus.IDLE;

        if (subscriptionPlan.getPrice() == 0) {
            expiresAt = getNewExpiryDate(null, subscriptionPlan.getDuration());
            status = SubscriptionStatus.ACTIVE;
        }

        Subscription subscription = new Subscription(
                user,
                status,
                subscriptionPlan,
                createdAt,
                expiresAt
        );

        subscriptionRepository.save(subscription);
        var response = generateResponse(subscription, SUBSCRIBED_SUCCESSFULLY);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Transactional
    public void deleteSubscription(final AppUser user) {
        final long userId = user.getId();
        subscriptionRepository.findByAppUser_Id(userId).orElseThrow(() -> new IllegalStateException(USER_NOT_SUBSCRIBED));
        subscriptionRepository.deleteByAppUser_Id(userId);
    }

    @Transactional
    public SubscriptionResponse renewSubscription(final AppUser user) {
        long userId = user.getId();
        Subscription subscription = subscriptionRepository.findByAppUser_Id(userId).orElseThrow(() -> new IllegalStateException(USER_NOT_SUBSCRIBED));
        SubscriptionPlan plan = subscription.getSubscriptionPlan();

        if (subscription.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(USER_ALREADY_SUBSCRIBED);
        }

        long id = subscription.getId();
        LocalDateTime newExpiryDate = getNewExpiryDate(subscription.getExpiresAt(), plan.getDuration());
        subscriptionRepository.updateSubscriptionExpiration(id, newExpiryDate);
        subscriptionRepository.updateSubscriptionStatus(id, SubscriptionStatus.ACTIVE);
        return generateResponse(id, plan, RENEWED_SUCCESSFULLY, SubscriptionStatus.ACTIVE, subscription.getCreatedAt(), newExpiryDate);
    }

    public SubscriptionResponse getUserSubscription(final AppUser user) {
        final long userId = user.getId();
        Subscription subscription = subscriptionRepository.findByAppUser_Id(userId).orElse(null);

        if (subscription == null) return null;
        return generateResponse(subscription, SUBSCRIBED_SUCCESSFULLY);
    }

    public boolean isUserSubscribed(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            return false;
        }

        return subscriptionRepository.findByAppUser_Email(userDetails.getUsername())
                .filter(subscription -> subscription.getExpiresAt() != null) // Ensure expiresAt is not null
                .map(subscription ->
                        subscription.getExpiresAt().isAfter(LocalDateTime.now())
                                && subscription.getStatus() == SubscriptionStatus.ACTIVE
                )
                .orElse(false);
    }

    private LocalDateTime getNewExpiryDate(LocalDateTime initialDate, SubscriptionPlanDuration planDuration) {
        initialDate = initialDate == null ? LocalDateTime.now() : initialDate;
        LocalDateTime expiryDate;
        switch (planDuration) {
            case DAY -> expiryDate = initialDate.plusDays(1);
            case WEEK -> expiryDate = initialDate.plusWeeks(1);
            case MONTH -> expiryDate = initialDate.plusMonths(1);
            case YEAR -> expiryDate = initialDate.plusYears(1);
            default -> throw new RuntimeException("Unexpected value: " + planDuration);
        }
        return expiryDate;
    }

    private SubscriptionResponse generateResponse(Subscription subscription, String message) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .subscriptionPlan(subscription.getSubscriptionPlan())
                .message(message)
                .status(subscription.getStatus())
                .createdAt(subscription.getCreatedAt())
                .expiresAt(subscription.getExpiresAt()).build();
    }

    private SubscriptionResponse generateResponse(
            long id,
            SubscriptionPlan plan,
            String message,
            SubscriptionStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        return SubscriptionResponse.builder()
                .id(id)
                .subscriptionPlan(plan)
                .message(message)
                .status(status)
                .createdAt(createdAt)
                .expiresAt(expiresAt).build();
    }
}
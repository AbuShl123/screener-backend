package dev.abu.screener_backend.subscription;

import dev.abu.screener_backend.appuser.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByAppUser(AppUser appUser);

    Optional<Subscription> findByAppUser_Email(String email);

    Optional<Subscription> findByAppUser_Id(long userId);

    void deleteByAppUser_Id(long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Subscription s SET s.status = :status WHERE s.id = :id")
    void updateSubscriptionStatus(@Param("id") Long id, @Param("status") SubscriptionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Subscription s SET s.expiresAt = :newExpiresAt WHERE s.id = :id")
    void updateSubscriptionExpiration(@Param("id") Long id, @Param("newExpiresAt") LocalDateTime expiresAt);

    @Modifying
    @Transactional
    @Query("UPDATE Subscription s SET s.status = :status WHERE s.expiresAt < :now AND s.status != :status")
    void markExpiredSubscriptions(@Param("now") LocalDateTime now,
                                 @Param("status") SubscriptionStatus status);
}

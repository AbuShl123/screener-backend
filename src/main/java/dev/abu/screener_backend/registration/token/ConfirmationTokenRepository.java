package dev.abu.screener_backend.registration.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ConfirmationTokenRepository extends JpaRepository<ConfirmationToken, Long> {

    Optional<ConfirmationToken> findByToken(String token);

    @Transactional
    @Modifying
    @Query("""
            UPDATE ConfirmationToken c
            SET c.confirmedAt = :confirmedAt
            WHERE c.token = :token
            """)
    int updateConfirmedAt(String token, LocalDateTime confirmedAt);

    @Query("""
            select ct.expiresAt
            from AppUser u
            inner join ConfirmationToken ct on u.id = ct.appUser.id
            where ct.confirmedAt is NULL and u.email = :email
            order by ct.expiresAt desc
            limit 1
            """)
    LocalDateTime getLatestUnconfirmedExpirationDate(String email);
}

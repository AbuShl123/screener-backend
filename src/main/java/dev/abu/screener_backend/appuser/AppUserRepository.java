package dev.abu.screener_backend.appuser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    @Transactional
    @Modifying
    @Query("""
        UPDATE AppUser u
        SET u.enabled=true WHERE u.email=:email
        """)
    int enableAppUser(String email);

    @Transactional
    @Modifying
    @Query("""
        UPDATE AppUser u
        SET u.enabled=false WHERE u.email=:email
        """)
    int disableAppUser(String email);
}
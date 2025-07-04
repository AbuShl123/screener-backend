package dev.abu.screener_backend.settings;

import dev.abu.screener_backend.appuser.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByAppUserAndSettings_mSymbol(AppUser user, String mSymbol);

    List<UserSettings> findAllByAppUser(AppUser appUser);

    @Query("SELECT us.settings FROM UserSettings us where us.appUser = :appUser")
    Collection<Settings> findAllSettingsByAppUser(@Param("appUser") AppUser appUser);

    @Query("SELECT us.settings.settingsHash FROM UserSettings us where us.appUser = :appUser")
    Set<String> findAllSettingHashesByAppUser(@Param("appUser") AppUser appUser);

    @Query("SELECT COUNT(us) FROM UserSettings us WHERE us.settings = :settings")
    long countBySettings(@Param("settings") Settings settings);

    void deleteAllByAppUser(AppUser appUser);
}

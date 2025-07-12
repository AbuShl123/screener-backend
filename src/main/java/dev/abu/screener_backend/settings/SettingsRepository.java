package dev.abu.screener_backend.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettingsRepository extends JpaRepository<Settings, Long> {
    Optional<Settings> findBySettingsHash(String settingsHash);

    @Modifying
    @Query("DELETE FROM Settings s WHERE s.id NOT IN (SELECT us.settings.id FROM UserSettings us) and s.settingsHash not like 'default%'")
    void deleteOrphanedSettings();

    @Query("SELECT s FROM Settings s WHERE s.settingsHash LIKE 'default%'")
    List<Settings> findAllDefaultSettings();

    @Query("SELECT s FROM Settings s WHERE s.settingsHash = CONCAT('default_', :mSymbol)")
    Optional<Settings> findDefaultSettings(@Param("mSymbol") String mSymbol);

    @Query("SELECT s FROM Settings s WHERE s.mSymbol = 'all'")
    Optional<Settings> findDefaultSettingsForAllSymbols();

}

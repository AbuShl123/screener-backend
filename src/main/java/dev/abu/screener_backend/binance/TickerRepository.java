package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.binance.entities.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TickerRepository extends JpaRepository<Ticker, String> {

    List<Ticker> findByHasSpotTrue();

    List<Ticker> findByHasFutTrue();
}

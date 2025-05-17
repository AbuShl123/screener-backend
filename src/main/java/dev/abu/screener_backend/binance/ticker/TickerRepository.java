package dev.abu.screener_backend.binance.ticker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TickerRepository extends JpaRepository<Ticker, String> {

    List<Ticker> findByHasSpotTrue();

    List<Ticker> findByHasFutTrue();
}

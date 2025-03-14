package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.entity.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TickerRepository extends JpaRepository<Ticker, String> {
}

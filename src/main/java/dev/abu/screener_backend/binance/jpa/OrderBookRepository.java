package dev.abu.screener_backend.binance.jpa;

import dev.abu.screener_backend.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OrderBookRepository extends JpaRepository<Trade, Long> {

    @Transactional
    @Modifying
    void deleteBySymbol(String symbol);

}

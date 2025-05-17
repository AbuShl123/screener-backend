package dev.abu.screener_backend.binance.density;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DensityRepository extends JpaRepository<Density, Double> {

    List<Density> findByIdSymbol(String symbol);

    Optional<Density> findByIdSymbolAndIdPrice(String symbol, BigDecimal price);

    void deleteByIdSymbolAndIdPrice(String symbol, BigDecimal price);

    @Modifying
    @Transactional
    @Query("UPDATE Density d SET d.qty = :qty WHERE d.id.symbol = :symbol AND d.id.price = :price")
    int updateQtyBySymbolAndPrice(@Param("qty") double qty,
                                  @Param("symbol") String symbol,
                                  @Param("price") BigDecimal price);
}

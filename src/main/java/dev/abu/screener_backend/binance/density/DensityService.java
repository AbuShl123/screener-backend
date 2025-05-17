package dev.abu.screener_backend.binance.density;

import com.fasterxml.jackson.databind.JsonNode;
import dev.abu.screener_backend.binance.ticker.TickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Math.abs;

@RequiredArgsConstructor
@Service
@Slf4j
public class DensityService {

    private final JdbcTemplate jdbcTemplate;
    private final List<Density> batchedDensities = new ArrayList<>();
    private final int batchSize = 1000;

    public synchronized void saveDensities(String mSymbol, JsonNode root, JsonNode asksArray, JsonNode bidsArray) {
        long timestamp = getTimeStamp(root);
        traverseAndSaveDensities(mSymbol, asksArray, timestamp, true);
        traverseAndSaveDensities(mSymbol, bidsArray, timestamp, false);
    }

    private long getTimeStamp(JsonNode root) {
        JsonNode eField = root.get("E");
        if (eField == null) return System.currentTimeMillis();
        long timestamp = eField.asLong();
        return timestamp == 0 ? System.currentTimeMillis() : timestamp;
    }

    private void traverseAndSaveDensities(String mSymbol, JsonNode array, long timestamp, boolean isAsk) {
        if (array == null || (array.isArray() && array.isEmpty())) {
            return;
        }
        for (JsonNode node : array) {
            BigDecimal price = BigDecimal.valueOf(node.get(0).asDouble());
            double qty = node.get(1).asDouble();
            filterByDistance(mSymbol, price, qty, isAsk, timestamp);
        }
    }

    private void filterByDistance(String mSymbol, BigDecimal price, double qty, boolean isAsk, long timestamp) {
        double distance = getDistance(mSymbol, price);
        if (abs(distance) <= MAX_INCLINE) {
            saveDensity(mSymbol, price, qty, isAsk, timestamp);
        }
    }

    private void saveDensity(String mSymbol, BigDecimal price, double qty, boolean isAsk, long timestamp) {
        var symbol = mSymbol.replace(FUT_SIGN, "_f");
        DensityId id = new DensityId(symbol, price);
        Density density = new Density(id, qty, isAsk, timestamp);
        batchedDensities.add(density);
        if (batchedDensities.size() >= batchSize) {
            upsertDensities(batchedDensities);
            batchedDensities.clear();
        }
    }

    public void upsertDensities(List<Density> densities) {
        String sql = """
                INSERT INTO density (symbol, price, qty, life, statecode)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (symbol, price)
                DO UPDATE SET qty = EXCLUDED.qty
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Density d = densities.get(i);
                ps.setString(1, d.getId().getSymbol());
                ps.setBigDecimal(2, d.getId().getPrice());
                ps.setDouble(3, d.getQty());
                ps.setDouble(4, d.getLife());
                ps.setInt(5, d.getStatecode());
            }

            @Override
            public int getBatchSize() {
                return densities.size();
            }
        });
    }

    private double getDistance(String mSymbol, BigDecimal price) {
        double marketPrice = TickerService.getPrice(mSymbol);
        double ratio = price.doubleValue() / marketPrice;
        return (ratio - 1) * 100;
    }
}
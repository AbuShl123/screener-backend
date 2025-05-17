package dev.abu.screener_backend.binance.density;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PartitionManagerService {

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> knownPartitions = ConcurrentHashMap.newKeySet();

    public synchronized void ensurePartitionExists(String symbol) {
        if (knownPartitions.contains(symbol)) {
            return;
        }

        String partitionName = "density_" + symbol.toLowerCase();
        String sql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s
                    PARTITION OF density FOR VALUES IN ('%s');
                """, partitionName, symbol);

        jdbcTemplate.execute(sql);
        knownPartitions.add(symbol);
    }
}


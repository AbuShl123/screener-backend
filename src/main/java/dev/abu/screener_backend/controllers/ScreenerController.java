package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.binance.MaxOrdersService;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.bitget.BitgetOpenInterestService;
import dev.abu.screener_backend.entity.Ticker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1")
public class ScreenerController {

    private final MaxOrdersService maxOrdersService;
    private final TickerService tickerService;
    private final BitgetOpenInterestService oiService;

    @GetMapping("/tickers")
    public List<Ticker> allTickers() {
        return tickerService.getAllTickers();
    }

    @GetMapping("/max-orders")
    public ResponseEntity<String> getMaxOrders() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(maxOrdersService.getMaxOrders());
    }

    @GetMapping("/openInterest")
    public ResponseEntity<String> getOpenInterest() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(oiService.getHistoricalOI());
    }
}

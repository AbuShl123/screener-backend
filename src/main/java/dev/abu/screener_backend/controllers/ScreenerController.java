package dev.abu.screener_backend.controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.annotations.SubscribedOnly;
import dev.abu.screener_backend.binance.BitgetOpenInterestService;
import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.binance.TickerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@SubscribedOnly
@RequestMapping(path = "api/v1")
public class ScreenerController {

    private final TickerService tickerService;
    private final BitgetOpenInterestService oiService;

    @GetMapping("/tickers")
    public List<Ticker> allTickers() {
        return tickerService.getAllTickers();
    }

    @GetMapping("/openInterest")
    public ResponseEntity<String> getOpenInterest() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(oiService.getHistoricalOI());
    }

    @GetMapping("/ticker-price-change")
    public ResponseEntity<List<ObjectNode>> getTickerPriceChange() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(tickerService.getHistory());
    }
}

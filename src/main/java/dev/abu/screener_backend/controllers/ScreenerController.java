package dev.abu.screener_backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.annotations.SubscribedOnly;
import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.binance.BinanceService;
import dev.abu.screener_backend.binance.BitgetOpenInterestService;
import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.settings.SettingsRequestDTO;
import dev.abu.screener_backend.settings.SettingsService;
import dev.abu.screener_backend.settings.UserSettingsResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@SubscribedOnly
@RequestMapping(path = "api/v1")
public class ScreenerController {

    private final TickerService tickerService;
    private final BitgetOpenInterestService oiService;
    private final BinanceService binanceService;
    private final SettingsService settingsService;

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

    @GetMapping("/orderbook/{mSymbol}")
    public void geOrderBook(
            HttpServletResponse response,
            @PathVariable String mSymbol
    ) throws Exception {
        response.setContentType("application/json");
        binanceService.depthSnapshot(response, mSymbol);
    }

    @GetMapping("/orderbook/top/{mSymbol}")
    public ResponseEntity<JsonNode> geOrderBookTop(
            @PathVariable String mSymbol
    ) {
        return ResponseEntity.ok(binanceService.topOrderBook(mSymbol));
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, String>> addSettings(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody SettingsRequestDTO settingsRequest
    ) {
        settingsService.saveSettings(appUser, settingsRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("success", "new settings for " + settingsRequest.getMSymbol() + " are saved."));
    }

    @DeleteMapping("/settings/{mSymbol}")
    public ResponseEntity<Map<String, String>> deleteSettings(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String mSymbol
    ) {
        boolean isDeleted = settingsService.deleteSettings(appUser, mSymbol);
        if (isDeleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getAllSettings(
            @AuthenticationPrincipal AppUser appUser
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(settingsService.getAllSettings(appUser));
    }

    @GetMapping("/settings/{mSymbol}")
    public ResponseEntity<UserSettingsResponse> getSettings(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String mSymbol
    ) {
        return settingsService.getSettings(appUser, mSymbol)
                .map(settings -> ResponseEntity.ok().body(settings))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

package dev.abu.screener_backend.controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.annotations.SubscribedOnly;
import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.binance.BinanceService;
import dev.abu.screener_backend.binance.BitgetOpenInterestService;
import dev.abu.screener_backend.binance.entities.Ticker;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.settings.SettingsRequestDTO;
import dev.abu.screener_backend.settings.SettingsService;
import dev.abu.screener_backend.settings.UserSettingsResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
public class TradingController {

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

    // **************** KLINE *********************

    @GetMapping("/kilnes")
    public ResponseEntity<String> getKlines(
            @RequestParam String mSymbol,
            @RequestParam String interval,
            @RequestParam String limit
    ) {
        try {
            String jsonResponse = binanceService.getKlinesData(mSymbol, interval, limit);

            if (jsonResponse == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_GATEWAY)
                        .body("{\"error\": \"Failed to proxy depth data\"}");
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Failed to proxy depth data: " + e.getMessage());
        }
    }

    @GetMapping("/kilnes/5m-volume/{mSymbol}")
    public ResponseEntity<String> get5MVolumeData(@PathVariable String mSymbol) {
        return getKlines(mSymbol, "5m", "1");
    }

    @GetMapping("/klines/gvolume")
    public ResponseEntity<String> getGVolume() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(binanceService.getGVolume());
    }

    // **************** ORDER BOOK ****************

    @GetMapping("/orderbook/{mSymbol}")
    public void geOrderBook(
            HttpServletResponse response,
            @PathVariable String mSymbol
    ) throws Exception {
        response.setContentType("application/json");
        binanceService.depthSnapshot(response, mSymbol);
    }

    // **************** SETTINGS ****************

    @PostMapping("/settings")
    public ResponseEntity<Map<String, String>> addSettings(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody SettingsRequestDTO settingsRequest
    ) {
        settingsService.saveSettings(appUser, settingsRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("success", "New settings are are saved."));
    }

    @PostMapping("/settings/reset")
    public ResponseEntity<Map<String, String>> resetSettings(
            @AuthenticationPrincipal AppUser appUser
    ) {
        settingsService.resetSettings(appUser);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("success", "Settings are reset."));
    }

    @PostMapping("/settings/reset/{mSymbol}")
    public ResponseEntity<Map<String, String>> resetSettings(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String mSymbol
    ) {
        settingsService.resetSettings(appUser, mSymbol);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("success", "Settings are reset for " + mSymbol));
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

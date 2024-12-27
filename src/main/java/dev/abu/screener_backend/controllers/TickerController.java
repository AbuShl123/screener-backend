package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.entity.Ticker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1/tickers")
public class TickerController {

    private final TickerService tickerService;

    @GetMapping()
    public List<Ticker> allTickers() {
        return tickerService.getAllTickers();
    }
}

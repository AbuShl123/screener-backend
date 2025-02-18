package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.binance.MaxOrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1/max-orders")
public class MaxOrdersController {

    private final MaxOrdersService maxOrdersService;

    @GetMapping
    public ResponseEntity<String> getMaxOrders() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(maxOrdersService.getMaxOrders());
    }
}

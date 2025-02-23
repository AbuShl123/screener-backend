package dev.abu.screener_backend.controllers;

import dev.abu.screener_backend.analysis.DensityAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping(path = "api/v1/densities")
public class DemoController {

    @GetMapping
    public ResponseEntity<String> getDensities() {
        StringBuilder response = new StringBuilder("{");
        
        for (DensityAnalyzer densityAnalyzer : DensityAnalyzer.getAllDensityAnalyzers()) {
            response.append(getData(densityAnalyzer)).append(",");
        }

        if (response.charAt(response.length() - 1) == ',') {
            response.deleteCharAt(response.length() - 1);
        }

        response.append("}");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.toString());
    }

    private String getData(DensityAnalyzer densityAnalyzer) {
        String pattern = """
                {
                "symbol": "%s",
                "sum": %f,
                "N": %d,
                "mean": %f,
                "level1": %f,
                "level2": %f,
                "level3": %f
                }
                """;

        return String.format(pattern, densityAnalyzer.getSymbol(),
                densityAnalyzer.getSum(),
                densityAnalyzer.getQty(),
                densityAnalyzer.getSum() / densityAnalyzer.getQty(),
                densityAnalyzer.getFirstLevel().get(),
                densityAnalyzer.getSecondLevel().get(),
                densityAnalyzer.getThirdLevel().get());
    }
}

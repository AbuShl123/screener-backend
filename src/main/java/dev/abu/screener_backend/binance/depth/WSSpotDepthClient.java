package dev.abu.screener_backend.binance.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.OBService;
import org.springframework.stereotype.Component;

import static dev.abu.screener_backend.utils.EnvParams.STREAM_SPOT_URL;

@Component
public class WSSpotDepthClient extends WSDepthClient {

    public WSSpotDepthClient(
            OBService obService,
            ObjectMapper objectMapper
    ) {
        super(STREAM_SPOT_URL, "spot", true, obService);
        super.wsDepthHandler = new WSDepthHandler(name, true, this, objectMapper, obService);
    }

}

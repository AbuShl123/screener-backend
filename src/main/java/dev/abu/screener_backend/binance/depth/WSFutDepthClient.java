package dev.abu.screener_backend.binance.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.OBService;
import org.springframework.stereotype.Component;

import static dev.abu.screener_backend.utils.EnvParams.STREAM_FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.STREAM_SPOT_URL;

@Component
public class WSFutDepthClient extends WSDepthClient {

    public WSFutDepthClient(
            OBService obService,
            ObjectMapper objectMapper
    ) {
        super(STREAM_FUT_URL, "futures", false, obService);
        super.wsDepthHandler = new WSDepthHandler(name, false, this, objectMapper, obService);
    }

}

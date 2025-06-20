package dev.abu.screener_backend.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Properties;

@Slf4j
public class EnvParams {

    public static final String SPOT_URL;
    public static final String FUT_URL;
    public static final String STREAM_SPOT_URL;
    public static final String STREAM_FUT_URL;
    public static final int CHUNK_SIZE;
    public static final int CUP_SIZE;
    public static final int MAX_PERCENT_DISTANCE_FROM_MARKET;
    public static final String FUT_SIGN;

    private EnvParams() {}

    static {
        Properties properties = new Properties();
        String sep = FileSystems.getDefault().getSeparator();
        String configPropsPath = System.getProperty("user.dir") + sep + "config.properties";

        try(FileInputStream in = new FileInputStream(configPropsPath)) {
            properties.load(in);
        } catch (IOException e) {
            log.error("FATAL: failed to read config.properties file.");
            throw new RuntimeException(e);
        }

        SPOT_URL = properties.getProperty("binance.spot-url");
        FUT_URL = properties.getProperty("binance.fut-url");
        STREAM_SPOT_URL = properties.getProperty("binance.stream.spot-url");
        STREAM_FUT_URL = properties.getProperty("binance.stream.fut-url");

        String chunkSize = properties.getProperty("chunk-size");
        String cupSize = properties.getProperty("cup-size");
        String maxIncline = properties.getProperty("max-incline");

        CHUNK_SIZE = Integer.parseInt(chunkSize);
        CUP_SIZE = Integer.parseInt(cupSize);
        MAX_PERCENT_DISTANCE_FROM_MARKET = Integer.parseInt(maxIncline);
        FUT_SIGN = properties.getProperty("fut-sign");
    }
}

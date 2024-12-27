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
    public static final int CUP_SIZE;
    public static final int MAX_INCLINE;
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

        String spotUrl = properties.getProperty("binance.spot-url");
        String futUrl = properties.getProperty("binance.fut-url");
        String streamSpotUrl = properties.getProperty("binance.stream.spot-url");
        String streamFutUrl = properties.getProperty("binance.stream.fut-url");

        Boolean isUSLocation = Boolean.getBoolean(properties.getProperty("location-us"));
        if (isUSLocation) {
            spotUrl = spotUrl.replace("binance.com", "binance.us");
            futUrl = futUrl.replace("binance.com", "binance.us");
            streamSpotUrl = streamSpotUrl.replace("binance.com", "binance.us");
            streamFutUrl = streamFutUrl.replace("binance.com", "binance.us");
        }

        SPOT_URL = spotUrl;
        FUT_URL = futUrl;
        STREAM_SPOT_URL = streamSpotUrl;
        STREAM_FUT_URL = streamFutUrl;

        String cupSize = properties.getProperty("cup-size");
        String maxIncline = properties.getProperty("max-incline");

        CUP_SIZE = Integer.parseInt(cupSize);
        MAX_INCLINE = Integer.parseInt(maxIncline);
        FUT_SIGN = properties.getProperty("fut-sign");
    }
}

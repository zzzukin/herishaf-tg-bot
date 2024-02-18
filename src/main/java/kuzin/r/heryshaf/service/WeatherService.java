package kuzin.r.heryshaf.service;

import org.json.JSONObject;

public interface WeatherService {
    JSONObject getWeather(Double longitude, Double latitude);
    String getResource();
}

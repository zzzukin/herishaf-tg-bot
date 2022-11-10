package kuzin.r.heryshaf.service;

import org.json.JSONObject;

public interface WeatherService {
    JSONObject getWeather();
    String getResource();
}

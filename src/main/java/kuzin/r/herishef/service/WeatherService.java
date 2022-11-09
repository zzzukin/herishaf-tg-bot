package kuzin.r.herishef.service;

import org.json.JSONObject;

public interface WeatherService {
    JSONObject getWeather();
    String getResource();
}

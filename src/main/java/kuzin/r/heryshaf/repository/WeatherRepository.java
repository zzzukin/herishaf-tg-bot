package kuzin.r.heryshaf.repository;


import kuzin.r.heryshaf.model.WeatherData;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;

public interface WeatherRepository extends CrudRepository<WeatherData, Long> {

    @Transactional
    WeatherData findTopByOrderByTimestampDesc();

    @Transactional
    WeatherData findTopByOrderByTimestampAsc();

    @Transactional
    Long deleteByTimestamp(long timestamp);
}

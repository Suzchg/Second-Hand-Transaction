package com.secondhand.logistics.provider;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mock 物流查询实现。
 * 返回模拟的四段式物流轨迹。
 */
@Component
public class MockLogisticsProvider implements LogisticsProvider {

    @Override
    public TrackResult track(String carrierCode, String trackingNo) {
        LocalDateTime now = LocalDateTime.now();
        return new TrackResult(carrierCode, trackingNo, "DELIVERED", List.of(
                new TrackPoint(now.minusDays(3).toString(), "快件已揽收"),
                new TrackPoint(now.minusDays(2).toString(), "快件在途中"),
                new TrackPoint(now.minusDays(1).toString(), "快件正在派送"),
                new TrackPoint(now.toString(), "快件已签收")
        ));
    }
}

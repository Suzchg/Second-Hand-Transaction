package com.secondhand.logistics.provider;

import java.util.List;

/**
 * 物流查询抽象接口。
 */
public interface LogisticsProvider {

    TrackResult track(String carrierCode, String trackingNo);

    record TrackResult(String carrierCode, String trackingNo, String status, List<TrackPoint> points) {}
    record TrackPoint(String time, String desc) {}
}

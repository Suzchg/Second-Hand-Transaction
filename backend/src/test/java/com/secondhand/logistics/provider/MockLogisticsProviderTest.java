package com.secondhand.logistics.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 物流服务商单元测试(用例4 物流查询)。
 */
@DisplayName("MockLogisticsProvider 单元测试")
class MockLogisticsProviderTest {

    @Test
    @DisplayName("UNIT-TC04-08 返回四段式物流轨迹:揽收→在途→派送→签收")
    void shouldReturnFourStepTrack() {
        MockLogisticsProvider provider = new MockLogisticsProvider();

        LogisticsProvider.TrackResult result = provider.track("SF", "SF123456");

        assertEquals("SF", result.carrierCode());
        assertEquals("SF123456", result.trackingNo());
        assertEquals("DELIVERED", result.status());
        assertEquals(4, result.points().size());
        assertTrue(result.points().get(0).desc().contains("揽收"));
        assertTrue(result.points().get(1).desc().contains("途中"));
        assertTrue(result.points().get(2).desc().contains("派送"));
        assertTrue(result.points().get(3).desc().contains("签收"));
    }
}

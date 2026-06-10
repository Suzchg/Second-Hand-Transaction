package com.secondhand.logistics.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 快递100 实时物流查询实现。
 *
 * 文档: https://api.kuaidi100.com
 * 接口: POST https://poll.kuaidi100.com/poll/query.do
 * 参数: customer + sign(MD5) + param(JSON)
 */
@Component
@ConditionalOnProperty(name = "logistics.kuaidi100.enabled", havingValue = "true")
public class Kuaidi100LogisticsProvider implements LogisticsProvider {

    private static final Logger log = LoggerFactory.getLogger(Kuaidi100LogisticsProvider.class);
    private static final String API_URL = "https://poll.kuaidi100.com/poll/query.do";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String customer;
    private final String key;

    public Kuaidi100LogisticsProvider(
            @Value("${logistics.kuaidi100.customer:}") String customer,
            @Value("${logistics.kuaidi100.key:}") String key) {
        this.customer = customer;
        this.key = key;
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public TrackResult track(String carrierCode, String trackingNo) {
        if (customer.isBlank() || key.isBlank()) {
            log.warn("快递100未配置密钥(customer/key)，无法查询真实物流");
            return new TrackResult(carrierCode, trackingNo, "NOT_CONFIGURED",
                    List.of(new TrackPoint("", "快递100未配置，请设置 logistics.kuaidi100.customer 和 logistics.kuaidi100.key")));
        }

        try {
            String param = objectMapper.writeValueAsString(
                    new QueryParam(carrierCode, trackingNo));
            String sign = md5(key + param + customer);

            String body = restClient.post()
                    .uri(API_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("customer=" + customer + "&sign=" + sign + "&param=" + param)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String state = root.path("state").asText();
            String message = root.path("message").asText();

            List<TrackPoint> points = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    points.add(new TrackPoint(
                            node.path("time").asText(),
                            node.path("context").asText()));
                }
            }

            // 映射快递100状态码到可读状态
            String statusLabel = switch (state) {
                case "0" -> "IN_TRANSIT";
                case "1" -> "DELIVERED";
                case "3" -> "DELIVERED";
                case "2", "4" -> "EXCEPTION";
                default -> "UNKNOWN";
            };

            return new TrackResult(carrierCode, trackingNo, statusLabel, points);

        } catch (Exception e) {
            log.error("快递100查询失败 carrier={} no={}: {}", carrierCode, trackingNo, e.getMessage());
            // 返回错误结果而不是 null，让前端能展示错误信息
            return new TrackResult(carrierCode, trackingNo, "QUERY_FAILED",
                    List.of(new TrackPoint("", "物流查询失败: " + e.getMessage())));
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 快递100请求参数 */
    private record QueryParam(String com, String num) {}
}

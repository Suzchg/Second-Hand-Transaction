package com.secondhand.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.ApiResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 中国省市区三级联动数据（静态 JSON）。
 */
@RestController
@RequestMapping("/api")
public class RegionController {

    private final ObjectMapper objectMapper;
    private JsonNode cachedRegions;

    public RegionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadRegions() {
        try {
            ClassPathResource resource = new ClassPathResource("regions.json");
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            cachedRegions = objectMapper.readTree(json);
        } catch (Exception e) {
            cachedRegions = objectMapper.createArrayNode();
        }
    }

    @GetMapping("/regions")
    public ApiResponse<JsonNode> regions() {
        return ApiResponse.ok(cachedRegions);
    }
}

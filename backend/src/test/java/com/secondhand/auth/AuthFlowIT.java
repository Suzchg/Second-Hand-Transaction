package com.secondhand.auth;

import com.secondhand.auth.entity.IdentityType;
import com.secondhand.auth.repository.UserIdentityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT {

    private static final String PHONE = "13900139000";
    private static final String PASSWORD = "integration-pass-123";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("secondhand_it")
            .withUsername("secondhand")
            .withPassword("secondhand-it-password");

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        // 集成测试无需演示数据，加快容器内启动速度
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserIdentityRepository identityRepository;

    @Test
    void shouldRegisterPersistAndLoginAgainstRealMySql() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"identityType":"PHONE","identifier":"%s","password":"%s"}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));

        boolean persisted = identityRepository
                .existsByIdentityTypeAndIdentifier(IdentityType.PHONE, PHONE);
        if (!persisted) {
            throw new AssertionError("注册身份未持久化到 MySQL");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identityType":"PHONE","identifier":"%s","password":"%s"}
                                """.formatted(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));
    }
}

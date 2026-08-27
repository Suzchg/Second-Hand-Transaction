package com.secondhand.it.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers MySQL 8.0 单例容器。
 *
 * 通过 {@code mvn verify -Dit.db=mysql} 启用：整个测试 JVM 共享一个 MySQL 容器，
 * 所有集成测试类复用同一容器与同一 Spring 上下文，避免重复启动开销。
 *
 * Docker 不可用时由 {@link AbstractIntegrationTest} 自动降级 H2 并打印告警。
 */
public final class SharedMySqlContainer {

    private static final Logger log = LoggerFactory.getLogger(SharedMySqlContainer.class);

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("secondhand_it")
            .withUsername("secondhand")
            .withPassword("secondhand-it-password");

    private SharedMySqlContainer() {
    }

    /** 判断当前环境 Docker 是否可用（用于无 Docker 环境自动降级 H2） */
    public static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 幂等启动容器并把数据源属性注册到 Spring 环境（覆盖 application-it.yml 的 H2 配置） */
    public static synchronized void register(DynamicPropertyRegistry registry) {
        if (!MYSQL.isRunning()) {
            log.info("[IT] 启动 Testcontainers MySQL 8.0 容器 ...");
            MYSQL.start();
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        log.info("[IT] 已切换数据源到 Testcontainers MySQL: {}", MYSQL.getJdbcUrl());
    }
}

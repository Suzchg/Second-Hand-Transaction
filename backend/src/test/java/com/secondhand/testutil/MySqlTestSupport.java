package com.secondhand.testutil;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/** 每个测试 JVM 共用一个临时 MySQL 容器，始终与开发数据库隔离。 */
public abstract class MySqlTestSupport {
    private static final class Database {
        // 首次使用时启动，避免每个测试类重复创建数据库。
        static final MySQLContainer<?> MYSQL = start();

        private static MySQLContainer<?> start() {
            MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("secondhand_it")
                    .withUsername("secondhand")
                    .withPassword("secondhand-it-password");
            mysql.start();
            // JVM 退出后由 Testcontainers/Ryuk 清理，不能在复用 Spring 上下文时提前停止。
            return mysql;
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // 用随机端口和测试账户覆盖数据源配置，不读取本机开发数据库。
        registry.add("spring.datasource.url", Database.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", Database.MYSQL::getUsername);
        registry.add("spring.datasource.password", Database.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", Database.MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
        // 关闭随机演示数据和真实物流服务，保证用例可重复执行。
        registry.add("app.seed.enabled", () -> "false");
        registry.add("logistics.kuaidi100.enabled", () -> "false");
    }
}

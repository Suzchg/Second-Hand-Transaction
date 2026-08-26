# 第一阶段：编译打包
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 加入 spring-boot:repackage 强制打出带有启动类的可运行 jar 包
RUN mvn clean package spring-boot:repackage -DskipTests
# 删除由于强制打包可能产生的冗余“瘦包”，防止下面 COPY 命令通配符报错
RUN rm -f target/*-plain.jar target/*.original

# 第二阶段：运行环境
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# 此时 target 目录下必定只有唯一正确的启动包
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8088
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl --fail --silent http://localhost:8088/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

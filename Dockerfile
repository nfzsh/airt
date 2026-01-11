# 使用官方 Maven 镜像作为构建阶段
FROM maven:3.8-openjdk-17-slim AS build

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 和下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 编译项目
RUN mvn clean package -DskipTests

# 使用轻量级 JRE 镜像作为运行阶段
FROM openjdk:17-slim

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 创建应用目录
WORKDIR /app

# 复制编译好的 JAR 文件
COPY --from=build /app/target/airt-1.0.0.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 设置环境变量
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE=docker

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/airt/api/roundtable/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

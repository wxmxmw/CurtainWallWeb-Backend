FROM docker.m.daocloud.io/maven:3.8.5-openjdk-17-slim AS builder
WORKDIR /app
COPY pom.xml .
# 预先下载依赖（可选，但能加速后续构建）
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# 使用包含 Python 的基础镜像
FROM docker.m.daocloud.io/python:3.9-slim

# 设置工作目录
WORKDIR /app

# 安装 Java 17 和必要的系统库
RUN apt-get update && apt-get install -y --fix-missing --no-install-recommends \
    default-jre \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 设置 Java 环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# 配置 pip 镜像源加速下载
RUN pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple

# 安装 Python 依赖
# 移除之前的清华源配置，使用阿里云源
RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/
RUN pip install --upgrade pip

# 注意：先安装 torch，指定 pytorch 的 index-url
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu

# 然后安装其他包
RUN pip install --no-cache-dir ultralytics opencv-python-headless

# 从 builder 阶段复制编译好的 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 复制 Python 推理脚本
COPY scripts/ ./scripts/

# 如果有 runs 目录存放了模型，也需要复制进去
COPY runs/ ./runs/

EXPOSE 8000

# 启动 Spring Boot 应用
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

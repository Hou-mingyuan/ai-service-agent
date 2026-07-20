# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
ARG MAVEN_MIRROR_URL=""
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">' \
        '  <mirrors><mirror><id>build-mirror</id><name>Build mirror</name>' \
        "  <url>${MAVEN_MIRROR_URL}</url><mirrorOf>central</mirrorOf></mirror></mirrors>" \
        '</settings>' > /root/.m2/settings.xml; \
    fi
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /app/target/ai-service-agent.jar app.jar
EXPOSE 19040
ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

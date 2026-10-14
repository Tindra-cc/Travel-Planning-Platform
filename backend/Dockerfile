# 多阶段构建：服务器上只需 git clone + docker compose up，无需预装 JDK/Maven，也无需本地先打包。
# （旧版 COPY target/*.jar 在干净克隆上必然失败——target/ 已被 .gitignore 忽略。）

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷 pom 拉依赖：pom 不变时该层命中缓存，改代码不必重下依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/travel-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=prod"]

# syntax=docker/dockerfile:1
# 멀티스테이지: ① Gradle 로 bootJar 빌드 → ② JRE 런타임 이미지.
# build context 는 backend/ 디렉터리 기준.

# ───── build ─────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 의존성 캐시 최적화: 빌드 스크립트 먼저 복사.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# 테스트는 CI 에서 수행. 이미지 빌드는 산출물만 생성.
RUN ./gradlew bootJar -x test --no-daemon

# ───── runtime ─────
FROM eclipse-temurin:25-jre
WORKDIR /app

# 업로드 파일 기본 저장 경로(.uploads/*)는 볼륨으로 마운트 권장.
RUN useradd -r -u 1001 appuser && mkdir -p /app/.uploads && chown -R appuser /app
USER appuser

# bootJar 산출물만 복사 (plain jar 는 -plain.jar 라 매칭되지 않음).
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

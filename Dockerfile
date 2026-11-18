# 앱 이미지: 화면(Vue)과 서버(Spring Boot)를 한 이미지로 만든다 (ADR-013, ADR-037).
# 빌드 위치가 Oracle VM(arm64)이든 로컬이든 같은 파일로 만든다.
# 비밀 값은 이미지에 넣지 않는다. 실행할 때 환경변수(쿠버네티스 Secret)로 받는다 (NFR-06).

# 1단계: 화면 빌드 (실제 API 모드)
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
# lock 파일의 선택 패키지가 OS마다 달라 npm ci 대신 npm install을 쓴다 (frontend-ci와 같다).
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2단계: 서버 빌드, 화면 결과물을 static으로 함께 넣는다
FROM eclipse-temurin:21-jdk AS server
WORKDIR /app
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
RUN chmod +x gradlew
# 의존성을 먼저 받아 두면 소스만 바뀔 때 다시 받지 않는다.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
ENV GRADLE_OPTS="-Xmx1g"
RUN ./gradlew --no-daemon bootJar -x test \
 && cp build/libs/*.jar app.jar \
 && rm -f build/libs/*-plain.jar

# 3단계: 실행
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"
RUN groupadd --system app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=server /app/app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

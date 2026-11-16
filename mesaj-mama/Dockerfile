FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl mesaj-mama -am -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN groupadd --system --gid 10001 mesaj \
    && useradd --system --uid 10001 --gid mesaj --no-create-home mesaj
COPY --from=build --chown=mesaj:mesaj /workspace/mesaj-common/target/mesaj-common-1.0-SNAPSHOT.jar /app/mesaj-common.jar
COPY --from=build --chown=mesaj:mesaj /workspace/mesaj-mama/target/mesaj-mama-1.0-SNAPSHOT.jar /app/mesaj-mama.jar

USER mesaj
EXPOSE 9090
ENTRYPOINT ["java", "-cp", "/app/mesaj-mama.jar:/app/mesaj-common.jar", "com.jarec.mesaj.mama.MamaApplication"]

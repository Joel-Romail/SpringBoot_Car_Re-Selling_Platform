FROM jelastic/maven:3.9.5-openjdk-21 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
COPY --from=build target/perekupi-0.0.1-SNAPSHOT.jar perekupi.jar
EXPOSE 9090
ENTRYPOINT [ "java", "-jar", "perekupi.jar" ]
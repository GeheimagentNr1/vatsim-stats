FROM eclipse-temurin:21

COPY app/target/vatsim-stats-app.jar /vatsim-stats.jar

# application.yml in jar can be overridden with config folder inside the container
ENTRYPOINT ["java", "-jar", "vatsim-stats.jar"]

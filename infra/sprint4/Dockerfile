FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --chown=10001:10001 *.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x ./gradlew && ./gradlew :backend:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ /app/
ENV PORT=10000
ENV UPLOAD_DIR=/tmp/uploads
EXPOSE 10000
CMD ["/app/bin/backend"]

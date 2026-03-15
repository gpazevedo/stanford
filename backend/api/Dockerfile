# Build context must be the multi-module backend root directory.
# Example: docker build -f api/Dockerfile .. (from backend/)
FROM public.ecr.aws/amazoncorretto/amazoncorretto:25-al2023 AS build
WORKDIR /build
COPY . .
RUN ./gradlew :api:bootJar --no-daemon

FROM public.ecr.aws/amazoncorretto/amazoncorretto:25-al2023
COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:0.9.1 \
     /lambda-adapter /opt/extensions/lambda-adapter
WORKDIR /app
COPY --from=build /build/api/build/libs/api-*.jar app.jar
ENV PORT=8080
# Shell form required so ${PORT} is expanded by the shell at runtime
ENTRYPOINT java -jar app.jar --server.port=${PORT}

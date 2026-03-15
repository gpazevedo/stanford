FROM public.ecr.aws/lambda/java:25 AS build
WORKDIR /build
COPY . .
RUN ./gradlew :ingestion:shadowJar --no-daemon

FROM public.ecr.aws/lambda/java:25
COPY --from=build /build/ingestion/build/libs/ingestion-*.jar \
    ${LAMBDA_TASK_ROOT}/lib/
CMD ["edu.stanford.courses.ingestion.IngestionHandler::handleRequest"]

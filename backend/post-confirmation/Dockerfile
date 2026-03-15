FROM public.ecr.aws/lambda/java:25 AS build
WORKDIR /build
COPY . .
RUN ./gradlew :post-confirmation:shadowJar --no-daemon

FROM public.ecr.aws/lambda/java:25
COPY --from=build /build/post-confirmation/build/libs/post-confirmation-*.jar \
    ${LAMBDA_TASK_ROOT}/lib/
CMD ["edu.stanford.courses.postconfirmation.PostConfirmationHandler::handleRequest"]

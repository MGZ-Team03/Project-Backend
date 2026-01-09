FROM public.ecr.aws/lambda/java:21

COPY build/libs/*-all.jar ${LAMBDA_TASK_ROOT}/lib/app.jar


ENTRYPOINT ["top", "-b"]
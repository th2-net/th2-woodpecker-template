FROM gradle:7.6-jdk11 AS build
ARG release_version
COPY ./ .
RUN gradle --no-daemon clean build dockerPrepare -Prelease_version=${release_version}

#FROM adoptopenjdk/openjdk11:alpine

# FIXME: remove when release
FROM adoptopenjdk/openjdk11:x86_64-ubuntu-jdk-11.0.11_9
RUN cat /etc/os-release
RUN apt-get update
RUN apt-get install -y ifstat

WORKDIR /home
COPY --from=build /home/gradle/build/docker .
ENTRYPOINT ["/home/service/bin/service"]
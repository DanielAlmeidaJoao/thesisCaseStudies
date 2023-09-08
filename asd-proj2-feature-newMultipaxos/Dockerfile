FROM eclipse-temurin:17-jre-alpine

WORKDIR code
#ADD docker/ docker/
ADD log4j2.xml .
ADD config.properties .
ADD target/asdProj2.jar .

ENTRYPOINT [ "java" ]

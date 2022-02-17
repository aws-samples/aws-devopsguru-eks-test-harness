FROM amazoncorretto:11

ARG JAR_FILE=build/libs/devopsguru_eks_test-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/application.jar"]
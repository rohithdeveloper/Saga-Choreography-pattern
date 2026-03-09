FROM openjdk:17.0.2
VOLUME /tmp
EXPOSE 8083
ADD target/payment-service-0.0.1-SNAPSHOT.jar payment-service-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/payment-service-0.0.1-SNAPSHOT.jar"]

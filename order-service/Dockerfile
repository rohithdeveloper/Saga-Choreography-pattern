FROM openjdk:17.0.2
VOLUME /tmp
EXPOSE 8081
ADD target/order-service-0.0.1-SNAPSHOT.jar order-service-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/order-service-0.0.1-SNAPSHOT.jar"]
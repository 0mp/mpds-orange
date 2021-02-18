# Getting Started

## Prerequisites
* Install OpenJDK 11 (OpenJDK, AdoptJDK) on your system for development, e.g.: https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot
* Install Git on your system for development (e.g. Sourcetree as Git client)
* Install [Docker] and register on their site to pull available Docker images (e.g. https://www.docker.com/products/docker-desktop) or install the required servers locally
* Install an IDE for Java on your system for development (e.g. IntelliJ)
* Open the project with your chosen IDE (e.g. IntelliJ)
* Setup your IDE to use AdoptOpenJDK11 (see above)

## Setting up the development environment
* Enable Annotation Processing on your IDE (e.g. on IntelliJ: Preferences -> Annotation Processors -> check the box Enable Annotation Processing)
* Make sure, that the properties on flink-autoscaler/src/main/resources/application.yml match to the configurations (hostname, ports, credentials etc.) on the docker-compose.yml file or to the server that you have installed locally
```
# Database Config
r2dbc:
  postgres:
    database: postgres
    hostname: localhost
    password: postgres
    port: 5432
    username: postgres
```
* Open the cli and navigate to the project root path and execute the following command to start the required Docker Containers
* Run Apache Kafka (e.g. through a Docker container https://hub.docker.com/r/landoop/kafka-lenses-dev landoop/kafka-lenses-dev)
* Make sure that the application is pointing to Kafka on the resources/application.yml file (e.g. localhost:9092)
``` 
docker-compose up -d
```
* pgAdmin (username: pgadmin, pw: pgadmin) will run on localhost port 80 by default that connects to the running PostgreSQL database container
* Via add server -> name: postgres -> in connection: hostname: postgres, username: postgres, pw: postgres (or like it is defined in the docker-compose.yml file)
* In order to stop the running Docker containers, use the Docker command from your project root path:
```
docker-compose stop
```
* In order to stop and remove the running Docker containers, use the Docker command from your project root path:
```
docker-compose down
```

## Running and testing the application locally
* Start the application through the IDE (play button) or through the gradle task ./gradlew bootRun using the cli
* By default, the application will run under port 8080
* When the application starts, it will execute the SQL commands defined on the file com/mpds/flinkautoscaler/src/ressources/schema.sql

## Building and Deployment
* Make sure that the version and target image repository properties are set properly on the flink-autoscaler/build.gradle file
* Make sure that docker credentials are configured properly, use the following command if the image should be pushed to Google Container Registry (GCR):
```
gcloud auth configure-docker
```
* Run the following command on the home directory of the simulator project to build and push the Docker image on Mac/Linux:
```
./gradlew clean jib
```
* Run the following command on the home directory of the simulator project to build and push the Docker image on Windows:
```
gradlew.bat clean jib
```
* Get the credentials from the Google Kubernetes Engine:
```
gcloud container clusters get-credentials --project mpds-297011 --region europe-west3-c  mpds-cluster
```
* Use the Helm charts under flink-autoscaler/k8s/helm to do a deployment on the Kubernetes Cluster, e.g.:
```
helm install flink-autoscaler .
```
* Use the Helm charts under flink-autoscaler/k8s/helm to upgrade the deployment of the simulator on the Kubernetes Cluster, e.g.:
```
helm install upgrade -f override.yaml flink-autoscaler .
```
* Use the Helm charts under flink-autoscaler/k8s/helm to uninstall the deployment of the simulator on the Kubernetes Cluster, e.g.:
```
helm uninstall flink-autoscaler
```

### Links
* Using Helm and Kubernetes: https://www.baeldung.com/kubernetes-helm
* Jib - Containerize your Gradle Java project: https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#quickstart
* Dockerizing Java Apps using Jib: https://www.baeldung.com/jib-dockerizing
* Reactive Programming with Project Reactor: https://projectreactor.io/
* Spring Boot Dependency Injection: https://www.baeldung.com/spring-dependency-injection
* Introduction to Project Lombok: https://www.baeldung.com/intro-to-project-lombok
* Kafka for development: https://lenses.io/box/
* Reactor Kafka Reference Guide: https://projectreactor.io/docs/kafka/release/reference/
* Kafka Performance Tuning — Ways for Kafka Optimization: https://medium.com/@rinu.gour123/kafka-performance-tuning-ways-for-kafka-optimization-fdee5b19505b
* Spring Cloud Stream: https://cloud.spring.io/spring-cloud-stream/spring-cloud-stream.html
* Spring Cloud Stream Programming Model: https://docs.spring.io/spring-cloud-stream/docs/current-snapshot/reference/html/_programming_model.html
* Spring 5 WebClient: https://www.baeldung.com/spring-5-webclient
    

# IoT Simulator

## Quick start

Configure the JARs (this also produces an "./args" file for the Flink job):

```
make configure
```

Build the JARs:

```
make build
```

Run the producer (it may be unnecessary to set JAVA_HOME if OpenJDK 11 or newer is the default in your environment):

```
env JAVA_HOME=/usr/local/openjdk11 make producer-start
```

Run the processor with a script:

```
cd ../infrastructure && make flink-run-job-iot
```

Run the processor manually, through the web UI:

- JAR: `./iot_vehicles_experiment/processor/target/processor-1.0-SNAPSHOT.jar`
- Arguments: see the `./args` file

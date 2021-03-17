package de.tu_berlin.dos.arm.iot_vehicles_experiment.producer;

import akka.actor.ActorRef;
import de.tu_berlin.dos.arm.iot_vehicles_experiment.common.data.TimeSeries;
import de.tu_berlin.dos.arm.iot_vehicles_experiment.common.events.Point;
import de.tu_berlin.dos.arm.iot_vehicles_experiment.common.events.TrafficEvent;
import de.tu_berlin.dos.arm.iot_vehicles_experiment.producer.Vehicles.VehicleActor;
import de.tu_berlin.dos.arm.iot_vehicles_experiment.producer.Vehicles.VehicleActor.Emit;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public enum Generator { GET;

    private static final Logger LOG = Logger.getLogger(Generator.class);

    private final StopWatch stopWatch = new StopWatch();

    public void generate(
            String graphFileName, int updateInterval, TimeSeries ts,
            String topic, KafkaProducer<String, TrafficEvent> kafkaProducer) throws Exception {

        // import and generate street graph from file
        RoutesGraph.GET.importFromResource(graphFileName);
        // start stopwatch
        Generator.GET.stopWatch.start();
        // loop through dataset based on synchronization with stopwatch
        int current = (int) Generator.GET.stopWatch.getTime(TimeUnit.SECONDS);
        System.out.println(ts.observations.size());

        for (int i = 0; i < ts.observations.size(); i++) {

            // test if new vehicles need to be created
            int vehiclesNeeded = ts.observations.get(i).value - VehicleActor.VEHICLE_COUNT.get();
            if (vehiclesNeeded > 0) {
                // create the desired number of vehicles
                IntStream.range(0, vehiclesNeeded).forEach(j -> {

                    List<Point> waypoints = RoutesGraph.GET.getRandomRoute();
                    ActorRef vehicleActor = Vehicles.SYSTEM.actorOf(VehicleActor.props(updateInterval, waypoints));
                    vehicleActor.tell(new Emit(topic, kafkaProducer), ActorRef.noSender());
                });
            }
            // wait until next second
            while (current < i) {

                current = (int) Generator.GET.stopWatch.getTime(TimeUnit.SECONDS);
            }
            LOG.info("Vehicle Limit: " + VehicleActor.VEHICLE_COUNT.get());
        }
        // terminate the actor system
        Vehicles.SYSTEM.getWhenTerminated().toCompletableFuture().get(3, TimeUnit.SECONDS);
    }
}

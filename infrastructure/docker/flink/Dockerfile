FROM flink:scala_2.12-java8
RUN mkdir -p $FLINK_HOME/usrlib
COPY flink-shaded-hadoop-2-uber-2.8.3-10.0.jar $FLINK_HOME/lib/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
COPY covid-engine-2.3.0.jar $FLINK_HOME/usrlib/covid-engine-2.3.0.jar

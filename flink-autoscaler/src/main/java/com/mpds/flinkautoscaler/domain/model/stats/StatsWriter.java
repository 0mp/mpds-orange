package com.mpds.flinkautoscaler.domain.model.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class StatsWriter {

    private String writeToPath;
    private PrintWriter printWriter;
    File out;

    public StatsWriter(){
        writeToPath = "./captured-stats/" + LocalDateTime.now() + ".csv";
        out = new File(writeToPath);
        try {
            out.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            printWriter = new PrintWriter(writeToPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        makeHeader();
    }

    public void makeHeader(){
        printWriter.println("Timestamp,Kafka Lag,Kafka Message Rate,Flink Records In,CPU,Mem,Long-Term Prediction,Short-Term Prediction,Aggregate Prediction,Parallelism,Lag Latency");
    }

    public void addRecord(StatsRecord stats){
        String print = stats.toCSVString();
        printWriter.println(print);
        printWriter.flush();
    }
}

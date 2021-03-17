package de.tu_berlin.dos.arm.iot_vehicles_experiment.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.*;

public class TrafficEvent {

    private String lp;
    private Point pt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone="UTC")
    private Date ts;

    public TrafficEvent() { }

    public TrafficEvent(String lp, Point pt, Date ts) {
        this.lp = lp;
        this.pt = pt;
        this.ts = ts;
    }

    public void setLp(String lp) {
        this.lp = lp;
    }

    public void setPt(Point pt) {
        this.pt = pt;
    }

    public void setTs(Date ts) {
        this.ts = ts;
    }

    public String getLp() {
        return lp;
    }

    public Point getPt() {
        return pt;
    }

    public Date getTs() {
        return ts;
    }

    @Override
    public String toString() {
        return "{lp:" + lp + ",pt:" + pt + ",ts:" + ts + '}';
    }
}

package com.mpds.flinkautoscaler.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    public static String getCurrentUTCDateTime(){
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        return currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }
}

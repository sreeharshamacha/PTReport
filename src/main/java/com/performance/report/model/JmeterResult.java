package com.performance.report.model;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class JmeterResult {

    @CsvBindByName(column = "timeStamp")
    private long timeStamp;

    @CsvBindByName(column = "elapsed")
    private long elapsed;

    @CsvBindByName(column = "label")
    private String label;

    @CsvBindByName(column = "responseCode")
    private String responseCode;

    @CsvBindByName(column = "responseMessage")
    private String responseMessage;

    @CsvBindByName(column = "threadName")
    private String threadName;

    @CsvBindByName(column = "dataType")
    private String dataType;

    @CsvBindByName(column = "success")
    private boolean success;

    @CsvBindByName(column = "failureMessage")
    private String failureMessage;

    @CsvBindByName(column = "bytes")
    private long bytes;

    @CsvBindByName(column = "sentBytes")
    private long sentBytes;

    @CsvBindByName(column = "grpThreads")
    private int grpThreads;

    @CsvBindByName(column = "allThreads")
    private int allThreads;

    @CsvBindByName(column = "URL")
    private String url;

    @CsvBindByName(column = "Latency")
    private long latency;

    @CsvBindByName(column = "IdleTime")
    private long idleTime;

    @CsvBindByName(column = "Connect")
    private long connect;
}

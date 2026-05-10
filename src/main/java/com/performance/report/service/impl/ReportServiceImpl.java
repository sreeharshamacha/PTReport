package com.performance.report.service.impl;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.performance.report.model.JmeterResult;
import com.performance.report.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Override
    public ByteArrayInputStream generateReport(MultipartFile file, String runId, String testType, String startDate, String endDate) throws Exception {
        log.info("Generating report for Run ID: {}, Test Type: {}", runId, testType);

        List<JmeterResult> results;
        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CsvToBean<JmeterResult> csvToBean = new CsvToBeanBuilder<JmeterResult>(reader)
                    .withType(JmeterResult.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();
            results = csvToBean.parse();
        }

        if (results.isEmpty()) {
            throw new IllegalArgumentException("The provided JTL file is empty or invalid.");
        }

        // Log first few timestamps to help debugging
        log.info("First timestamp in file: {}", results.get(0).getTimeStamp());

        // Global stats
        long actualMin = results.stream().mapToLong(JmeterResult::getTimeStamp).min().orElse(0);
        long actualMax = results.stream().mapToLong(JmeterResult::getTimeStamp).max().orElse(0);
        log.info("JTL Data Range: {} to {}", actualMin, actualMax);

        long minTime = actualMin;
        long maxTime = actualMax;
        
        if ("Load Test".equals(testType) && startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            try {
                LocalDateTime startDateTime = LocalDateTime.parse(startDate);
                LocalDateTime endDateTime = LocalDateTime.parse(endDate);
                
                if (startDateTime.isAfter(endDateTime)) {
                    throw new IllegalArgumentException("Start date cannot be after end date");
                }
                
                minTime = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                maxTime = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                
                log.info("Filtering with range: {} to {}", minTime, maxTime);
                
                final long filterStart = minTime;
                final long filterEnd = maxTime;
                
                List<JmeterResult> filteredResults = results.stream()
                        .filter(r -> r.getTimeStamp() >= filterStart && r.getTimeStamp() <= filterEnd)
                        .collect(Collectors.toList());
                        
                if (filteredResults.isEmpty()) {
                    throw new IllegalArgumentException("No data available for the selected date range");
                }
                results = filteredResults;
            } catch (Exception e) {
                log.error("Error during date filtering: {}", e.getMessage());
                throw e;
            }
        }

        long durationMs = maxTime - minTime;
        double durationSec = durationMs / 1000.0;
        double durationHrs = durationSec / 3600.0;
        
        long totalIterations = results.size();
        long totalPass = results.stream().filter(JmeterResult::isSuccess).count();
        long totalFail = totalIterations - totalPass;
        double totalPassPct = totalIterations > 0 ? ((double) totalPass / totalIterations) * 100 : 0;
        double totalTph = durationHrs > 0 ? (totalIterations / durationHrs) : 0;
        double totalTps = durationSec > 0 ? (totalIterations / durationSec) : 0;
        long pagesOver5s = results.stream().filter(r -> r.getElapsed() > 5000).count();

        Map<String, List<JmeterResult>> groupedByLabel = results.stream()
                .collect(Collectors.groupingBy(JmeterResult::getLabel));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            createExecutiveSummarySheet(workbook, minTime, maxTime, durationMs, totalTph, pagesOver5s, totalPassPct);
            createPassFailSheet(workbook, groupedByLabel, durationSec, durationHrs, totalIterations, totalPass, totalFail, totalPassPct, totalTph, totalTps);
            createResponseTimeSheet(workbook, "Total ResponseTime", groupedByLabel, results, false);
            createResponseTimeSheet(workbook, "Pass ResponseTime", groupedByLabel, results, true);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createExecutiveSummarySheet(Workbook workbook, long minTime, long maxTime, long durationMs, double tph, long pagesOver5s, double passPct) {
        Sheet sheet = workbook.createSheet("Executive Summary");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Test Parameters");
        headerRow.createCell(1).setCellValue("Observations");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String testTimings = sdf.format(new Date(minTime)) + " to " + sdf.format(new Date(maxTime));
        
        long s = (durationMs / 1000) % 60;
        long m = (durationMs / (1000 * 60)) % 60;
        long h = (durationMs / (1000 * 60 * 60)) % 24;
        String durationStr = String.format("%02d:%02d:%02d", h, m, s);

        String[][] data = {
            {"Test Timings", testTimings},
            {"Total Peak Duration", durationStr},
            {"Achieved TransactionsPer Hour", String.format("%.2f", tph)},
            {"Pages > 5.0 Seconds", String.valueOf(pagesOver5s)},
            {"Pass Percentage(%)", String.format("%.2f%%", passPct)}
        };

        for (int i = 0; i < data.length; i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(data[i][0]);
            row.createCell(1).setCellValue(data[i][1]);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createPassFailSheet(Workbook workbook, Map<String, List<JmeterResult>> grouped, double durationSec, double durationHrs, long totIter, long totPass, long totFail, double totPassPct, double totTph, double totTps) {
        Sheet sheet = workbook.createSheet("PassFail");
        Row header = sheet.createRow(0);
        String[] columns = {"Scenario", "Tot-Iteration", "Pass-Iteration", "Fail Count", "Pass%", "TPH", "TPS"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowIdx = 1;
        for (Map.Entry<String, List<JmeterResult>> entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<JmeterResult> stats = entry.getValue();
            long iter = stats.size();
            long pass = stats.stream().filter(JmeterResult::isSuccess).count();
            long fail = iter - pass;
            double passPct = iter > 0 ? ((double) pass / iter) * 100 : 0;
            double tph = durationHrs > 0 ? (iter / durationHrs) : 0;
            double tps = durationSec > 0 ? (iter / durationSec) : 0;

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(label);
            row.createCell(1).setCellValue(iter);
            row.createCell(2).setCellValue(pass);
            row.createCell(3).setCellValue(fail);
            row.createCell(4).setCellValue(Math.round(passPct * 100.0) / 100.0);
            row.createCell(5).setCellValue(Math.round(tph * 100.0) / 100.0);
            row.createCell(6).setCellValue(Math.round(tps * 100.0) / 100.0);
        }

        // Add Total row
        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(0).setCellValue("Total");
        totalRow.createCell(1).setCellValue(totIter);
        totalRow.createCell(2).setCellValue(totPass);
        totalRow.createCell(3).setCellValue(totFail);
        totalRow.createCell(4).setCellValue(Math.round(totPassPct * 100.0) / 100.0);
        totalRow.createCell(5).setCellValue(Math.round(totTph * 100.0) / 100.0);
        totalRow.createCell(6).setCellValue(Math.round(totTps * 100.0) / 100.0);

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createResponseTimeSheet(Workbook workbook, String sheetName, Map<String, List<JmeterResult>> grouped, List<JmeterResult> allResults, boolean onlyPass) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        String[] columns = {"Scenario", "Page", "Tot-Iteration", "Pass-Iteration", "Minimum", "Average", "Maximum", "50th %tile", "80th %tile", "90th %tile", "95th %tile", "97th %tile", "98th %tile", "99th %tile"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowIdx = 1;
        for (Map.Entry<String, List<JmeterResult>> entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<JmeterResult> rawStats = entry.getValue();
            long totIter = rawStats.size();
            long passIter = rawStats.stream().filter(JmeterResult::isSuccess).count();
            
            List<JmeterResult> stats = onlyPass ? rawStats.stream().filter(JmeterResult::isSuccess).collect(Collectors.toList()) : rawStats;
            
            writePercentileRow(sheet, rowIdx++, label, label, totIter, passIter, stats);
        }

        // Add Total row
        long totalIter = allResults.size();
        long totalPassIter = allResults.stream().filter(JmeterResult::isSuccess).count();
        List<JmeterResult> stats = onlyPass ? allResults.stream().filter(JmeterResult::isSuccess).collect(Collectors.toList()) : allResults;
        
        writePercentileRow(sheet, rowIdx, "Total", "Total", totalIter, totalPassIter, stats);

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writePercentileRow(Sheet sheet, int rowIdx, String scenario, String page, long totIter, long passIter, List<JmeterResult> stats) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(scenario);
        row.createCell(1).setCellValue(page);
        row.createCell(2).setCellValue(totIter);
        row.createCell(3).setCellValue(passIter);

        if (stats.isEmpty()) {
            for (int i = 4; i <= 13; i++) row.createCell(i).setCellValue(0);
            return;
        }

        long min = stats.stream().mapToLong(JmeterResult::getElapsed).min().orElse(0);
        long max = stats.stream().mapToLong(JmeterResult::getElapsed).max().orElse(0);
        double avg = stats.stream().mapToLong(JmeterResult::getElapsed).average().orElse(0);

        List<Long> sorted = stats.stream().map(JmeterResult::getElapsed).sorted().collect(Collectors.toList());

        row.createCell(4).setCellValue(min);
        row.createCell(5).setCellValue(Math.round(avg * 100.0) / 100.0);
        row.createCell(6).setCellValue(max);
        row.createCell(7).setCellValue(getPercentile(sorted, 50.0));
        row.createCell(8).setCellValue(getPercentile(sorted, 80.0));
        row.createCell(9).setCellValue(getPercentile(sorted, 90.0));
        row.createCell(10).setCellValue(getPercentile(sorted, 95.0));
        row.createCell(11).setCellValue(getPercentile(sorted, 97.0));
        row.createCell(12).setCellValue(getPercentile(sorted, 98.0));
        row.createCell(13).setCellValue(getPercentile(sorted, 99.0));
    }

    private long getPercentile(List<Long> sorted, double percentile) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}

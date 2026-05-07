package com.performance.report.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;

public interface ReportService {
    ByteArrayInputStream generateReport(MultipartFile file, String runId, String testType, String startDate, String endDate) throws Exception;
}

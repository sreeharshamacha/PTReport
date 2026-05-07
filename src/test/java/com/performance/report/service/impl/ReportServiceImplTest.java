package com.performance.report.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceImplTest {

    @Test
    void testGenerateReport() throws Exception {
        ReportServiceImpl service = new ReportServiceImpl();
        
        Path jtlPath = Paths.get("src/test/resources/sample.jtl");
        byte[] content = Files.readAllBytes(jtlPath);
        MultipartFile multipartFile = new MockMultipartFile("file", "sample.jtl", "text/csv", content);
        
        ByteArrayInputStream reportStream = service.generateReport(multipartFile, "RUN-123", "Load");
        
        assertNotNull(reportStream);
        assertTrue(reportStream.available() > 0);
    }
}

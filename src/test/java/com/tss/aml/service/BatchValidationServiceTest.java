package com.tss.aml.service;

import com.tss.aml.dtos.batch.BatchValidationResult;
import com.tss.aml.dtos.batch.ParsedTransactionRowDto;
import com.tss.aml.services.BatchValidationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BatchValidationServiceTest {

    private BatchValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new BatchValidationService();
    }

    @Test
    void testValidExcelBatch_passesValidation() throws IOException {
        byte[] excelContent = createExcelBytes(
                new String[]{"TxnNo", "OriginatorAccountNo", "OriginatorName", "Amount", "Currency", "TxnType", "Direction", "CounterpartyName", "CounterpartyAccountNo", "CounterpartyBank", "CounterpartyCountryCode", "TxnTimestamp", "CountryCode"},
                new Object[][]{
                        {"TXN-001", "ACC-101", "John Doe", "5000.00", "USD", "NEFT", "OUT", "Jane Smith", "ACC-202", "Chase", "US", "2026-01-01T12:00:00", "US"},
                        {"TXN-002", "ACC-102", "Alice Smith", "10000.00", "USD", "RTGS", "IN", "Bob Jones", "ACC-203", "Citi", "US", "2026-01-01T12:30:00", "US"}
                }
        );

        MockMultipartFile mockFile = new MockMultipartFile("file", "batch.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        BatchValidationResult<ParsedTransactionRowDto> result = validationService.validateExcelBatch(mockFile);

        assertTrue(result.isValid(), "Valid Excel file should pass validation");
        assertTrue(result.getErrors().isEmpty(), "No validation errors expected");
        assertEquals(2, result.getParsedData().size(), "2 transactions expected to be parsed");
        assertEquals("TXN-001", result.getParsedData().get(0).getTxnNo());
    }

    @Test
    void testMissingHeaderColumn_failsValidation() throws IOException {
        // Missing "TxnTimestamp" header
        byte[] excelContent = createExcelBytes(
                new String[]{"TxnNo", "OriginatorAccountNo", "OriginatorName", "Amount", "Currency", "TxnType", "Direction", "CounterpartyName", "CounterpartyAccountNo", "CounterpartyBank", "CounterpartyCountryCode", "CountryCode"},
                new Object[][]{
                        {"TXN-001", "ACC-101", "John Doe", "5000.00", "USD", "NEFT", "OUT", "Jane Smith", "ACC-202", "Chase", "US", "US"}
                }
        );

        MockMultipartFile mockFile = new MockMultipartFile("file", "bad_headers.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        BatchValidationResult<ParsedTransactionRowDto> result = validationService.validateExcelBatch(mockFile);

        assertFalse(result.isValid(), "Batch with missing header should fail validation");
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("TxnTimestamp")));
    }

    @Test
    void testBlankMandatoryField_failsValidationWithStructuredError() throws IOException {
        // Blank "Amount" on Row 2
        byte[] excelContent = createExcelBytes(
                new String[]{"TxnNo", "OriginatorAccountNo", "OriginatorName", "Amount", "Currency", "TxnType", "Direction", "CounterpartyName", "CounterpartyAccountNo", "CounterpartyBank", "CounterpartyCountryCode", "TxnTimestamp", "CountryCode"},
                new Object[][]{
                        {"TXN-001", "ACC-101", "John Doe", "", "USD", "NEFT", "OUT", "Jane Smith", "ACC-202", "Chase", "US", "2026-01-01T12:00:00", "US"}
                }
        );

        MockMultipartFile mockFile = new MockMultipartFile("file", "blank_amount.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        BatchValidationResult<ParsedTransactionRowDto> result = validationService.validateExcelBatch(mockFile);

        assertFalse(result.isValid(), "Batch with blank mandatory amount should fail validation");
        assertEquals(1, result.getErrors().size());
        assertEquals(2, result.getErrors().get(0).getRowNumber(), "Row number should be 2");
        assertEquals("Amount", result.getErrors().get(0).getFieldName());
    }

    @Test
    void testInvalidDateFormat_failsValidation() throws IOException {
        byte[] excelContent = createExcelBytes(
                new String[]{"TxnNo", "OriginatorAccountNo", "OriginatorName", "Amount", "Currency", "TxnType", "Direction", "CounterpartyName", "CounterpartyAccountNo", "CounterpartyBank", "CounterpartyCountryCode", "TxnTimestamp", "CountryCode"},
                new Object[][]{
                        {"TXN-001", "ACC-101", "John Doe", "5000.00", "USD", "NEFT", "OUT", "Jane Smith", "ACC-202", "Chase", "US", "INVALID_DATE_STRING", "US"}
                }
        );

        MockMultipartFile mockFile = new MockMultipartFile("file", "bad_date.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        BatchValidationResult<ParsedTransactionRowDto> result = validationService.validateExcelBatch(mockFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("TxnTimestamp")));
    }

    private byte[] createExcelBytes(String[] headers, Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transactions");
            Row headerRow = sheet.createRow(0);
            for (int h = 0; h < headers.length; h++) {
                Cell cell = headerRow.createCell(h);
                cell.setCellValue(headers[h]);
            }

            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(String.valueOf(rows[r][c]));
                }
            }

            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}

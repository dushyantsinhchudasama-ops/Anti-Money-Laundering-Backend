package com.tss.aml.services;

import com.tss.aml.dtos.batch.BatchValidationErrorDto;
import com.tss.aml.dtos.batch.BatchValidationResult;
import com.tss.aml.dtos.batch.ParsedTransactionRowDto;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.enums.TransactionType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.tss.aml.common.templete.FileConstant.REQUIRED_HEADERS;

@Service
public class BatchValidationService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter SPACE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BatchValidationResult<ParsedTransactionRowDto> validateExcelBatch(MultipartFile file) {
        List<BatchValidationErrorDto> errors = new ArrayList<>();
        List<ParsedTransactionRowDto> parsedRows = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add(BatchValidationErrorDto.builder()
                    .rowNumber(0)
                    .fieldName("file")
                    .errorMessage("Uploaded file is empty or missing.")
                    .build());
            return BatchValidationResult.<ParsedTransactionRowDto>builder()
                    .valid(false)
                    .errors(errors)
                    .parsedData(Collections.emptyList())
                    .build();
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                errors.add(BatchValidationErrorDto.builder()
                        .rowNumber(0)
                        .fieldName("sheet")
                        .errorMessage("Excel sheet contains no data.")
                        .build());
                return BatchValidationResult.<ParsedTransactionRowDto>builder()
                        .valid(false)
                        .errors(errors)
                        .parsedData(Collections.emptyList())
                        .build();
            }

            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerMap = validateHeaders(headerRow, errors);
            if (!errors.isEmpty()) {
                return BatchValidationResult.<ParsedTransactionRowDto>builder()
                        .valid(false)
                        .errors(errors)
                        .parsedData(Collections.emptyList())
                        .build();
            }

            int rowCount = sheet.getLastRowNum();
            for (int r = 1; r <= rowCount; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row)) {
                    continue;
                }
                validateAndParseRow(row, r + 1, headerMap, errors, parsedRows);
            }

        } catch (Exception e) {
            errors.add(BatchValidationErrorDto.builder()
                    .rowNumber(0)
                    .fieldName("file")
                    .errorMessage("Failed to parse Excel file: " + e.getMessage())
                    .build());
        }

        boolean isValid = errors.isEmpty();
        return BatchValidationResult.<ParsedTransactionRowDto>builder()
                .valid(isValid)
                .errors(errors)
                .parsedData(isValid ? parsedRows : Collections.emptyList())
                .build();
    }

    private Map<String, Integer> validateHeaders(Row headerRow, List<BatchValidationErrorDto> errors) {
        Map<String, Integer> headerMap = new HashMap<>();
        if (headerRow == null) {
            errors.add(BatchValidationErrorDto.builder()
                    .rowNumber(1)
                    .fieldName("headers")
                    .errorMessage("Header row is missing.")
                    .build());
            return headerMap;
        }

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String headerVal = cell.getStringCellValue().trim();
                headerMap.put(headerVal, c);
            }
        }

        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headerMap.containsKey(requiredHeader)) {
                errors.add(BatchValidationErrorDto.builder()
                        .rowNumber(1)
                        .fieldName(requiredHeader)
                        .errorMessage("Missing required header column: " + requiredHeader)
                        .build());
            }
        }

        return headerMap;
    }

    private void validateAndParseRow(Row row, int displayRowNum, Map<String, Integer> headerMap,
                                     List<BatchValidationErrorDto> errors, List<ParsedTransactionRowDto> parsedRows) {

        String txnNo = getCellValue(row, headerMap.get("TxnNo"));
        String origAcc = getCellValue(row, headerMap.get("OriginatorAccountNo"));
        String origName = getCellValue(row, headerMap.get("OriginatorName"));
        String amountStr = getCellValue(row, headerMap.get("Amount"));
        String currency = getCellValue(row, headerMap.get("Currency"));
        String typeStr = getCellValue(row, headerMap.get("TxnType"));
        String dirStr = getCellValue(row, headerMap.get("Direction"));
        String cpName = getCellValue(row, headerMap.get("CounterpartyName"));
        String cpAcc = getCellValue(row, headerMap.get("CounterpartyAccountNo"));
        String cpBank = getCellValue(row, headerMap.get("CounterpartyBank"));
        String cpCountry = getCellValue(row, headerMap.get("CounterpartyCountryCode"));
        String timestampStr = getCellValue(row, headerMap.get("TxnTimestamp"));
        String countryCode = getCellValue(row, headerMap.get("CountryCode"));

        boolean rowValid = true;

        rowValid &= checkNonBlank(displayRowNum, "TxnNo", txnNo, errors);
        rowValid &= checkNonBlank(displayRowNum, "OriginatorAccountNo", origAcc, errors);
        rowValid &= checkNonBlank(displayRowNum, "OriginatorName", origName, errors);
        rowValid &= checkNonBlank(displayRowNum, "Amount", amountStr, errors);
        rowValid &= checkNonBlank(displayRowNum, "Currency", currency, errors);
        rowValid &= checkNonBlank(displayRowNum, "TxnType", typeStr, errors);
        rowValid &= checkNonBlank(displayRowNum, "Direction", dirStr, errors);
        rowValid &= checkNonBlank(displayRowNum, "TxnTimestamp", timestampStr, errors);
        rowValid &= checkNonBlank(displayRowNum, "CountryCode", countryCode, errors);

        BigDecimal amount = null;
        if (amountStr != null && !amountStr.isBlank()) {
            try {
                amount = new BigDecimal(amountStr);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new BatchValidationErrorDto(displayRowNum, "Amount", "Amount must be greater than zero."));
                    rowValid = false;
                }
            } catch (NumberFormatException e) {
                errors.add(new BatchValidationErrorDto(displayRowNum, "Amount", "Invalid numeric amount: " + amountStr));
                rowValid = false;
            }
        }

        TransactionType txnType = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                txnType = TransactionType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new BatchValidationErrorDto(displayRowNum, "TxnType", "Invalid TxnType value: " + typeStr));
                rowValid = false;
            }
        }

        TransactionDirection direction = null;
        if (dirStr != null && !dirStr.isBlank()) {
            try {
                direction = TransactionDirection.valueOf(dirStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new BatchValidationErrorDto(displayRowNum, "Direction", "Invalid Direction value: " + dirStr));
                rowValid = false;
            }
        }

        LocalDateTime timestamp = null;
        if (timestampStr != null && !timestampStr.isBlank()) {
            timestamp = parseTimestamp(row, headerMap.get("TxnTimestamp"), timestampStr, displayRowNum, errors);
            if (timestamp == null) {
                rowValid = false;
            }
        }

        if (rowValid) {
            parsedRows.add(ParsedTransactionRowDto.builder()
                    .txnNo(txnNo)
                    .originatorAccountNo(origAcc)
                    .originatorName(origName)
                    .amount(amount)
                    .currency(currency)
                    .txnType(txnType)
                    .direction(direction)
                    .counterpartyName(cpName)
                    .counterpartyAccountNo(cpAcc)
                    .counterpartyBank(cpBank)
                    .counterpartyCountryCode(cpCountry)
                    .txnTimestamp(timestamp)
                    .countryCode(countryCode)
                    .build());
        }
    }

    private boolean checkNonBlank(int rowNum, String fieldName, String value, List<BatchValidationErrorDto> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new BatchValidationErrorDto(rowNum, fieldName, fieldName + " is a mandatory field and cannot be blank."));
            return false;
        }
        return true;
    }

    private LocalDateTime parseTimestamp(Row row, Integer colIdx, String strVal, int displayRowNum, List<BatchValidationErrorDto> errors) {
        if (colIdx != null && row.getCell(colIdx) != null) {
            Cell cell = row.getCell(colIdx);
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        }
        try {
            return LocalDateTime.parse(strVal, ISO_FORMATTER);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(strVal, SPACE_FORMATTER);
            } catch (DateTimeParseException e2) {
                errors.add(new BatchValidationErrorDto(displayRowNum, "TxnTimestamp", "Invalid date format. Expected ISO format (yyyy-MM-ddTHH:mm:ss): " + strVal));
                return null;
            }
        }
    }

    private String getCellValue(Row row, Integer colIdx) {
        if (colIdx == null || row == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}

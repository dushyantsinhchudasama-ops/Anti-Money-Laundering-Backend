package com.tss.aml.utils;

import com.tss.aml.entities.tenant.SarStr;

import java.nio.charset.StandardCharsets;

public class SarStrPdfGenerator {

    /**
     * Generates persistent PDF binary output (%PDF-1.4 format) for a filed SAR/STR record.
     *
     * @param sarStr the saved SarStr filing entity
     * @return PDF byte array
     */
    public static byte[] generateSarStrPdf(SarStr sarStr) {
        StringBuilder content = new StringBuilder();
        content.append("=========================================================\n");
        content.append("       SUSPICIOUS ACTIVITY / TRANSACTION REPORT          \n");
        content.append("=========================================================\n\n");
        content.append("Filing Reference : ").append(sarStr.getReferenceNumber() != null ? sarStr.getReferenceNumber() : "N/A").append("\n");
        content.append("Report Type      : ").append(sarStr.getReportType() != null ? sarStr.getReportType().name() : "SAR").append("\n");
        content.append("Submitted At     : ").append(sarStr.getSubmittedAt() != null ? sarStr.getSubmittedAt() : "N/A").append("\n");
        content.append("Filed By         : ").append(sarStr.getFiledBy() != null ? sarStr.getFiledBy().getEmail() : "N/A").append("\n");
        content.append("Case Code        : ").append(sarStr.getAmlCase() != null ? sarStr.getAmlCase().getCaseCode() : "N/A").append("\n\n");

        content.append("--- TYPOLOGY CATEGORY ---\n");
        content.append(sarStr.getTypologyCategory() != null ? sarStr.getTypologyCategory() : "N/A").append("\n\n");

        content.append("--- DESCRIPTION OF SUSPICIOUS ACTIVITY ---\n");
        content.append(sarStr.getDescriptionOfActivity() != null ? sarStr.getDescriptionOfActivity() : "N/A").append("\n\n");

        content.append("--- BASIS FOR SUSPICION ---\n");
        content.append(sarStr.getBasisForSuspicion() != null ? sarStr.getBasisForSuspicion() : "N/A").append("\n\n");

        content.append("--- SUPPORTING EVIDENCE & ACCOUNT RELATIONSHIPS ---\n");
        content.append(sarStr.getSupportingEvidence() != null ? sarStr.getSupportingEvidence() : "None provided").append("\n\n");

        content.append("=========================================================\n");
        content.append("          END OF REGULATORY FILING DOCUMENT              \n");
        content.append("=========================================================\n");

        String text = content.toString().replace("(", "\\(").replace(")", "\\)");
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        pdf.append("1 0 obj <</Type /Catalog /Pages 2 0 R>> endobj\n");
        pdf.append("2 0 obj <</Type /Pages /Kids [3 0 R] /Count 1>> endobj\n");
        pdf.append("3 0 obj <</Type /Page /Parent 2 0 R /Resources <</Font <</F1 4 0 R>>>> /MediaBox [0 0 612 792] /Contents 5 0 R>> endobj\n");
        pdf.append("4 0 obj <</Type /Font /Subtype /Type1 /BaseFont /Helvetica>> endobj\n");

        StringBuilder streamContent = new StringBuilder("BT /F1 10 Tf 50 740 Td 14 TL ");
        String[] lines = text.split("\n");
        for (String line : lines) {
            streamContent.append("(").append(line).append(") ' ");
        }
        streamContent.append("ET");

        byte[] streamBytes = streamContent.toString().getBytes(StandardCharsets.ISO_8859_1);
        pdf.append("5 0 obj <</Length ").append(streamBytes.length).append(">> stream\n");
        pdf.append(streamContent).append("\nendstream\nendobj\n");

        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        pdf.append("0000000009 00000 n \n");
        pdf.append("0000000056 00000 n \n");
        pdf.append("0000000111 00000 n \n");
        pdf.append("0000000212 00000 n \n");
        pdf.append("0000000281 00000 n \n");
        pdf.append("trailer <</Size 6 /Root 1 0 R>>\nstartxref\n380\n%%EOF");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}

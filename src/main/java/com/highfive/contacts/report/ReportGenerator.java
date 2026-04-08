package com.highfive.contacts.report;

import com.highfive.contacts.model.ContactStatus;
import com.highfive.contacts.model.MailchimpContact;
import com.highfive.contacts.model.UploadReport;
import com.highfive.contacts.model.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates an HTML report summarising classification results.
 * Also saves a local backup copy to the reports/ directory.
 */
public class ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public String generate(UploadReport report) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String html = buildHtml(report, timestamp);

        // Save local backup
        String fileTimestamp = LocalDateTime.now().format(FILE_FMT);
        Path reportsDir = Paths.get("reports");
        Files.createDirectories(reportsDir);
        Path outFile = reportsDir.resolve("upload_report_" + fileTimestamp + ".html");
        Files.writeString(outFile, html);
        System.out.println("[Report] Saved local copy: " + outFile);

        return html;
    }

    private String buildHtml(UploadReport report, String timestamp) {
        long total        = report.totalContacts();
        long alreadyExist = report.countByStatus(ContactStatus.ALREADY_EXISTS);
        long badFormat    = report.countByStatus(ContactStatus.BAD_FORMAT);
        long gibberish    = report.countByStatus(ContactStatus.GIBBERISH);
        long toUpload     = report.countByStatus(ContactStatus.NEW);

        List<ValidationResult> newContacts = report.getAllResults().stream()
                .filter(ValidationResult::isNew).collect(Collectors.toList());
        List<ValidationResult> skipped = report.getAllResults().stream()
                .filter(r -> !r.isNew()).collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<title>Contact Upload Report — ").append(esc(timestamp)).append("</title>")
          .append("<style>")
          .append("body{font-family:Arial,sans-serif;margin:32px;color:#222}")
          .append("h1{color:#1a5276}h2{color:#2874a6;border-bottom:1px solid #aed6f1;padding-bottom:4px}")
          .append("table{border-collapse:collapse;width:100%;margin-bottom:24px}")
          .append("th{background:#2874a6;color:#fff;padding:8px 12px;text-align:left}")
          .append("td{padding:7px 12px;border-bottom:1px solid #d5d8dc}")
          .append("tr:nth-child(even){background:#eaf4fb}")
          .append(".stat-table td:first-child{font-weight:bold;width:260px}")
          .append(".flag{background:#fdf2f8;border-left:4px solid #e74c3c;padding:8px 14px;margin:6px 0;border-radius:2px}")
          .append(".ok{background:#eafaf1;border-left:4px solid #27ae60;padding:8px 14px;margin:6px 0;border-radius:2px}")
          .append(".badge-new{background:#27ae60;color:#fff;border-radius:3px;padding:2px 7px;font-size:0.85em}")
          .append(".badge-dup{background:#7f8c8d;color:#fff;border-radius:3px;padding:2px 7px;font-size:0.85em}")
          .append(".badge-fmt{background:#e67e22;color:#fff;border-radius:3px;padding:2px 7px;font-size:0.85em}")
          .append(".badge-gib{background:#e74c3c;color:#fff;border-radius:3px;padding:2px 7px;font-size:0.85em}")
          .append("</style></head><body>");

        // Header
        sb.append("<h1>Contact Upload Report</h1>")
          .append("<p><strong>Generated:</strong> ").append(esc(timestamp)).append("</p>")
          .append("<p><strong>Source file:</strong> ").append(esc(report.getCsvPath())).append("</p>");

        // Summary table
        sb.append("<h2>Summary</h2>")
          .append("<table class=\"stat-table\">")
          .append("<tr><td>Total contacts in CSV</td><td>").append(total).append("</td></tr>")
          .append("<tr><td>Already in OwnerRez (skipped)</td><td>").append(alreadyExist).append("</td></tr>")
          .append("<tr><td>Bad email format (skipped)</td><td>").append(badFormat).append("</td></tr>")
          .append("<tr><td>Gibberish emails (skipped)</td><td>").append(gibberish).append("</td></tr>")
          .append("<tr><td><strong>Contacts eligible to upload</strong></td><td><strong>").append(toUpload).append("</strong></td></tr>")
          .append("</table>");

        // Programmatic review flags
        sb.append("<h2>Programmatic Review</h2>");
        for (String flag : report.getReviewFlags()) {
            boolean isOk = flag.startsWith("No anomalies");
            sb.append("<div class=\"").append(isOk ? "ok" : "flag").append("\">")
              .append(esc(flag)).append("</div>");
        }

        // Contacts to be uploaded
        sb.append("<h2>Contacts to Be Uploaded (").append(toUpload).append(")</h2>");
        if (newContacts.isEmpty()) {
            sb.append("<p><em>None.</em></p>");
        } else {
            sb.append("<table><tr><th>Email</th><th>First Name</th><th>Last Name</th><th>Phone</th></tr>");
            for (ValidationResult r : newContacts) {
                MailchimpContact c = r.getContact();
                sb.append("<tr>")
                  .append("<td>").append(esc(c.getEmail())).append("</td>")
                  .append("<td>").append(esc(c.getFirstName())).append("</td>")
                  .append("<td>").append(esc(c.getLastName())).append("</td>")
                  .append("<td>").append(esc(c.getPhone())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        // Skipped contacts
        sb.append("<h2>Skipped Contacts (").append(skipped.size()).append(")</h2>");
        if (skipped.isEmpty()) {
            sb.append("<p><em>None.</em></p>");
        } else {
            sb.append("<table><tr><th>Email</th><th>First Name</th><th>Last Name</th><th>Reason</th><th>Detail</th></tr>");
            for (ValidationResult r : skipped) {
                MailchimpContact c = r.getContact();
                sb.append("<tr>")
                  .append("<td>").append(esc(c.getEmail())).append("</td>")
                  .append("<td>").append(esc(c.getFirstName())).append("</td>")
                  .append("<td>").append(esc(c.getLastName())).append("</td>")
                  .append("<td>").append(badgeFor(r.getStatus())).append("</td>")
                  .append("<td>").append(esc(r.getReason())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        sb.append("<hr><p style=\"color:#7f8c8d;font-size:0.85em\">")
          .append("Upload Contacts to OwnerRez — High Five Retreats</p>")
          .append("</body></html>");

        return sb.toString();
    }

    private String badgeFor(ContactStatus status) {
        return switch (status) {
            case NEW            -> "<span class=\"badge-new\">NEW</span>";
            case ALREADY_EXISTS -> "<span class=\"badge-dup\">ALREADY EXISTS</span>";
            case BAD_FORMAT     -> "<span class=\"badge-fmt\">BAD FORMAT</span>";
            case GIBBERISH      -> "<span class=\"badge-gib\">GIBBERISH</span>";
        };
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

package com.highfive.contacts;

import com.highfive.contacts.csv.MailchimpCsvReader;
import com.highfive.contacts.email.GmailSender;
import com.highfive.contacts.model.*;
import com.highfive.contacts.ownerrez.OwnerRezClient;
import com.highfive.contacts.report.ReportGenerator;
import com.highfive.contacts.review.ProgrammaticReviewer;
import com.highfive.contacts.validation.EmailFormatValidator;
import com.highfive.contacts.validation.GibberishDetector;
import com.highfive.contacts.validation.SuspiciousDomainChecker;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Upload Mailchimp Contacts to OwnerRez ===");

        // ── 1. Load config ────────────────────────────────────────────────────
        Properties config = loadConfig();
        String csvPath             = config.getProperty("csv.path", "data/contacts.csv");
        String orEmail             = require(config, "ownerrez.email");
        String orToken             = require(config, "ownerrez.token");
        String hfToken             = require(config, "huggingface.api.token");
        String hfModel             = config.getProperty("huggingface.model.id",
                                        "madhurjindal/autonlp-Gibberish-Detector-492513457");
        String smtpHost            = config.getProperty("smtp.host", "smtp.gmail.com");
        int    smtpPort            = Integer.parseInt(config.getProperty("smtp.port", "587"));
        String smtpUsername        = require(config, "smtp.username");
        String smtpPassword        = require(config, "smtp.password");
        String gmailFrom           = require(config, "smtp.from");
        String gmailTo             = require(config, "smtp.to");
        boolean autoConfirm        = Boolean.parseBoolean(config.getProperty("upload.auto.confirm", "false"));

        // Allow --auto flag to override config
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--auto")) { autoConfirm = true; }
        }

        // ── 2. Read Mailchimp CSV ─────────────────────────────────────────────
        MailchimpCsvReader csvReader = new MailchimpCsvReader();
        List<MailchimpContact> contacts = csvReader.read(csvPath);
        System.out.println("Total contacts in CSV: " + contacts.size());

        if (contacts.isEmpty()) {
            System.out.println("No contacts found in CSV. Exiting.");
            return;
        }

        // ── 3. Check OwnerRez for existing contacts ───────────────────────────
        OwnerRezClient ownerRez = new OwnerRezClient(orEmail, orToken);
        List<ValidationResult> results = new ArrayList<>();

        System.out.println("\nChecking contacts against OwnerRez...");
        for (MailchimpContact contact : contacts) {
            ValidationResult r = new ValidationResult(contact);
            try {
                if (ownerRez.existsByEmail(contact.getEmail())) {
                    r.setStatus(ContactStatus.ALREADY_EXISTS, "Guest already exists in OwnerRez");
                    System.out.println("  [EXISTS]  " + contact.getEmail());
                }
            } catch (IOException e) {
                System.err.println("  [ERROR]   OwnerRez lookup failed for " + contact.getEmail() + ": " + e.getMessage());
                r.setStatus(ContactStatus.ALREADY_EXISTS, "OwnerRez lookup error: " + e.getMessage());
            }
            results.add(r);
        }

        // ── 4. Validate email format ──────────────────────────────────────────
        EmailFormatValidator formatValidator = new EmailFormatValidator();
        System.out.println("\nValidating email formats...");
        for (ValidationResult r : results) {
            if (!r.isNew()) continue;
            EmailFormatValidator.Result fmtResult = formatValidator.validate(r.getContact().getEmail());
            if (!fmtResult.valid()) {
                r.setStatus(ContactStatus.BAD_FORMAT, fmtResult.reason());
                System.out.println("  [BAD FMT] " + r.getContact().getEmail() + " — " + fmtResult.reason());
            }
        }

        // ── 5. Suspicious domain + timezone check ────────────────────────────
        SuspiciousDomainChecker suspiciousChecker = new SuspiciousDomainChecker();
        System.out.println("\nChecking for suspicious domains and timezones...");
        for (ValidationResult r : results) {
            if (!r.isNew()) continue;
            MailchimpContact c = r.getContact();

            SuspiciousDomainChecker.Result suspResult = suspiciousChecker.checkLength(c.getEmail());
            if (!suspResult.suspicious()) {
                suspResult = suspiciousChecker.check(c.getEmail());
            }
            if (!suspResult.suspicious()) {
                suspResult = suspiciousChecker.checkTimezone(c.getTimezone());
            }
            if (suspResult.suspicious()) {
                r.setStatus(ContactStatus.SUSPICIOUS, suspResult.reason());
                System.out.println("  [SUSPIC]  " + c.getEmail() + " — " + suspResult.reason());
            }
        }

        // ── 7. Gibberish detection via HuggingFace ────────────────────────────
        GibberishDetector gibberishDetector = new GibberishDetector(hfToken, hfModel);
        long toCheck = results.stream().filter(ValidationResult::isNew).count();
        System.out.println("\nChecking " + toCheck + " contact(s) for gibberish via HuggingFace...");
        for (ValidationResult r : results) {
            if (!r.isNew()) continue;
            try {
                if (gibberishDetector.isGibberish(r.getContact().getEmail())) {
                    r.setStatus(ContactStatus.GIBBERISH, "Email local part classified as gibberish");
                    System.out.println("  [GIBBER]  " + r.getContact().getEmail());
                }
            } catch (IOException e) {
                System.err.println("  [WARN]    HuggingFace check failed for "
                        + r.getContact().getEmail() + ": " + e.getMessage() + " — treating as clean");
            }
        }

        // ── 8. Programmatic review ────────────────────────────────────────────
        System.out.println("\nRunning programmatic review...");
        ProgrammaticReviewer reviewer = new ProgrammaticReviewer();
        List<String> reviewFlags = reviewer.review(results);

        // ── 7. Generate report ────────────────────────────────────────────────
        UploadReport uploadReport = new UploadReport(results, reviewFlags, csvPath);
        ReportGenerator reportGen = new ReportGenerator();
        System.out.println("\nGenerating report...");
        String htmlReport = reportGen.generate(uploadReport);

        // ── 8. Send report via Gmail ──────────────────────────────────────────
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String subject = "Contact Upload Report — " + timestamp;
        GmailSender gmailSender = new GmailSender(smtpHost, smtpPort, smtpUsername, smtpPassword, gmailFrom, gmailTo);
        System.out.println("\nSending report to " + gmailTo + "...");
        gmailSender.sendReport(subject, htmlReport);

        // ── 9. Confirmation gate ──────────────────────────────────────────────
        printSummary(results);
        long newCount = results.stream().filter(ValidationResult::isNew).count();

        if (newCount == 0) {
            System.out.println("\nNo new contacts to upload. Done.");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        if (!autoConfirm) {
            System.out.println("\nReport sent. Review the email at " + gmailTo);
            System.out.print("Type CONFIRM to proceed to manual verification, or anything else to abort: ");
            String input = scanner.nextLine().trim();
            if (!input.equalsIgnoreCase("CONFIRM")) {
                System.out.println("Upload cancelled. No contacts were uploaded.");
                return;
            }
        } else {
            System.out.println("\n[Auto-confirm] Proceeding with upload of " + newCount + " contact(s).");
        }

        // ── 10. Manual verification ───────────────────────────────────────────
        List<ValidationResult> candidates = results.stream()
                .filter(ValidationResult::isNew)
                .collect(Collectors.toList());

        if (!autoConfirm) {
            System.out.println("\n=== Manual Verification (" + candidates.size() + " contacts) ===");
            System.out.println("For each contact: press Y to approve, N to reject, Q to quit verification.\n");

            int idx = 0;
            for (ValidationResult r : candidates) {
                idx++;
                MailchimpContact c = r.getContact();
                System.out.println("[" + idx + "/" + candidates.size() + "]");
                System.out.println("  Email:      " + c.getEmail());
                System.out.println("  Name:       " + c.getFirstName() + " " + c.getLastName());
                System.out.println("  Subscribed: " + (c.getSubscriptionDate().isEmpty() ? "(unknown)" : c.getSubscriptionDate()));
                System.out.println("  Timezone:   " + (c.getTimezone().isEmpty() ? "(unknown)" : c.getTimezone()));
                System.out.print("  Approve? (Y/N/Q): ");

                String answer = scanner.nextLine().trim().toUpperCase();
                if (answer.equals("Q")) {
                    System.out.println("Verification stopped. Remaining contacts will not be uploaded.");
                    // Mark remaining (including this one) as rejected
                    for (int j = idx - 1; j < candidates.size(); j++) {
                        candidates.get(j).setStatus(ContactStatus.MANUALLY_REJECTED, "Verification stopped by user");
                    }
                    break;
                } else if (!answer.equals("Y")) {
                    r.setStatus(ContactStatus.MANUALLY_REJECTED, "Rejected during manual review");
                    System.out.println("  → Rejected.");
                } else {
                    System.out.println("  → Approved.");
                }
                System.out.println();
            }
        }

        // ── 11. Upload approved contacts to OwnerRez ──────────────────────────
        List<MailchimpContact> uploadedContacts = new ArrayList<>();
        int uploadedCount = 0, failed = 0;
        System.out.println("\nUploading approved contact(s) to OwnerRez...");
        for (ValidationResult r : results) {
            if (!r.isNew()) continue;
            try {
                int guestId = ownerRez.createGuest(r.getContact());
                System.out.println("  [UPLOADED] " + r.getContact().getEmail() + " → guest ID " + guestId);
                uploadedContacts.add(r.getContact());
                uploadedCount++;
            } catch (IOException e) {
                System.err.println("  [FAILED]   " + r.getContact().getEmail() + ": " + e.getMessage());
                failed++;
            }
        }

        // ── 12. Final summary + email report ──────────────────────────────────
        System.out.println("\n=== Upload Complete ===");
        System.out.println("Uploaded:  " + uploadedCount);
        System.out.println("Failed:    " + failed);
        System.out.println("Rejected:  " + results.stream().filter(r -> r.getStatus() == ContactStatus.MANUALLY_REJECTED).count());

        System.out.println("\nSending final upload report...");
        String finalHtml = reportGen.generateFinalReport(uploadedContacts);
        String finalSubject = "Contacts Added to OwnerRez — " + timestamp;
        gmailSender.sendReport(finalSubject, finalHtml);
        System.out.println("Final report sent to " + gmailTo);
    }

    private static void printSummary(List<ValidationResult> results) {
        long total      = results.size();
        long exists     = results.stream().filter(r -> r.getStatus() == ContactStatus.ALREADY_EXISTS).count();
        long badFmt     = results.stream().filter(r -> r.getStatus() == ContactStatus.BAD_FORMAT).count();
        long suspicious = results.stream().filter(r -> r.getStatus() == ContactStatus.SUSPICIOUS).count();
        long gibberish  = results.stream().filter(r -> r.getStatus() == ContactStatus.GIBBERISH).count();
        long newC       = results.stream().filter(ValidationResult::isNew).count();

        System.out.println("\n--- Classification Summary ---");
        System.out.println("  Total:           " + total);
        System.out.println("  Already exists:  " + exists);
        System.out.println("  Bad format:      " + badFmt);
        System.out.println("  Suspicious:      " + suspicious);
        System.out.println("  Gibberish:       " + gibberish);
        System.out.println("  To upload:       " + newC);
    }

    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new IOException(
                        "config.properties not found on classpath.\n"
                        + "Copy src/main/resources/config.properties.template → src/main/resources/config.properties "
                        + "and fill in your credentials.");
            }
            props.load(is);
        }
        return props;
    }

    private static String require(Properties props, String key) {
        String val = props.getProperty(key, "").trim();
        if (val.isEmpty()) {
            throw new IllegalStateException("Missing required config: " + key
                    + ". Check your config.properties.");
        }
        return val;
    }
}

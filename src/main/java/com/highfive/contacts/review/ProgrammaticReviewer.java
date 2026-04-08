package com.highfive.contacts.review;

import com.highfive.contacts.model.ContactStatus;
import com.highfive.contacts.model.ValidationResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs automated checks on classified contacts before the report is sent.
 * Flags anomalies so the operator can review them in the report.
 */
public class ProgrammaticReviewer {

    public List<String> review(List<ValidationResult> results) {
        List<String> flags = new ArrayList<>();

        checkDuplicatesWithinCsv(results, flags);
        checkHighGibberishRate(results, flags);
        checkAllRejected(results, flags);
        checkSuspiciousDomains(results, flags);

        if (flags.isEmpty()) {
            flags.add("No anomalies detected — all checks passed.");
        }

        System.out.println("[Review] " + flags.size() + " flag(s) generated.");
        for (String flag : flags) {
            System.out.println("  [FLAG] " + flag);
        }
        return flags;
    }

    /** Flag emails that appear more than once in the CSV. */
    private void checkDuplicatesWithinCsv(List<ValidationResult> results, List<String> flags) {
        Map<String, Long> counts = results.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getContact().getEmail().toLowerCase(),
                        Collectors.counting()));

        List<String> dupes = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                .sorted()
                .collect(Collectors.toList());

        if (!dupes.isEmpty()) {
            flags.add("Duplicate emails in CSV (" + dupes.size() + "): " + String.join(", ", dupes));
        }
    }

    /** Warn if more than 20% of non-ALREADY_EXISTS contacts are gibberish. */
    private void checkHighGibberishRate(List<ValidationResult> results, List<String> flags) {
        long validated = results.stream()
                .filter(r -> r.getStatus() != ContactStatus.ALREADY_EXISTS)
                .count();
        long gibberish = results.stream()
                .filter(r -> r.getStatus() == ContactStatus.GIBBERISH)
                .count();

        if (validated > 0 && (double) gibberish / validated > 0.20) {
            long pct = Math.round((double) gibberish / validated * 100);
            flags.add("High gibberish rate: " + pct + "% of validated contacts flagged as gibberish (" + gibberish + "/" + validated + ")");
        }
    }

    /** Warn if no contacts would be uploaded. */
    private void checkAllRejected(List<ValidationResult> results, List<String> flags) {
        long newCount = results.stream()
                .filter(r -> r.getStatus() == ContactStatus.NEW)
                .count();
        if (newCount == 0) {
            flags.add("WARNING: Zero contacts are eligible for upload — all were skipped or rejected.");
        }
    }

    /**
     * Flag email domains that appear only once in the entire list.
     * These can indicate typos (e.g. gmial.com instead of gmail.com).
     * Only checked if the list has more than 10 contacts.
     */
    private void checkSuspiciousDomains(List<ValidationResult> results, List<String> flags) {
        if (results.size() <= 10) return;

        Map<String, Long> domainCounts = results.stream()
                .map(r -> r.getContact().getEmail().toLowerCase())
                .filter(e -> e.contains("@"))
                .collect(Collectors.groupingBy(
                        e -> e.substring(e.indexOf('@') + 1),
                        Collectors.counting()));

        List<String> suspiciousDomains = domainCounts.entrySet().stream()
                .filter(e -> e.getValue() == 1)
                .map(Map.Entry::getKey)
                .filter(d -> !isWellKnownDomain(d))
                .sorted()
                .collect(Collectors.toList());

        if (!suspiciousDomains.isEmpty()) {
            flags.add("Domains appearing only once (possible typos): " + String.join(", ", suspiciousDomains));
        }
    }

    private boolean isWellKnownDomain(String domain) {
        return domain.equals("gmail.com") || domain.equals("yahoo.com")
                || domain.equals("hotmail.com") || domain.equals("outlook.com")
                || domain.equals("icloud.com") || domain.equals("me.com")
                || domain.equals("aol.com") || domain.equals("protonmail.com")
                || domain.equals("highfiveretreats.com");
    }
}

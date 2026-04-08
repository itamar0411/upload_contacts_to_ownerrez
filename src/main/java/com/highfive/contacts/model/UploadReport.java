package com.highfive.contacts.model;

import java.util.List;

/**
 * Aggregated data passed to ReportGenerator.
 */
public class UploadReport {

    private final List<ValidationResult> allResults;
    private final List<String> reviewFlags;
    private final String csvPath;

    public UploadReport(List<ValidationResult> allResults, List<String> reviewFlags, String csvPath) {
        this.allResults = allResults;
        this.reviewFlags = reviewFlags;
        this.csvPath = csvPath;
    }

    public List<ValidationResult> getAllResults() { return allResults; }
    public List<String> getReviewFlags()          { return reviewFlags; }
    public String getCsvPath()                    { return csvPath; }

    public long countByStatus(ContactStatus status) {
        return allResults.stream().filter(r -> r.getStatus() == status).count();
    }

    public long totalContacts() { return allResults.size(); }
}

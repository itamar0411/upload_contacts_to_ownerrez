package com.highfive.contacts.csv;

import com.highfive.contacts.model.MailchimpContact;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a Mailchimp CSV export and returns a list of contacts.
 *
 * Uses header-aware reading so column order doesn't matter.
 * Standard Mailchimp export columns used:
 *   "Email Address", "First Name", "Last Name", "Phone Number", "Status", "Tags"
 */
public class MailchimpCsvReader {

    public List<MailchimpContact> read(String filePath) throws IOException {
        List<MailchimpContact> contacts = new ArrayList<>();
        int skipped = 0;

        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(filePath))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                String email = get(row, "Email Address", "email", "Email");
                if (email.isEmpty()) {
                    skipped++;
                    continue;
                }
                String firstName = get(row, "First Name", "first_name", "FirstName");
                String lastName  = get(row, "Last Name",  "last_name",  "LastName");
                String phone     = get(row, "Phone Number", "phone", "Phone");
                String status    = get(row, "Status", "status");
                String tags      = get(row, "Tags", "tags");

                contacts.add(new MailchimpContact(email, firstName, lastName, phone, status, tags));
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV parse error: " + e.getMessage(), e);
        }

        System.out.println("[CSV] Read " + contacts.size() + " contacts from " + filePath
                + (skipped > 0 ? " (" + skipped + " rows skipped — no email)" : ""));
        return contacts;
    }

    /** Returns the first non-blank value found for any of the given candidate column names. */
    private String get(Map<String, String> row, String... candidates) {
        for (String key : candidates) {
            String val = row.get(key);
            if (val != null && !val.isBlank()) return val.trim();
        }
        return "";
    }
}

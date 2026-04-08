package com.highfive.contacts.model;

/**
 * Associates a MailchimpContact with the outcome of processing.
 */
public class ValidationResult {

    private final MailchimpContact contact;
    private ContactStatus status;
    private String reason;

    public ValidationResult(MailchimpContact contact) {
        this.contact = contact;
        this.status = ContactStatus.NEW;
        this.reason = "";
    }

    public MailchimpContact getContact() { return contact; }
    public ContactStatus getStatus()     { return status; }
    public String getReason()            { return reason; }

    public void setStatus(ContactStatus status, String reason) {
        this.status = status;
        this.reason = reason == null ? "" : reason;
    }

    public boolean isNew() { return status == ContactStatus.NEW; }
}

package com.highfive.contacts.model;

/**
 * Represents a single row from a Mailchimp CSV export.
 * Field names match the standard Mailchimp column headers.
 */
public class MailchimpContact {

    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final String status;    // subscribed / unsubscribed / cleaned
    private final String tags;      // comma-separated Mailchimp tags
    private final String timezone;         // IANA timezone (e.g. America/New_York)
    private final String subscriptionDate; // CONFIRM_TIME from Mailchimp

    public MailchimpContact(String email, String firstName, String lastName,
                            String phone, String status, String tags,
                            String timezone, String subscriptionDate) {
        this.email            = email            == null ? "" : email.trim();
        this.firstName        = firstName        == null ? "" : firstName.trim();
        this.lastName         = lastName         == null ? "" : lastName.trim();
        this.phone            = phone            == null ? "" : phone.trim();
        this.status           = status           == null ? "" : status.trim();
        this.tags             = tags             == null ? "" : tags.trim();
        this.timezone         = timezone         == null ? "" : timezone.trim();
        this.subscriptionDate = subscriptionDate == null ? "" : subscriptionDate.trim();
    }

    public String getEmail()            { return email; }
    public String getFirstName()        { return firstName; }
    public String getLastName()         { return lastName; }
    public String getPhone()            { return phone; }
    public String getStatus()           { return status; }
    public String getTags()             { return tags; }
    public String getTimezone()         { return timezone; }
    public String getSubscriptionDate() { return subscriptionDate; }

    @Override
    public String toString() {
        return email + " (" + firstName + " " + lastName + ")";
    }
}

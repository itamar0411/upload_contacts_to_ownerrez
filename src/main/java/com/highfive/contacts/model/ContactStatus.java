package com.highfive.contacts.model;

public enum ContactStatus {
    /** Passed all checks — will be uploaded to OwnerRez. */
    NEW,
    /** Already exists in OwnerRez — skipped. */
    ALREADY_EXISTS,
    /** Email address failed format validation — skipped. */
    BAD_FORMAT,
    /** Email local part classified as gibberish by HuggingFace — skipped. */
    GIBBERISH,
    /** Email domain is a known disposable/test/spam provider — skipped. */
    SUSPICIOUS,
    /** Passed all automated checks but rejected during manual review — skipped. */
    MANUALLY_REJECTED
}

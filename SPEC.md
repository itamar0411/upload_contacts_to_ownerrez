# Upload Mailchimp Contacts to OwnerRez — Specification

## Overview

A Java program that reads a CSV export of Mailchimp subscribers and uploads them to OwnerRez as guests. It runs each contact through a multi-layer validation pipeline, sends a review report before uploading, requires manual approval for each contact, then sends a final confirmation report of what was actually added.

---

## Functional Requirements

### 1. Language & Runtime
- Java 17, Maven build, packaged as a fat JAR via `maven-shade-plugin`

### 2. Input
- Read a CSV file of contacts downloaded from Mailchimp (`subscribed` export)
- Columns read: `Email Address`, `First Name`, `Last Name`, `Phone Number`, `TIMEZONE`, `CONFIRM_TIME` (fallback `OPTIN_TIME`), `Status`, `Tags`
- File path is configurable via `config.properties`
- A contact is uniquely identified by its email address
- Rows with no email are skipped silently

### 3. Validation Pipeline

Contacts pass through checks in order. Each check assigns a status; once a contact is assigned a non-NEW status it skips all remaining checks.

#### 3.1 Duplicate Detection — OwnerRez (`ALREADY_EXISTS`)
- Query OwnerRez API `GET /v2/guests?q={email}`
- Case-insensitive exact match against `email_addresses[].address`
- If found → mark `ALREADY_EXISTS`, skip

#### 3.2 Email Format Validation (`BAD_FORMAT`)
Three-layer check:
1. **Regex**: `^[^\s@]+@[^\s@]+\.[^\s@]{2,}$` — fast structural check
2. **Apache Commons Validator** — RFC-compliant format check
3. **DNS domain check** — looks up MX record; falls back to A record if no MX
   - Up to 3 retries with 500 ms delay between attempts
   - Results cached per domain to avoid redundant lookups
- Any layer failing → mark `BAD_FORMAT`, skip

#### 3.3 Suspicious Contact Detection (`SUSPICIOUS`)
Three sub-checks, first match wins:

1. **Email length** (RFC 5321 limits):
   - Total address > 254 characters → SUSPICIOUS
   - Local part (before `@`) > 64 characters → SUSPICIOUS

2. **Disposable/test domain blocklist** (~150 domains):
   - Known throwaway providers: mailinator, guerrillamail, yopmail, trashmail, tempmail, etc.
   - Explicit test domains: `testform.xyz`, `example.com`, `fake.com`, `test.com`, etc.
   - Domain label contains suspicious keywords: `test`, `fake`, `temp`, `temporary`, `disposable`, `spam`, `trash`, `junk`, `dummy`, `sample`, `example`, `invalid`, `noreply`, `no-reply`, `throwaway`

3. **Non-US timezone**:
   - If the `TIMEZONE` column is populated and is not in the whitelist of US IANA timezone IDs → SUSPICIOUS
   - Contacts with no timezone recorded are **not** flagged
   - US timezone whitelist covers all 50 states, US territories (Puerto Rico, Guam, USVI, Samoa), and legacy `US/` aliases

#### 3.4 Gibberish Detection — HuggingFace (`GIBBERISH`)
- Uses HuggingFace Inference API: `router.huggingface.co`
- Model: `madhurjindal/autonlp-Gibberish-Detector-492513457`
- Only the **local part** of the email (before `@`) is sent to the model
- Labels: `clean`, `mild gibberish`, `word salad`, `noise`
- If top label is `word salad` or `noise` with score > 0.85 → mark `GIBBERISH`, skip
- Local parts ≤ 3 characters are skipped (too short to classify reliably)
- Results cached per local part; 100 ms rate-limit pause every 50 calls
- Up to 2 retries on 503 (model loading); 10 s wait between retries

### 4. Programmatic Review (Pre-Report)
Before generating the report, automated checks flag anomalies:
1. **Duplicate emails within the CSV** — same email appearing more than once
2. **High gibberish rate** — if > 20% of validated contacts are gibberish
3. **All-rejected batch** — if 0 contacts would be uploaded
4. **Suspicious single-occurrence domains** — domains appearing only once across the full list (potential typos like `gmial.com`), excluding well-known providers

Flags are included in the report summary section.

### 5. Pre-Upload Report (Email 1 of 2)
Generated and sent **before any contact is uploaded**.

**HTML report contents:**

*Summary table:*
- Total contacts in CSV
- Already in OwnerRez (skipped)
- Bad email format (skipped)
- Suspicious domain/timezone/length (skipped)
- Gibberish emails (skipped)
- Contacts eligible to upload

*Programmatic review flags*

*Contacts to be uploaded table:* email, first name, last name, phone, timezone, subscribed date

*Skipped contacts table:* email, first name, last name, timezone, subscribed date, reason badge, detail

Reason badges: `ALREADY EXISTS` (grey) · `BAD FORMAT` (orange) · `SUSPICIOUS` (purple) · `GIBBERISH` (red)

A local backup is saved to `reports/upload_report_{timestamp}.html`.
Sent via Gmail SMTP (App Password) from/to `host@highfiveretreats.com`.

### 6. Confirmation Gate
After the report email is sent, the program prompts:
> `Type CONFIRM to proceed to manual verification, or anything else to abort`

If the user does not type `CONFIRM`, the program exits and nothing is uploaded.

### 7. Manual Verification
Each contact that passed all automated checks is presented one at a time:

```
[3/12]
  Email:      jessica@example.com
  Name:       Jessica Fosberg
  Subscribed: 2024-11-13 13:15:07
  Timezone:   America/New_York
  Approve? (Y/N/Q):
```

- **Y** — approved for upload
- **N** — marked `MANUALLY_REJECTED`, skipped
- **Q** — stops verification; this contact and all remaining are marked `MANUALLY_REJECTED`

### 8. Upload
- Only contacts approved in manual verification (still `NEW` status) are uploaded
- Upload via OwnerRez API `POST /v2/guests`
- Fields sent: first name, last name, email (default), phone (mobile)

### 9. Final Report (Email 2 of 2)
Sent after all uploads complete. Lists every contact actually added to OwnerRez:
- Summary banner: "X contact(s) successfully added to OwnerRez"
- Table: email, first name, last name, phone, timezone, subscribed date

Local backup saved to `reports/final_upload_report_{timestamp}.html`.

---

## Contact Statuses

| Status | Meaning |
|--------|---------|
| `NEW` | Passed all checks and approved — uploaded |
| `ALREADY_EXISTS` | Email found in OwnerRez — skipped |
| `BAD_FORMAT` | Failed regex, RFC, or DNS domain check — skipped |
| `SUSPICIOUS` | Disposable domain, non-US timezone, or excessive length — skipped |
| `GIBBERISH` | Local part classified as gibberish by HuggingFace — skipped |
| `MANUALLY_REJECTED` | Passed automated checks but rejected during manual review — skipped |

---

## Technical Design

### Project Structure
```
upload_contacts_to_ownerrez/
├── pom.xml
├── run.sh
├── SPEC.md
├── .gitignore
├── data/
│   └── contacts.csv                         (gitignored — Mailchimp subscribed export)
├── reports/
│   ├── upload_report_{timestamp}.html       (gitignored — pre-upload review report)
│   └── final_upload_report_{timestamp}.html (gitignored — post-upload confirmation)
└── src/main/java/com/highfive/contacts/
    ├── Main.java
    ├── model/
    │   ├── MailchimpContact.java             (CSV row data)
    │   ├── ContactStatus.java               (enum: NEW/ALREADY_EXISTS/BAD_FORMAT/SUSPICIOUS/GIBBERISH/MANUALLY_REJECTED)
    │   ├── ValidationResult.java            (contact + status + reason)
    │   └── UploadReport.java                (aggregated results for report)
    ├── csv/
    │   └── MailchimpCsvReader.java          (opencsv, header-aware)
    ├── ownerrez/
    │   └── OwnerRezClient.java              (lookup + create via /v2/guests, retry on 429)
    ├── validation/
    │   ├── EmailFormatValidator.java        (regex + commons-validator + DNS MX/A lookup)
    │   ├── SuspiciousDomainChecker.java     (length + blocklist + keyword + timezone)
    │   └── GibberishDetector.java           (HuggingFace inference API)
    ├── review/
    │   └── ProgrammaticReviewer.java        (automated pre-report anomaly detection)
    ├── report/
    │   └── ReportGenerator.java             (HTML report + final report generation)
    └── email/
        └── GmailSender.java                 (SMTP with Google App Password)
```

### Configuration (`config.properties` — gitignored)
```properties
ownerrez.email=MeitalYitzhak@gmail.com
ownerrez.token=pt_xxxxxxxxxxxxxxxx
csv.path=data/contacts.csv
huggingface.api.token=hf_xxxxxxxxxxxxxxxx
huggingface.model.id=madhurjindal/autonlp-Gibberish-Detector-492513457
smtp.host=smtp.gmail.com
smtp.port=587
smtp.username=host@highfiveretreats.com
smtp.password=xxxx xxxx xxxx xxxx
smtp.from=host@highfiveretreats.com
smtp.to=host@highfiveretreats.com
upload.auto.confirm=false
```

### APIs Used
| API | Purpose | Auth |
|-----|---------|------|
| OwnerRez v2 `/guests` | Lookup + create guests | HTTP Basic Auth (email:token) |
| HuggingFace Inference (`router.huggingface.co`) | Gibberish classification | Bearer token |
| Gmail SMTP (`smtp.gmail.com:587`) | Send report emails | Google App Password |

### Execution Flow
```
1.  Load config
2.  Read Mailchimp CSV → List<MailchimpContact>
3.  Check each email against OwnerRez → mark ALREADY_EXISTS
4.  Validate email format (regex + RFC + DNS) → mark BAD_FORMAT
5.  Check length, disposable domain, non-US timezone → mark SUSPICIOUS
6.  Check local part via HuggingFace → mark GIBBERISH
7.  Run ProgrammaticReviewer → generate anomaly flags
8.  Generate HTML pre-upload report (Email 1) + save local copy
9.  Send report via Gmail SMTP
10. Prompt: "Type CONFIRM to proceed to manual verification"
11. Manual verification: Y/N/Q for each passing contact → mark MANUALLY_REJECTED if N or Q
12. Upload approved contacts to OwnerRez POST /v2/guests
13. Generate final report (Email 2) listing actually uploaded contacts + save local copy
14. Send final report via Gmail SMTP
15. Print console summary
```

---

## Setup Instructions

1. **Copy config template**: `cp src/main/resources/config.properties.template src/main/resources/config.properties`
2. **Fill in credentials** in `config.properties`
3. **Place Mailchimp CSV** at `data/contacts.csv` (use the `subscribed` export, not `cleaned`)
4. **Build and run**: `./run.sh`

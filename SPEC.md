# Upload Mailchimp Contacts to OwnerRez — Specification

## Overview

A Java program that reads a CSV export of Mailchimp subscribers and uploads them to OwnerRez as guests, skipping duplicates and invalid entries, then sends a review report via email before performing any uploads.

---

## Functional Requirements

### 1. Language & Runtime
- Java 17, Maven build, packaged as a fat JAR via `maven-shade-plugin`

### 2. Input
- Read a CSV file of contacts downloaded from Mailchimp
- Each row contains at minimum an email address
- File path is configurable via `config.properties`
- A contact is uniquely identified by its email address

### 3. Duplicate Detection (OwnerRez)
- Before processing, check each email against the OwnerRez API (`GET /v2/guests?q={email}`)
- If the contact already exists in OwnerRez, skip it (mark as `ALREADY_EXISTS`)

### 4. Email Format Validation
- After confirming a contact is not in OwnerRez, validate the email format
- Two-layer validation:
  1. Regex pre-check: `^[^\s@]+@[^\s@]+\.[^\s@]{2,}$`
  2. Apache Commons Validator RFC-compliant check
- Invalid format → mark as `BAD_FORMAT`, do not upload

### 5. Gibberish Detection (HuggingFace)
- For contacts passing format validation, check whether the email local part (before `@`) is gibberish
- Uses HuggingFace Inference API with model `madhurjindal/autonlp-Gibberish-Detector-492513457`
- Classifies text as: `clean`, `mild gibberish`, `word salad`, `noise`
- If top label is `word salad` or `noise` with confidence > 0.85 → mark as `GIBBERISH`, do not upload
- Local parts of 3 characters or fewer are skipped (too short to classify reliably)
- HuggingFace API token is configurable via `config.properties`

### 6. Upload
- Only upload contacts with status `NEW` (not already in OwnerRez, valid format, not gibberish)
- Upload via OwnerRez API (`POST /v2/guests`)
- Upload happens **after** the report is reviewed and confirmed

### 7. Report
The report is an HTML document with:

**Summary section** (top):
- Total contacts in CSV
- Already in OwnerRez (skipped)
- Bad email format (skipped)
- Gibberish emails (skipped)
- Contacts to be uploaded

**Detailed sections**:
- Table: contacts to be uploaded (email, first name, last name, phone)
- Table: skipped contacts with reason (`BAD_FORMAT`, `GIBBERISH`, `ALREADY_EXISTS`) and detail

A local backup copy is saved to `reports/upload_report_{timestamp}.html`

### 8. Email Delivery
- Report is sent via Gmail API (OAuth2 with Google Cloud credentials JSON)
- From: `highfiveretreats.com` account
- To: `highfiveretreats.com` account
- Subject: `Contact Upload Report — {timestamp}`
- Credentials JSON path is configurable

### 9. Report-Before-Upload Workflow
- The report is sent (and saved locally) **before any contact is uploaded**
- After sending the report, the program pauses and prompts: `Type CONFIRM to proceed with upload, or ABORT to cancel`
- Upload only proceeds upon explicit confirmation

### 10. Programmatic Review (Pre-Report)
Before generating the report, an automated review runs and flags:
1. **Duplicate emails within the CSV**: same email appearing more than once
2. **High gibberish rate**: if > 20% of validated contacts are gibberish
3. **All-rejected batch**: if 0 contacts would be uploaded
4. **Suspicious domains**: domains appearing only once across the list (potential typos like `gmial.com`)

Programmatic review flags are included in the report summary section.

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
│   └── contacts.csv                    (gitignored — place your Mailchimp export here)
├── reports/
│   └── upload_report_{timestamp}.html  (gitignored — generated reports)
└── src/main/java/com/highfive/contacts/
    ├── Main.java
    ├── model/
    │   ├── MailchimpContact.java        (data from CSV row)
    │   ├── ContactStatus.java           (NEW / ALREADY_EXISTS / BAD_FORMAT / GIBBERISH)
    │   ├── ValidationResult.java        (contact + status + reason)
    │   └── UploadReport.java            (aggregated results for report)
    ├── csv/
    │   └── MailchimpCsvReader.java      (opencsv, header-aware)
    ├── ownerrez/
    │   └── OwnerRezClient.java          (lookup + create via /v2/guests)
    ├── validation/
    │   ├── EmailFormatValidator.java    (regex + commons-validator)
    │   └── GibberishDetector.java       (HuggingFace inference API)
    ├── review/
    │   └── ProgrammaticReviewer.java    (automated pre-report checks)
    ├── report/
    │   └── ReportGenerator.java         (HTML report generation)
    └── email/
        └── GmailSender.java             (Gmail API OAuth2 sender)
```

### Configuration (`config.properties` — gitignored)
```properties
ownerrez.email=host@highfiveretreats.com
ownerrez.token=at_xxxxxxxxxxxxxxxx
csv.path=data/contacts.csv
huggingface.api.token=hf_xxxxxxxxxxxxxxxx
huggingface.model.id=madhurjindal/autonlp-Gibberish-Detector-492513457
gmail.credentials.json.path=credentials/gmail_credentials.json
gmail.tokens.dir=credentials/tokens
gmail.from=host@highfiveretreats.com
gmail.to=host@highfiveretreats.com
upload.auto.confirm=false
```

### APIs Used
| API | Purpose | Auth |
|-----|---------|------|
| OwnerRez v2 `/guests` | Lookup + create guests | HTTP Basic Auth (email:token) |
| HuggingFace Inference | Gibberish classification | Bearer token |
| Google Gmail API v1 | Send report email | OAuth2 (credentials JSON) |

### Execution Flow
```
1.  Load config
2.  Read Mailchimp CSV → List<MailchimpContact>
3.  Check each email against OwnerRez → mark ALREADY_EXISTS
4.  For remaining: validate email format → mark BAD_FORMAT
5.  For remaining: check HuggingFace gibberish → mark GIBBERISH
6.  Run ProgrammaticReviewer → generate flags
7.  Generate HTML report
8.  Send report via Gmail + save local copy
9.  Prompt: "Type CONFIRM to upload or ABORT to cancel"
10. On CONFIRM: upload all NEW contacts to OwnerRez
11. Print final upload summary to console
```

---

## Setup Instructions

1. **Copy config template**: `cp src/main/resources/config.properties.template src/main/resources/config.properties`
2. **Fill in credentials** in `config.properties`
3. **Place Mailchimp CSV** at `data/contacts.csv` (or configure the path)
4. **Google OAuth setup** (first run only): place your `credentials.json` from Google Cloud Console at the path in config; the program will open a browser for OAuth consent and store the refresh token in `credentials/tokens/`
5. **Build and run**: `./run.sh`

package com.highfive.contacts.validation;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Flags email domains that are technically valid DNS-wise but should not be uploaded:
 *   1. Known disposable / temporary email providers (blocklist)
 *   2. Domains whose name contains suspicious keywords (test, fake, temp, spam, etc.)
 */
public class SuspiciousDomainChecker {

    private static final Pattern SUSPICIOUS_KEYWORD = Pattern.compile(
            "\\b(test|fake|temp|temporary|disposable|spam|trash|junk|dummy|sample|example|invalid|noreply|no-reply|mailnull|notreal|throwaway)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Known disposable / throwaway email providers
    private static final Set<String> BLOCKLIST = Set.of(
            // Generic disposable services
            "mailinator.com", "guerrillamail.com", "guerrillamail.net", "guerrillamail.org",
            "guerrillamail.de", "guerrillamail.biz", "guerrillamail.info",
            "sharklasers.com", "guerrillamailblock.com", "grr.la", "spam4.me",
            "trashmail.com", "trashmail.me", "trashmail.net", "trashmail.at",
            "trashmail.io", "trashmail.org", "trashmail.xyz",
            "yopmail.com", "yopmail.fr", "cool.fr.nf", "jetable.fr.nf", "nospam.ze.tc",
            "nomail.xl.cx", "mega.zik.dj", "speed.1s.fr", "courriel.fr.nf",
            "jetable.com", "dispostable.com", "discard.email",
            "maildrop.cc", "mailnull.com", "mailnesia.com",
            "tempmail.com", "tempmail.net", "tempmail.org", "tempr.email",
            "temp-mail.org", "temp-mail.io", "tempinbox.com",
            "throwam.com", "throwaway.email", "throwam.com",
            "getnada.com", "fakeinbox.com", "fakemailgenerator.com",
            "mailforspam.com", "spamgourmet.com", "spamgourmet.net",
            "spamgourmet.org", "spamfree24.org", "spamfree24.de",
            "spamfree24.info", "spamfree24.biz",
            "mailexpire.com", "spamex.com", "spam.la",
            "binkmail.com", "bobmail.info", "chammy.info", "devnullmail.com",
            "dispostable.com", "emailias.com", "emailsensei.com",
            "fakedemail.com", "fakemail.fr", "filzmail.com",
            "fleckens.hu", "getairmail.com", "girlsundertheinfluence.com",
            "gishpuppy.com", "haltospam.com", "ieatspam.eu", "ieatspam.info",
            "inoutmail.de", "inoutmail.eu", "inoutmail.info", "inoutmail.net",
            "jetable.net", "jetable.org",
            "kasmail.com", "klassmaster.com", "klassmaster.net",
            "kurzepost.de", "letthemeatspam.com", "lhsdv.com",
            "lookugly.com", "lortemail.dk",
            "m21.cc", "mailblocks.com", "mailcatch.com", "maileater.com",
            "mailfreeonline.com", "mailguard.me", "mailimate.com",
            "mailme.lv", "mailme24.com", "mailmetrash.com",
            "mailmoat.com", "mailnew.com", "mailquack.com",
            "mailscrap.com", "mailseal.de", "mailshell.com",
            "mailslapping.com", "mailsiphon.com", "mailslite.com",
            "mailzilla.org", "mbx.cc", "meltmail.com",
            "messagebeamer.de", "mjihrn.com", "moakt.com",
            "mt2009.com", "myspamless.com", "mytempemail.com",
            "nospamfor.us", "nospamthanks.info",
            "objectmail.com", "obobbo.com", "oneoffemail.com",
            "onewaymail.com", "pookmail.com", "proxymail.eu",
            "rcpt.at", "recode.me", "recursor.net", "regbypass.comsafe-mail.net",
            "safetymail.info", "safetypost.de", "shieldemail.com",
            "shortmail.net", "sibmail.com", "skeefmail.com",
            "slapsfromlastnight.com", "slopsbox.com", "smellfear.com",
            "snakemail.com", "sneakemail.com", "sneakmail.de",
            "sofimail.com", "sofort-mail.de", "sogetthis.com",
            "spamevader.com", "spaml.com", "spaml.de", "spamoff.de",
            "spamspot.com", "spamthisplease.com",
            "squizzy.de", "squizzy.eu", "squizzy.net",
            "stinkefinger.net", "supergreatmail.com", "supermailer.jp",
            "suremail.info", "teleworm.com", "teleworm.us",
            "tempalias.com", "tempemailaddress.com", "tempthe.net",
            "tgasa.com", "thanksnospam.info", "thisisnotmyrealemail.com",
            "throwam.com", "tilien.com", "tmail.com", "tmailinator.com",
            "toiea.com", "tradermail.info", "trash-mail.at",
            "trash-mail.com", "trash-mail.de", "trash-mail.io",
            "trashdevil.com", "trashdevil.de", "trashemail.de",
            "trashmail.at", "trashmail.com", "trashmail.io",
            "trashmail.me", "trashmail.net",
            "trashmailer.com", "trashmailer.com", "trbvm.com",
            "turual.com", "tyldd.com", "uggsrock.com",
            "uroid.com", "webemail.me", "weg-werf-email.de",
            "wegwerf-email.at", "wegwerf-email.de", "wegwerf-email.net",
            "wegwerfadresse.de", "wegwerfemail.com", "wegwerfemail.de",
            "wegwerfemail.net", "wegwerfemail.org",
            "wh4f.org", "willhackforfood.biz", "willselfdestruct.com",
            "wmail.cf", "wronghead.com", "wuzupmail.net",
            "xagloo.com", "xemaps.com", "xents.com", "xmaily.com",
            "xoxy.net", "yep.it", "yogamaven.com",
            "yuurok.com", "z1p.biz", "za.com", "zehnminuten.de",
            "zehnminutenmail.de", "zippymail.info", "zoemail.net",
            "zomg.info",
            // Test-specific domains from this dataset
            "testform.xyz", "test.com", "example.com", "example.net",
            "example.org", "invalid.com", "test.test", "fake.com"
    );

    // All IANA timezone IDs that belong to the United States (50 states + territories)
    private static final Set<String> US_TIMEZONES = Set.of(
            // Contiguous US
            "america/new_york", "america/chicago", "america/denver", "america/los_angeles",
            "america/phoenix", "america/detroit", "america/boise",
            // Indiana
            "america/indiana/indianapolis", "america/indiana/knox", "america/indiana/marengo",
            "america/indiana/petersburg", "america/indiana/tell_city", "america/indiana/vevay",
            "america/indiana/vincennes", "america/indiana/winamac",
            // Kentucky
            "america/kentucky/louisville", "america/kentucky/monticello",
            // North Dakota
            "america/north_dakota/beulah", "america/north_dakota/center",
            "america/north_dakota/new_salem",
            // Alaska
            "america/anchorage", "america/juneau", "america/nome", "america/sitka",
            "america/yakutat", "america/metlakatla", "america/adak",
            // Hawaii
            "america/honolulu", "pacific/honolulu",
            // Menominee (Michigan upper peninsula)
            "america/menominee",
            // US territories
            "america/puerto_rico", "america/virgin", "pacific/guam", "pacific/saipan",
            "pacific/pago_pago", "pacific/midway",
            // Legacy US/ aliases
            "us/eastern", "us/central", "us/mountain", "us/pacific",
            "us/alaska", "us/hawaii", "us/arizona", "us/east-indiana",
            "us/indiana-starke", "us/michigan", "us/pacific-new", "us/samoa"
    );

    public record Result(boolean suspicious, String reason) {}

    public Result check(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx < 0) return new Result(false, "");
        String domain = email.substring(atIdx + 1).toLowerCase();

        if (BLOCKLIST.contains(domain)) {
            return new Result(true, "Known disposable/test email provider: " + domain);
        }

        // Strip TLD and check the domain label for suspicious keywords
        String domainLabel = domain.contains(".") ? domain.substring(0, domain.lastIndexOf('.')) : domain;
        if (SUSPICIOUS_KEYWORD.matcher(domainLabel).find()) {
            return new Result(true, "Domain name contains suspicious keyword: " + domain);
        }

        return new Result(false, "");
    }

    // RFC 5321 limits
    private static final int MAX_EMAIL_LENGTH      = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    /**
     * Flags emails that exceed RFC 5321 length limits:
     *   - Total address > 254 characters
     *   - Local part (before @) > 64 characters
     */
    public Result checkLength(String email) {
        if (email == null) return new Result(false, "");
        if (email.length() > MAX_EMAIL_LENGTH) {
            return new Result(true, "Email too long (" + email.length() + " chars, max " + MAX_EMAIL_LENGTH + ")");
        }
        int atIdx = email.indexOf('@');
        if (atIdx > MAX_LOCAL_PART_LENGTH) {
            return new Result(true, "Local part too long (" + atIdx + " chars, max " + MAX_LOCAL_PART_LENGTH + ")");
        }
        return new Result(false, "");
    }

    /**
     * Returns a suspicious result if the timezone is set and is not a US timezone.
     * Contacts with no timezone recorded are not flagged.
     */
    public Result checkTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) return new Result(false, "");
        if (US_TIMEZONES.contains(timezone.toLowerCase())) return new Result(false, "");
        return new Result(true, "Non-US timezone: " + timezone);
    }
}

package com.highfive.contacts.validation;

import org.apache.commons.validator.routines.EmailValidator;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Three-layer email validation:
 * Layer 1: fast regex pre-check.
 * Layer 2: Apache Commons Validator RFC-compliant check.
 * Layer 3: DNS domain check — MX record lookup, with A-record fallback.
 *          Results are cached per domain to avoid redundant lookups.
 */
public class EmailFormatValidator {

    private static final Pattern BASIC_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final EmailValidator COMMONS_VALIDATOR = EmailValidator.getInstance();

    // Cache: domain → valid/invalid (avoid re-querying the same domain)
    private final Map<String, Boolean> domainCache = new HashMap<>();

    public record Result(boolean valid, String reason) {}

    public Result validate(String email) {
        if (email == null || email.isBlank()) {
            return new Result(false, "Empty email");
        }
        if (!BASIC_PATTERN.matcher(email).matches()) {
            return new Result(false, "Failed basic format check (missing @, domain, or TLD)");
        }
        if (!COMMONS_VALIDATOR.isValid(email)) {
            return new Result(false, "Failed RFC email validation");
        }

        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        if (!isDomainValid(domain)) {
            return new Result(false, "Domain does not exist (no MX or A record): " + domain);
        }

        return new Result(true, "");
    }

    /**
     * Returns true if the domain has at least one MX record, or an A record as fallback.
     * Caches results to avoid repeated DNS lookups for the same domain.
     */
    private boolean isDomainValid(String domain) {
        if (domainCache.containsKey(domain)) return domainCache.get(domain);

        boolean valid = hasMxRecord(domain) || hasARecord(domain);
        domainCache.put(domain, valid);
        return valid;
    }

    private static final int DNS_MAX_RETRIES = 3;
    private static final long DNS_RETRY_DELAY_MS = 500;

    private boolean hasMxRecord(String domain) {
        for (int attempt = 1; attempt <= DNS_MAX_RETRIES; attempt++) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                env.put("java.naming.provider.url", "dns:");
                env.put("com.sun.jndi.dns.timeout.initial", "3000");
                env.put("com.sun.jndi.dns.timeout.retries", "1");

                InitialDirContext ctx = new InitialDirContext(env);
                Attribute mx = ctx.getAttributes(domain, new String[]{"MX"}).get("MX");
                ctx.close();
                return mx != null && mx.size() > 0;
            } catch (NamingException e) {
                if (attempt < DNS_MAX_RETRIES) {
                    try { Thread.sleep(DNS_RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        return false;
    }

    private boolean hasARecord(String domain) {
        for (int attempt = 1; attempt <= DNS_MAX_RETRIES; attempt++) {
            try {
                InetAddress.getByName(domain);
                return true;
            } catch (Exception e) {
                if (attempt < DNS_MAX_RETRIES) {
                    try { Thread.sleep(DNS_RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        return false;
    }
}

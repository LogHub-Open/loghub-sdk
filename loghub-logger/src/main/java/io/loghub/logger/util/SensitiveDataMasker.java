package io.loghub.logger.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data in log messages and metadata.
 *
 * <p>This class provides automatic masking for common sensitive patterns like:
 * <ul>
 *   <li>Credit card numbers</li>
 *   <li>Email addresses</li>
 *   <li>CPF/CNPJ (Brazilian documents)</li>
 *   <li>Phone numbers</li>
 *   <li>Common sensitive field names (password, token, secret, etc.)</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * // Mask sensitive patterns in a message
 * String masked = SensitiveDataMasker.mask("User email is john@example.com");
 * // Result: "User email is j***@***.com"
 *
 * // Check if a field name is sensitive
 * boolean isSensitive = SensitiveDataMasker.isSensitiveField("password");
 * // Result: true
 *
 * // Add custom sensitive field names
 * SensitiveDataMasker.addSensitiveField("mySecretField");
 * }</pre>
 */
public final class SensitiveDataMasker {

    private static final String MASK = "***";
    private static final String MASKED_VALUE = "******";

    // Default sensitive field names (lowercase for comparison)
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>();

    static {
        // Authentication
        SENSITIVE_FIELDS.add("password");
        SENSITIVE_FIELDS.add("senha");
        SENSITIVE_FIELDS.add("pwd");
        SENSITIVE_FIELDS.add("pass");
        SENSITIVE_FIELDS.add("secret");
        SENSITIVE_FIELDS.add("token");
        SENSITIVE_FIELDS.add("accesstoken");
        SENSITIVE_FIELDS.add("access_token");
        SENSITIVE_FIELDS.add("refreshtoken");
        SENSITIVE_FIELDS.add("refresh_token");
        SENSITIVE_FIELDS.add("apikey");
        SENSITIVE_FIELDS.add("api_key");
        SENSITIVE_FIELDS.add("api-key");
        SENSITIVE_FIELDS.add("authorization");
        SENSITIVE_FIELDS.add("auth");
        SENSITIVE_FIELDS.add("bearer");
        SENSITIVE_FIELDS.add("credential");
        SENSITIVE_FIELDS.add("credentials");

        // Personal data
        SENSITIVE_FIELDS.add("cpf");
        SENSITIVE_FIELDS.add("cnpj");
        SENSITIVE_FIELDS.add("ssn");
        SENSITIVE_FIELDS.add("rg");

        // Financial
        SENSITIVE_FIELDS.add("cardnumber");
        SENSITIVE_FIELDS.add("card_number");
        SENSITIVE_FIELDS.add("creditcard");
        SENSITIVE_FIELDS.add("credit_card");
        SENSITIVE_FIELDS.add("cvv");
        SENSITIVE_FIELDS.add("cvc");
        SENSITIVE_FIELDS.add("pin");
        SENSITIVE_FIELDS.add("accountnumber");
        SENSITIVE_FIELDS.add("account_number");

        // Keys and certificates
        SENSITIVE_FIELDS.add("privatekey");
        SENSITIVE_FIELDS.add("private_key");
        SENSITIVE_FIELDS.add("publickey");
        SENSITIVE_FIELDS.add("public_key");
        SENSITIVE_FIELDS.add("certificate");
        SENSITIVE_FIELDS.add("cert");
    }

    // Regex patterns for sensitive data
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+)\\.([a-zA-Z]{2,})");

    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");

    private static final Pattern CPF_PATTERN =
            Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

    private static final Pattern CNPJ_PATTERN =
            Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");

    // Requires a separator between the area code and the number, and a mandatory
    // hyphen before the last 4 digits, so plain unformatted digit sequences
    // (order IDs, trace IDs, etc.) are not masked as phone numbers.
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b(?:\\+?\\d{1,3}[- ])?\\(?\\d{2,3}\\)?[- ]\\d{4,5}-\\d{4}\\b");

    private SensitiveDataMasker() {
        // Utility class
    }

    /**
     * Masks sensitive data patterns in the given text.
     *
     * @param text the text to mask
     * @return the text with sensitive data masked
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Mask credit card numbers (show last 4 digits)
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(match -> {
            String card = match.group().replaceAll("[- ]", "");
            return MASK + MASK + MASK + card.substring(card.length() - 4);
        });

        // Mask emails (show first char and domain extension)
        result = EMAIL_PATTERN.matcher(result).replaceAll(match -> {
            String user = match.group(1);
            String domain = match.group(3);
            return user.charAt(0) + MASK + "@" + MASK + "." + domain;
        });

        // Mask CPF (show last 2 digits)
        result = CPF_PATTERN.matcher(result).replaceAll(match -> {
            String cpf = match.group().replaceAll("[.-]", "");
            return MASK + "." + MASK + "." + MASK + "-" + cpf.substring(cpf.length() - 2);
        });

        // Mask CNPJ (show last 2 digits)
        result = CNPJ_PATTERN.matcher(result).replaceAll(match -> {
            String cnpj = match.group().replaceAll("[./-]", "");
            return MASK + "." + MASK + "." + MASK + "/" + MASK + "-" + cnpj.substring(cnpj.length() - 2);
        });

        // Mask phone numbers (show last 4 digits)
        result = PHONE_PATTERN.matcher(result).replaceAll(match -> {
            String phone = match.group().replaceAll("[^0-9]", "");
            if (phone.length() >= 4) {
                return "(" + MASK + ") " + MASK + "-" + phone.substring(phone.length() - 4);
            }
            return MASKED_VALUE;
        });

        return result;
    }

    /**
     * Masks a value if the field name is considered sensitive.
     *
     * @param fieldName the field name to check
     * @param value     the value to potentially mask
     * @return the original value if not sensitive, or masked value if sensitive
     */
    public static String maskIfSensitive(String fieldName, String value) {
        if (fieldName == null || value == null) {
            return value;
        }

        if (isSensitiveField(fieldName)) {
            return maskValue(value);
        }

        return value;
    }

    /**
     * Checks if a field name is considered sensitive.
     *
     * @param fieldName the field name to check
     * @return true if the field is sensitive
     */
    public static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String normalizedName = fieldName.toLowerCase().replaceAll("[_-]", "");

        // Check exact match
        if (SENSITIVE_FIELDS.contains(normalizedName)) {
            return true;
        }

        // Check if field name contains sensitive keywords
        for (String sensitiveField : SENSITIVE_FIELDS) {
            if (normalizedName.contains(sensitiveField)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Masks a value completely, showing only a portion based on length.
     *
     * @param value the value to mask
     * @return the masked value
     */
    public static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int length = value.length();

        if (length <= 4) {
            return MASKED_VALUE;
        } else if (length <= 8) {
            return value.substring(0, 2) + MASK + MASK;
        } else {
            return value.substring(0, 2) + MASK + MASK + value.substring(length - 2);
        }
    }

    /**
     * Adds a custom sensitive field name.
     *
     * @param fieldName the field name to add
     */
    public static void addSensitiveField(String fieldName) {
        if (fieldName != null && !fieldName.isEmpty()) {
            SENSITIVE_FIELDS.add(fieldName.toLowerCase().replaceAll("[_-]", ""));
        }
    }

    /**
     * Removes a field name from the sensitive list.
     *
     * @param fieldName the field name to remove
     */
    public static void removeSensitiveField(String fieldName) {
        if (fieldName != null) {
            SENSITIVE_FIELDS.remove(fieldName.toLowerCase().replaceAll("[_-]", ""));
        }
    }

    /**
     * Clears all custom sensitive fields (resets to defaults).
     */
    public static void resetSensitiveFields() {
        SENSITIVE_FIELDS.clear();
        // Re-add defaults
        SENSITIVE_FIELDS.add("password");
        SENSITIVE_FIELDS.add("senha");
        SENSITIVE_FIELDS.add("pwd");
        SENSITIVE_FIELDS.add("pass");
        SENSITIVE_FIELDS.add("secret");
        SENSITIVE_FIELDS.add("token");
        SENSITIVE_FIELDS.add("accesstoken");
        SENSITIVE_FIELDS.add("access_token");
        SENSITIVE_FIELDS.add("refreshtoken");
        SENSITIVE_FIELDS.add("refresh_token");
        SENSITIVE_FIELDS.add("apikey");
        SENSITIVE_FIELDS.add("api_key");
        SENSITIVE_FIELDS.add("api-key");
        SENSITIVE_FIELDS.add("authorization");
        SENSITIVE_FIELDS.add("auth");
        SENSITIVE_FIELDS.add("bearer");
        SENSITIVE_FIELDS.add("credential");
        SENSITIVE_FIELDS.add("credentials");
        SENSITIVE_FIELDS.add("cpf");
        SENSITIVE_FIELDS.add("cnpj");
        SENSITIVE_FIELDS.add("ssn");
        SENSITIVE_FIELDS.add("rg");
        SENSITIVE_FIELDS.add("cardnumber");
        SENSITIVE_FIELDS.add("card_number");
        SENSITIVE_FIELDS.add("creditcard");
        SENSITIVE_FIELDS.add("credit_card");
        SENSITIVE_FIELDS.add("cvv");
        SENSITIVE_FIELDS.add("cvc");
        SENSITIVE_FIELDS.add("pin");
        SENSITIVE_FIELDS.add("accountnumber");
        SENSITIVE_FIELDS.add("account_number");
        SENSITIVE_FIELDS.add("privatekey");
        SENSITIVE_FIELDS.add("private_key");
        SENSITIVE_FIELDS.add("publickey");
        SENSITIVE_FIELDS.add("public_key");
        SENSITIVE_FIELDS.add("certificate");
        SENSITIVE_FIELDS.add("cert");
    }
}


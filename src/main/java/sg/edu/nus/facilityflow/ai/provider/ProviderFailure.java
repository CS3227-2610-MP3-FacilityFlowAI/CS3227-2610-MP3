package sg.edu.nus.facilityflow.ai.provider;

/** Closed decoder/port failure with no attacker-controlled diagnostic content. */
final class ProviderFailure extends RuntimeException {
    private final Code code;

    ProviderFailure(Code code) {
        super(code.safeMessage());
        this.code = code;
    }

    Code code() {
        return code;
    }

    enum Code {
        BODY_TOO_LARGE("Provider body exceeded its limit."),
        BODY_UNREADABLE("Provider body could not be read."),
        INVALID_UTF8("Provider body encoding was invalid."),
        MALFORMED_JSON("Provider JSON was malformed."),
        INVALID_ENVELOPE("Provider envelope was invalid.");

        private final String safeMessage;

        Code(String safeMessage) {
            this.safeMessage = safeMessage;
        }

        String safeMessage() {
            return safeMessage;
        }
    }
}

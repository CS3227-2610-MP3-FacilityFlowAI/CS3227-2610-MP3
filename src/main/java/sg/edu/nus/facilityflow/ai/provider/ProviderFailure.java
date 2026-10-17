package sg.edu.nus.facilityflow.ai.provider;

import java.time.Duration;
import java.util.Objects;

/** Closed decoder/port failure with no attacker-controlled diagnostic content. */
final class ProviderFailure extends RuntimeException {
    private final Code code;
    private final Duration retryAfter;

    ProviderFailure(Code code) {
        this(code, null);
    }

    ProviderFailure(Code code, Duration retryAfter) {
        super(Objects.requireNonNull(code, "code").safeMessage());
        this.code = code;
        this.retryAfter = retryAfter;
    }

    Code code() {
        return code;
    }

    Duration retryAfter() {
        return retryAfter;
    }

    enum Code {
        BODY_TOO_LARGE("Provider body exceeded its limit."),
        BODY_UNREADABLE("Provider body could not be read."),
        INVALID_UTF8("Provider body encoding was invalid."),
        MALFORMED_JSON("Provider JSON was malformed."),
        INVALID_ENVELOPE("Provider envelope was invalid."),
        HTTP_BAD_REQUEST("Provider rejected the request."),
        HTTP_UNAUTHORIZED("Provider authentication failed."),
        HTTP_FORBIDDEN("Provider authorization failed."),
        HTTP_RATE_LIMITED("Provider rate limit was reached."),
        HTTP_SERVICE_UNAVAILABLE("Provider service was unavailable."),
        HTTP_UNEXPECTED("Provider returned an unexpected status."),
        REDIRECT_REJECTED("Provider redirect was rejected."),
        UNSUPPORTED_CONTENT_ENCODING("Provider response encoding was unsupported."),
        TIMEOUT("Provider request timed out."),
        CANCELLED("Provider request was cancelled."),
        NETWORK_FAILURE("Provider network request failed."),
        REQUEST_BUILD_FAILED("Provider request could not be built.");

        private final String safeMessage;

        Code(String safeMessage) {
            this.safeMessage = safeMessage;
        }

        String safeMessage() {
            return safeMessage;
        }
    }
}

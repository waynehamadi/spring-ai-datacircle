package dev.datacircle.springai;

/**
 * Raised when a Datacircle API request fails.
 */
final class DatacircleException extends RuntimeException {

    /** HTTP status, or -1 when the request received no HTTP response. */
    private final int statusCode;

    DatacircleException(String message) {
        this(-1, message, null);
    }

    DatacircleException(String message, Throwable cause) {
        this(-1, message, cause);
    }

    DatacircleException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    int statusCode() {
        return statusCode;
    }
}

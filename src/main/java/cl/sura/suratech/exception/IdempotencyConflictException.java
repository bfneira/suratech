package cl.sura.suratech.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key reuse with different request payload. key=" + key);
    }
}

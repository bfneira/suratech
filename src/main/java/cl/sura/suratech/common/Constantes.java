package cl.sura.suratech.common;

public class Constantes {
    private Constantes() {}
    public static final String HDR_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HDR_IDEMPOTENCY_STATUS = "Idempotency-Status";
    public static final String HDR_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";
}

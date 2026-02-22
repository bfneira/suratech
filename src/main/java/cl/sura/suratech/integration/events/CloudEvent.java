package cl.sura.suratech.integration.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CloudEvent<T>(
        String specversion,
        String type,
        String source,
        String id,
        OffsetDateTime time,
        String subject,
        String datacontenttype,
        String traceparent,
        T data
) {
    public static <T> CloudEvent<T> v1(
            String type,
            String source,
            String id,
            OffsetDateTime time,
            String subject,
            String datacontenttype,
            String traceparent,
            T data
    ) {
        return new CloudEvent<>(
                "1.0",
                type,
                source,
                id,
                time,
                subject,
                datacontenttype,
                traceparent,
                data
        );
    }
}
package cl.sura.suratech.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "quotes", indexes = {
        @Index(name = "ix_quotes_document_created_desc", columnList = "document_id, created_at")
})
public class QuoteEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "subtotal", nullable = false)
    private double subtotal;

    @Column(name = "tax_total", nullable = false)
    private double taxTotal;

    @Column(name = "grand_total", nullable = false)
    private double grandTotal;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuoteItemEntity> items = new ArrayList<>();
}
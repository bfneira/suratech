package cl.sura.suratech.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "quote_items", indexes = {
        @Index(name = "ix_quote_items_quote_id", columnList = "quote_id")
})
public class QuoteItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private QuoteEntity quote;

    @Column(name = "sku", length = 64, nullable = false)
    private String sku;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "tax_rate", nullable = false)
    private double taxRate;

    @Column(name = "line_total", nullable = false)
    private double lineTotal;

    @Column(name = "tax_amount", nullable = false)
    private double taxAmount;
}

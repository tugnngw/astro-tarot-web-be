package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reading_cards")
public class ReadingCard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private TarotReading reading;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private TarotCard card;

    @Column(nullable = false)
    private Short position;

    @Builder.Default
    @Column(name = "is_reversed")
    private Boolean reversed = false;

    @Column(columnDefinition = "TEXT")
    private String interpretation;
}

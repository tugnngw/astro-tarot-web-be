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
@Table(name = "tarot_cards")
public class TarotCard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "arcana_type", length = 20)
    private String arcanaType;

    @Column(name = "card_number")
    private Integer cardNumber;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;
}

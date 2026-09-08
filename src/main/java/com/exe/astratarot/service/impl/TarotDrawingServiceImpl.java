package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reader.DrawCardsRequest;
import com.exe.astratarot.domain.dto.reader.DrawCardsResponse;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.TarotDrawingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TarotDrawingServiceImpl implements TarotDrawingService {

    private final TarotCardRepository tarotCardRepository;
    private final ReadingCardRepository readingCardRepository;
    private final TarotReadingRepository tarotReadingRepository;

    @Override
    @Transactional
    public DrawCardsResponse drawCardsForReading(DrawCardsRequest request) {
        TarotReading reading = tarotReadingRepository.findById(request.getReadingId())
                .orElseThrow(() -> new EntityNotFoundException("Tarot reading not found with ID: " + request.getReadingId()));

        List<CardDrawDTO> drawnCardsDto = drawCards(request.getReadingId(), request.getNumberOfCards(), request.isIncludeReversed());

        saveReadingCards(reading, drawnCardsDto);

        return DrawCardsResponse.builder()
                .readingId(request.getReadingId())
                .drawnCards(drawnCardsDto)
                .build();
    }

    @Override
    public List<CardDrawDTO> drawCards(UUID readingId, int numberOfCards, boolean includeReversed) {
        List<TarotCard> allCards = tarotCardRepository.findAll();
        if (allCards.size() < numberOfCards) {
            throw new IllegalArgumentException("Not enough unique cards available for the requested number of draws.");
        }

        SecureRandom random = new SecureRandom();
        Set<UUID> drawnCardIds = new HashSet<>();
        List<CardDrawDTO> drawnCardsDto = new ArrayList<>();

        while (drawnCardIds.size() < numberOfCards) {
            int randomIndex = random.nextInt(allCards.size());
            TarotCard selectedCard = allCards.get(randomIndex);

            if (drawnCardIds.add(selectedCard.getId())) {
                boolean isReversed = false;
                if (includeReversed) {
                    isReversed = random.nextBoolean(); // 50/50 chance for reversed
                }
                drawnCardsDto.add(CardDrawDTO.builder()
                        .cardId(selectedCard.getId())
                        .position((short) drawnCardsDto.size())
                        .reversed(isReversed)
                        .build());
            }
        }

        return drawnCardsDto;
    }

    private void saveReadingCards(TarotReading reading, List<CardDrawDTO> drawnCardsDto) {
        List<ReadingCard> readingCardsToSave = new ArrayList<>();
        for (CardDrawDTO cardDto : drawnCardsDto) {
            TarotCard card = tarotCardRepository.findById(cardDto.getCardId())
                    .orElseThrow(() -> new EntityNotFoundException("Tarot card not found with ID: " + cardDto.getCardId()));

            ReadingCard readingCard = ReadingCard.builder()
                    .reading(reading)
                    .card(card)
                    .position(cardDto.getPosition())
                    .reversed(cardDto.getReversed())
                    .build();
            readingCardsToSave.add(readingCard);
        }
        readingCardRepository.saveAll(readingCardsToSave);
    }
}

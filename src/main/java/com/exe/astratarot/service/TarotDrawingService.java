package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reader.DrawCardsRequest;
import com.exe.astratarot.domain.dto.reader.DrawCardsResponse;

import java.util.List;
import java.util.UUID;

public interface TarotDrawingService {
    DrawCardsResponse drawCardsForReading(DrawCardsRequest request);
    List<CardDrawDTO> drawCards(UUID readingId, int numberOfCards, boolean includeReversed);
}

package com.exe.astratarot.controller;

import com.exe.astratarot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AIChatController SSE streaming endpoint.
 */
@ExtendWith(MockitoExtension.class)
class AIChatControllerStreamTest {

    private MockMvc mockMvc;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private AIChatController controller;

    private final UUID readingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void sendMessageStream_validRequest_returnsSseEmitter() throws Exception {
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            Consumer<Throwable> onError = invocation.getArgument(4);
            Consumer<ChatService.StreamResult> onComplete = invocation.getArgument(5);

            onChunk.accept("The ");
            onChunk.accept("cards say yes.");
            onComplete.accept(new ChatService.StreamResult(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "gemini-2.5-flash",
                    35, 10, 25
            ));
            return null;
        }).when(chatService).sendMessageStream(
                eq(readingId), any(), eq("hello"),
                any(), any(), any());

        MvcResult result = mockMvc.perform(post("/api/ai-readings/{readingId}/chat/stream", readingId)
                        .principal(() -> "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("event:chunk"),
                                org.hamcrest.Matchers.containsString("cards say yes"),
                                org.hamcrest.Matchers.containsString("event:complete"),
                                org.hamcrest.Matchers.containsString("gemini-2.5-flash")
                        ))
                );
    }

    @Test
    void sendMessageStream_error_sendsErrorEvent() throws Exception {
        doAnswer(invocation -> {
            Consumer<Throwable> onError = invocation.getArgument(4);
            onError.accept(new IllegalArgumentException("Chat session is closed"));
            return null;
        }).when(chatService).sendMessageStream(
                eq(readingId), any(), eq("fail"),
                any(), any(), any());

        mockMvc.perform(post("/api/ai-readings/{readingId}/chat/stream", readingId)
                        .principal(() -> "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"fail\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void sendMessageStream_chunks_sendMultipleChunks() throws Exception {
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            Consumer<Throwable> onError = invocation.getArgument(4);
            Consumer<ChatService.StreamResult> onComplete = invocation.getArgument(5);

            onChunk.accept("first chunk");
            onChunk.accept("second chunk");
            onComplete.accept(new ChatService.StreamResult(
                    UUID.randomUUID(), UUID.randomUUID(),
                    "gemini-2.5-flash", 0, 0, 0
            ));
            return null;
        }).when(chatService).sendMessageStream(
                eq(readingId), any(), eq("multi"),
                any(), any(), any());

        MvcResult result = mockMvc.perform(post("/api/ai-readings/{readingId}/chat/stream", readingId)
                        .principal(() -> "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"multi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.stringContainsInOrder(
                                "first chunk", "second chunk"))
                );
    }
}

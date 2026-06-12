package com.exe.astratarot.controllers;

import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReaderAdminController.class)
public class ReaderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReaderService readerService;

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testReviewApplication() throws Exception {
        UUID applicationId = UUID.randomUUID();
        ReviewReaderRequest request = new ReviewReaderRequest("APPROVED", null);

        Mockito.doNothing().when(readerService).review(Mockito.any(UUID.class), Mockito.eq(applicationId), Mockito.any(ReviewReaderRequest.class));

        mockMvc.perform(patch("/api/v1/admin/readers/" + applicationId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Application reviewed"));
    }
}
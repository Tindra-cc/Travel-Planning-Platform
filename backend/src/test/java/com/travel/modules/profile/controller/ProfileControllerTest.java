package com.travel.modules.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.modules.profile.dto.ProfileCheckResponse;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.dto.TravelerRequest;
import com.travel.modules.profile.dto.TravelerResponse;
import com.travel.modules.profile.service.ProfileService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ProfileController(profileService));
    }

    @Test
    void getProfileReturnsEnvelope() throws Exception {
        ProfileResponse profile = new ProfileResponse();
        profile.setProfileId(7L);
        profile.setVersion(2);
        when(profileService.getProfile()).thenReturn(profile);

        mvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.profileId").value(7))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void checkReturnsMissingFields() throws Exception {
        when(profileService.check()).thenReturn(
                new ProfileCheckResponse(false, List.of("pace")));

        mvc.perform(post("/api/profile/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.missingFields[0]").value("pace"));
    }

    @Test
    void addTravelerRejectsBlankName() throws Exception {
        mvc.perform(post("/api/profile/travelers").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void addTravelerReturnsCreatedRow() throws Exception {
        when(profileService.addTraveler(any(TravelerRequest.class)))
                .thenReturn(new TravelerResponse(3L, "小明", "id_card", "110", null, null));

        mvc.perform(post("/api/profile/travelers").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("name", "小明"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.travelerId").value(3))
                .andExpect(jsonPath("$.data.name").value("小明"));
    }

    @Test
    void updateTravelerUsesPathId() throws Exception {
        when(profileService.updateTraveler(eq(3L), any(TravelerRequest.class)))
                .thenReturn(new TravelerResponse(3L, "小红", null, null, null, null));

        mvc.perform(put("/api/profile/travelers/3").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("name", "小红"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("小红"));

        verify(profileService).updateTraveler(eq(3L), any(TravelerRequest.class));
    }

    @Test
    void deleteTravelerReturnsSuccessEnvelope() throws Exception {
        mvc.perform(delete("/api/profile/travelers/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(profileService).deleteTraveler(3L);
    }
}

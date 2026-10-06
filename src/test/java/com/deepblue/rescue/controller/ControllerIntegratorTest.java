package com.deepblue.rescue.controller;

import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.GlobalExceptionHandler;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.service.AnimalService;
import com.deepblue.rescue.service.RescueCaseService;
import com.deepblue.rescue.service.TreatmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AnimalController.class,
        RescueCaseController.class,
        TreatmentController.class
})
@Import(GlobalExceptionHandler.class)
class ControllerIntegratorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnimalService animalService;

    @MockitoBean
    private RescueCaseService rescueCaseService;

    @MockitoBean
    private TreatmentService treatmentService;

    @Test
    void shouldRunIntegratorScenarioAcrossControllers() throws Exception {
        RescueCaseResponse rescueCase = createRescueCase(
                RescueStatus.IN_REHABILITATION);
        AnimalResponse animal = new AnimalResponse(
                100L,
                "AN-2026-100",
                "Green Sea Turtle",
                "Chelonia mydas",
                AnimalSex.FEMALE,
                "RES-2026-100",
                RescueStatus.IN_REHABILITATION);
        TreatmentResponse treatment = new TreatmentResponse(
                100L,
                "AN-2026-100",
                "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning of left front flipper injury.");
        when(rescueCaseService.findByCode("RES-2026-100"))
                .thenReturn(rescueCase);
        when(animalService.findByCode("AN-2026-100")).thenReturn(animal);
        when(treatmentService.register(any(CreateTreatmentRequest.class)))
                .thenReturn(treatment);
        when(rescueCaseService.changeStatus(
                eq("RES-2026-100"),
                eq(new ChangeRescueStatusRequest(
                        RescueStatus.READY_FOR_RELEASE))))
                .thenReturn(createRescueCase(RescueStatus.READY_FOR_RELEASE));
        when(rescueCaseService.changeStatus(
                eq("RES-2026-100"),
                eq(new ChangeRescueStatusRequest(RescueStatus.RELEASED))))
                .thenThrow(new BusinessRuleException(
                        "Invalid status transition"));
        when(animalService.findByCode("AN-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"));

        mockMvc.perform(get("/api/rescue-cases/{code}", "RES-2026-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseCode").value("RES-2026-100"));

        mockMvc.perform(get("/api/animals/{code}", "AN-2026-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animalCode").value("AN-2026-100"));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalCode": "AN-2026-100",
                                  "specialistCode": "SPEC-001",
                                  "performedAt": "2026-08-21T09:00:00",
                                  "type": "WOUND_CARE",
                                  "description": "Cleaning of left front flipper injury."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.animalCode").value("AN-2026-100"));

        mockMvc.perform(patch(
                        "/api/rescue-cases/{code}/status", "RES-2026-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"READY_FOR_RELEASE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("READY_FOR_RELEASE"));

        mockMvc.perform(patch(
                        "/api/rescue-cases/{code}/status", "RES-2026-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status")
                        .value("Status is required"));

        mockMvc.perform(patch(
                        "/api/rescue-cases/{code}/status", "RES-2026-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RELEASED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Invalid status transition"));

        mockMvc.perform(get("/api/animals/{code}", "AN-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Animal not found: AN-999"));

        verify(rescueCaseService).findByCode("RES-2026-100");
        verify(animalService).findByCode("AN-2026-100");
        verify(treatmentService).register(any(CreateTreatmentRequest.class));
        verify(rescueCaseService).changeStatus(
                eq("RES-2026-100"),
                eq(new ChangeRescueStatusRequest(
                        RescueStatus.READY_FOR_RELEASE)));
        verify(rescueCaseService).changeStatus(
                eq("RES-2026-100"),
                eq(new ChangeRescueStatusRequest(RescueStatus.RELEASED)));
        verify(animalService).findByCode("AN-999");
    }

    private RescueCaseResponse createRescueCase(RescueStatus status) {
        return new RescueCaseResponse(
                100L,
                "RES-2026-100",
                LocalDate.of(2026, 8, 20),
                "Bahia Concha",
                status,
                "DB-CAR",
                "AN-2026-100");
    }
}
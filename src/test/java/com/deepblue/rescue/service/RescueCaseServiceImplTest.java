package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.RescueCaseMapper;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.service.impl.RescueCaseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RescueCaseServiceImplTest {

    @Mock
    private RescueCaseRepository repository;

    @Mock
    private RescueCaseMapper mapper;

    @InjectMocks
    private RescueCaseServiceImpl service;

    @Test
    void shouldFindRescueCaseByCode() {
	RescueCase rescueCase = createRescueCase(RescueStatus.ADMITTED);
	RescueCaseResponse response = createResponse(RescueStatus.ADMITTED);
	when(repository.findByCaseCode("RES-001"))
		.thenReturn(Optional.of(rescueCase));
	when(mapper.toResponse(rescueCase)).thenReturn(response);

	RescueCaseResponse result = service.findByCode("RES-001");

	assertThat(result).isEqualTo(response);
	verify(repository).findByCaseCode("RES-001");
	verify(mapper).toResponse(rescueCase);
    }

    @Test
    void shouldThrowWhenRescueCaseDoesNotExist() {
	when(repository.findByCaseCode("RES-999"))
		.thenReturn(Optional.empty());

	assertThatThrownBy(() -> service.findByCode("RES-999"))
		.isInstanceOf(ResourceNotFoundException.class)
		.hasMessageContaining("RES-999");

	verify(mapper, never()).toResponse(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldChangeStatusWhenTransitionIsValid() {
	RescueCase rescueCase = createRescueCase(RescueStatus.ADMITTED);
	RescueCaseResponse response = createResponse(RescueStatus.UNDER_EVALUATION);
	when(repository.findByCaseCode("RES-001"))
		.thenReturn(Optional.of(rescueCase));
	when(repository.save(rescueCase)).thenReturn(rescueCase);
	when(mapper.toResponse(rescueCase)).thenReturn(response);

	RescueCaseResponse result = service.changeStatus(
		"RES-001",
		new ChangeRescueStatusRequest(RescueStatus.UNDER_EVALUATION));

	assertThat(rescueCase.getStatus()).isEqualTo(RescueStatus.UNDER_EVALUATION);
	assertThat(result).isEqualTo(response);
	verify(repository).save(rescueCase);
    }

    @Test
    void shouldRejectInvalidTransitionWithoutSaving() {
	RescueCase rescueCase = createRescueCase(RescueStatus.ADMITTED);
	when(repository.findByCaseCode("RES-001"))
		.thenReturn(Optional.of(rescueCase));

	assertThatThrownBy(() -> service.changeStatus(
		"RES-001",
		new ChangeRescueStatusRequest(RescueStatus.READY_FOR_RELEASE)))
		.isInstanceOf(BusinessRuleException.class)
		.hasMessageContaining("Invalid rescue status transition");

	verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFindCasesByStatusAndMapEachResult() {
	RescueCase firstCase = createRescueCase(RescueStatus.IN_REHABILITATION);
	RescueCase secondCase = createRescueCase(RescueStatus.IN_REHABILITATION);
	RescueCaseResponse firstResponse = createResponse(RescueStatus.IN_REHABILITATION);
	RescueCaseResponse secondResponse = createResponse(RescueStatus.IN_REHABILITATION);
	when(repository.findByStatusOrderByRescueDateAsc(
		RescueStatus.IN_REHABILITATION))
		.thenReturn(List.of(firstCase, secondCase));
	when(mapper.toResponse(firstCase)).thenReturn(firstResponse);
	when(mapper.toResponse(secondCase)).thenReturn(secondResponse);

	List<RescueCaseResponse> result = service.findByStatus(
		RescueStatus.IN_REHABILITATION);

	assertThat(result).containsExactly(firstResponse, secondResponse);
	verify(repository).findByStatusOrderByRescueDateAsc(
		RescueStatus.IN_REHABILITATION);
    }

    private RescueCase createRescueCase(RescueStatus status) {
	return new RescueCase(
		"RES-001",
		LocalDate.of(2026, 8, 20),
		"Bahia Concha",
		status);
    }

    private RescueCaseResponse createResponse(RescueStatus status) {
	return new RescueCaseResponse(
		1L,
		"RES-001",
		LocalDate.of(2026, 8, 20),
		"Bahia Concha",
		status,
		"CENTER-001",
		"AN-001");
    }
}
package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.Specialist;
import com.deepblue.rescue.domain.Treatment;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import com.deepblue.rescue.service.impl.TreatmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceImplTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private SpecialistRepository specialistRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private TreatmentMapper mapper;

    @InjectMocks
    private TreatmentServiceImpl service;

    @Test
    void shouldRegisterIntegratorTreatmentForAnimalInRehabilitation() {
	Animal animal = createAnimal(RescueStatus.IN_REHABILITATION);
	Specialist specialist = createSpecialist(true);
	CreateTreatmentRequest request = createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0));
	TreatmentResponse response = createResponse();
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(animal));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.of(specialist));
	when(treatmentRepository.save(any(Treatment.class)))
		.thenAnswer(invocation -> invocation.getArgument(0));
	when(mapper.toResponse(any(Treatment.class))).thenReturn(response);

	TreatmentResponse result = service.register(request);

	assertThat(result).isEqualTo(response);
	ArgumentCaptor<Treatment> treatmentCaptor =
		ArgumentCaptor.forClass(Treatment.class);
	verify(treatmentRepository).save(treatmentCaptor.capture());
	assertThat(treatmentCaptor.getValue().getAnimal()).isSameAs(animal);
	assertThat(treatmentCaptor.getValue().getSpecialist()).isSameAs(specialist);
	assertThat(treatmentCaptor.getValue().getPerformedAt())
		.isEqualTo(request.performedAt());
	assertThat(treatmentCaptor.getValue().getType())
		.isEqualTo(TreatmentType.WOUND_CARE);
    }

    @Test
    void shouldRejectInactiveSpecialistWithoutSaving() {
	Animal animal = createAnimal(RescueStatus.IN_REHABILITATION);
	Specialist inactiveSpecialist = createSpecialist(false);
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(animal));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.of(inactiveSpecialist));

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0))))
		.isInstanceOf(BusinessRuleException.class)
		.hasMessageContaining("inactive specialist");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentForReleasedCaseWithoutSaving() {
	Animal animal = createAnimal(RescueStatus.RELEASED);
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(animal));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.of(createSpecialist(true)));

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0))))
		.isInstanceOf(BusinessRuleException.class)
		.hasMessageContaining("RELEASED");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentForClosedCaseWithoutSaving() {
	Animal animal = createAnimal(RescueStatus.CLOSED);
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(animal));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.of(createSpecialist(true)));

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0))))
		.isInstanceOf(BusinessRuleException.class)
		.hasMessageContaining("CLOSED");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentDatedBeforeRescueWithoutSaving() {
	Animal animal = createAnimal(RescueStatus.IN_REHABILITATION);
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(animal));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.of(createSpecialist(true)));

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 15, 9, 0))))
		.isInstanceOf(BusinessRuleException.class)
		.hasMessageContaining("before the rescue date");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAnimalDoesNotExist() {
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.empty());

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0))))
		.isInstanceOf(ResourceNotFoundException.class)
		.hasMessageContaining("AN-2026-100");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSpecialistDoesNotExist() {
	when(animalRepository.findByAnimalCode("AN-2026-100"))
		.thenReturn(Optional.of(createAnimal(RescueStatus.IN_REHABILITATION)));
	when(specialistRepository.findByProfessionalCode("SPEC-001"))
		.thenReturn(Optional.empty());

	assertThatThrownBy(() -> service.register(createRequest(
		LocalDateTime.of(2026, 8, 21, 9, 0))))
		.isInstanceOf(ResourceNotFoundException.class)
		.hasMessageContaining("SPEC-001");

	verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldFindTreatmentsByAnimalCodeInDateOrder() {
	Treatment firstTreatment = new Treatment(
		LocalDateTime.of(2026, 8, 21, 9, 0),
		TreatmentType.WOUND_CARE,
		"Wound care");
	Treatment secondTreatment = new Treatment(
		LocalDateTime.of(2026, 8, 22, 9, 0),
		TreatmentType.OBSERVATION,
		"Follow-up observation");
	TreatmentResponse firstResponse = createResponse();
	TreatmentResponse secondResponse = createResponse();
	when(treatmentRepository.findByAnimalAnimalCodeOrderByPerformedAtAsc(
		"AN-2026-100"))
		.thenReturn(List.of(firstTreatment, secondTreatment));
	when(mapper.toResponse(firstTreatment)).thenReturn(firstResponse);
	when(mapper.toResponse(secondTreatment)).thenReturn(secondResponse);

	List<TreatmentResponse> result = service.findByAnimalCode("AN-2026-100");

	assertThat(result).containsExactly(firstResponse, secondResponse);
    }

    private Animal createAnimal(RescueStatus status) {
	RescueCase rescueCase = new RescueCase(
		"RES-2026-100",
		LocalDate.of(2026, 8, 20),
		"Bahia Concha",
		status);
	Animal animal = new Animal(
		"AN-2026-100",
		"Green Sea Turtle",
		"Chelonia mydas",
		AnimalSex.FEMALE);
	rescueCase.assignAnimal(animal);
	return animal;
    }

    private Specialist createSpecialist(boolean active) {
	return new Specialist(
		"SPEC-001",
		"Elena",
		"Vargas",
		"elena@deepblue.org",
		active);
    }

    private CreateTreatmentRequest createRequest(LocalDateTime performedAt) {
	return new CreateTreatmentRequest(
		"AN-2026-100",
		"SPEC-001",
		performedAt,
		TreatmentType.WOUND_CARE,
		"Cleaning of left front flipper injury.");
    }

    private TreatmentResponse createResponse() {
	return new TreatmentResponse(
		1L,
		"AN-2026-100",
		"SPEC-001",
		LocalDateTime.of(2026, 8, 21, 9, 0),
		TreatmentType.WOUND_CARE,
		"Cleaning of left front flipper injury.");
    }
}
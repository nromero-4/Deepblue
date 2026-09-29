package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.AnimalMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.service.impl.AnimalServiceImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalServiceImplTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private AnimalMapper mapper;

    @InjectMocks
    private AnimalServiceImpl service;

    @Test
    void shouldFindAnimalByCode() {
        Animal animal = createAnimal(RescueStatus.IN_REHABILITATION);
        AnimalResponse response = createResponse(RescueStatus.IN_REHABILITATION);
        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        AnimalResponse result = service.findByCode("AN-001");

        assertThat(result).isEqualTo(response);
        verify(animalRepository).findByAnimalCode("AN-001");
        verify(mapper).toResponse(animal);
    }

    @Test
    void shouldThrowWhenAnimalDoesNotExist() {
        when(animalRepository.findByAnimalCode("AN-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("AN-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AN-999");
    }

    @Test
    void shouldFindAnimalsInRehabilitation() {
        Animal animal = createAnimal(RescueStatus.IN_REHABILITATION);
        AnimalResponse response = createResponse(RescueStatus.IN_REHABILITATION);
        when(animalRepository.findByRescueCaseStatus(RescueStatus.IN_REHABILITATION))
                .thenReturn(List.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        List<AnimalResponse> result = service.findAnimalsInRehabilitation();

        assertThat(result).containsExactly(response);
        verify(animalRepository)
                .findByRescueCaseStatus(RescueStatus.IN_REHABILITATION);
    }

    @Test
    void shouldAllowTreatmentDuringEvaluation() {
        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(createAnimal(RescueStatus.UNDER_EVALUATION)));

        assertThat(service.canReceiveTreatment("AN-001")).isTrue();
    }

    @Test
    void shouldAllowTreatmentDuringRehabilitation() {
        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(createAnimal(RescueStatus.IN_REHABILITATION)));

        assertThat(service.canReceiveTreatment("AN-001")).isTrue();
    }

    @Test
    void shouldNotAllowTreatmentAfterRelease() {
        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(createAnimal(RescueStatus.RELEASED)));

        assertThat(service.canReceiveTreatment("AN-001")).isFalse();
    }

    private Animal createAnimal(RescueStatus status) {
        RescueCase rescueCase = new RescueCase(
                "RES-001",
                LocalDate.of(2026, 8, 20),
                "Bahia Concha",
                status);
        Animal animal = new Animal(
                "AN-001",
                "Green Sea Turtle",
                "Chelonia mydas",
                AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);
        return animal;
    }

    private AnimalResponse createResponse(RescueStatus status) {
        return new AnimalResponse(
                1L,
                "AN-001",
                "Green Sea Turtle",
                "Chelonia mydas",
                AnimalSex.FEMALE,
                "RES-001",
                status);
    }
}
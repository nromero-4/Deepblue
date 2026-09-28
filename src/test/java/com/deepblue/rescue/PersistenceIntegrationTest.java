package com.deepblue.rescue;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.Expertise;
import com.deepblue.rescue.domain.MedicalRecord;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueCenter;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.Specialist;
import com.deepblue.rescue.domain.Treatment;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.ExpertiseRepository;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.repository.RescueCenterRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RescueCenterRepository rescueCenterRepository;

	@Autowired
	private RescueCaseRepository rescueCaseRepository;

	@Autowired
	private AnimalRepository animalRepository;

	@Autowired
	private SpecialistRepository specialistRepository;

	@Autowired
	private ExpertiseRepository expertiseRepository;

	@Autowired
	private TreatmentRepository treatmentRepository;

	@Test
	void flywayExecutesInitialMigrations() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where version in ('1', '2', '3')",
				Integer.class);

		assertThat(migrationCount).isEqualTo(3);
	}

	@Test
	void persistsOptionalTrackingDeviceCode() {
		RescueCenter center = new RescueCenter("DB-GPS", "Tracking Center", "Santa Marta");
		RescueCase rescueCase = new RescueCase("RES-GPS-1", LocalDate.of(2026, 8, 18),
				"Bahia Concha", RescueStatus.READY_FOR_RELEASE);
		Animal animal = new Animal("AN-GPS-1", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);
		animal.setTrackingDeviceCode("GPS-001");
		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);

		rescueCenterRepository.saveAndFlush(center);

		Animal savedAnimal = animalRepository.findByAnimalCode("AN-GPS-1").orElseThrow();
		assertThat(savedAnimal.getTrackingDeviceCode()).isEqualTo("GPS-001");
	}

	@Test
	void completesIntegratorScenarioAndQueries() {
		RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
		RescueCase rescueCase = new RescueCase("RES-2026-100",
				LocalDate.of(2026, 8, 18), "Bahia Concha",
				RescueStatus.IN_REHABILITATION);
		Animal animal = new Animal("AN-2026-100", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);
		MedicalRecord medicalRecord = new MedicalRecord(new BigDecimal("27.80"),
				"STABLE", "Injury caused by fishing net",
				"Possible plastic ingestion");

		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);
		animal.assignMedicalRecord(medicalRecord);
		rescueCenterRepository.saveAndFlush(center);

		Expertise marineReptiles = expertiseRepository
				.findByNameIgnoreCase("Marine Reptiles").orElseThrow();
		Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
		Expertise rehabilitation = expertiseRepository
				.findByNameIgnoreCase("Rehabilitation").orElseThrow();
		Specialist specialist = new Specialist("SPEC-001", "Elena", "Vargas",
				"elena@deepblue.org", true);
		specialist.addExpertise(marineReptiles);
		specialist.addExpertise(trauma);
		specialist.addExpertise(rehabilitation);
		specialistRepository.saveAndFlush(specialist);

		Treatment woundCare = new Treatment(LocalDateTime.of(2026, 8, 18, 10, 0),
				TreatmentType.WOUND_CARE, "Cleaning of left front flipper");
		woundCare.setAnimal(animal);
		woundCare.setSpecialist(specialist);
		Treatment hydration = new Treatment(LocalDateTime.of(2026, 8, 19, 10, 0),
				TreatmentType.HYDRATION, "Subcutaneous fluid therapy");
		hydration.setAnimal(animal);
		hydration.setSpecialist(specialist);
		treatmentRepository.saveAll(List.of(woundCare, hydration));

		assertThat(rescueCaseRepository.findByCaseCode("RES-2026-100")).isPresent();
		assertThat(rescueCaseRepository
				.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION))
				.extracting(RescueCase::getCaseCode)
				.contains("RES-2026-100");
		assertThat(animalRepository.findByRescueCaseRescueCenterCode("DB-CAR"))
				.extracting(Animal::getAnimalCode)
				.containsExactly("AN-2026-100");
		assertThat(animalRepository.findByCommonNameContainingIgnoreCase("turtle"))
				.extracting(Animal::getAnimalCode)
				.containsExactly("AN-2026-100");
		assertThat(specialistRepository.findActiveByExpertise("TRAUMA"))
				.extracting(Specialist::getProfessionalCode)
				.containsExactly("SPEC-001");
		assertThat(treatmentRepository.findByAnimalIdOrderByPerformedAtAsc(animal.getId()))
				.extracting(Treatment::getType)
				.containsExactly(TreatmentType.WOUND_CARE, TreatmentType.HYDRATION);
		assertThat(treatmentRepository.findBySpecialistExpertise("rehabilitation"))
				.extracting(Treatment::getType)
				.containsExactly(TreatmentType.WOUND_CARE, TreatmentType.HYDRATION);
		assertThat(treatmentRepository.findPerformedBetween(
				LocalDateTime.of(2026, 8, 18, 0, 0),
				LocalDateTime.of(2026, 8, 18, 23, 59)))
				.extracting(Treatment::getType)
				.containsExactly(TreatmentType.WOUND_CARE);
	}

	@Test
	void findsAnimalsInRehabilitationTreatedByTraumaSpecialists() {
		Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
		Expertise rehabilitation = expertiseRepository
				.findByNameIgnoreCase("Rehabilitation").orElseThrow();

		Specialist traumaSpecialist = new Specialist("SPEC-CH-1", "Elena", "Vargas",
				"challenge.elena@deepblue.org", true);
		traumaSpecialist.addExpertise(trauma);
		Specialist rehabilitationSpecialist = new Specialist("SPEC-CH-2", "Mateo", "Ruiz",
				"challenge.mateo@deepblue.org", true);
		rehabilitationSpecialist.addExpertise(rehabilitation);
		specialistRepository.saveAll(List.of(traumaSpecialist, rehabilitationSpecialist));

		Animal eligibleAnimal = createAnimalForChallenge("AN-CH-1", "RES-CH-1",
				RescueStatus.IN_REHABILITATION);
		Treatment eligibleTreatment = new Treatment(LocalDateTime.of(2026, 8, 21, 10, 0),
				TreatmentType.WOUND_CARE, "Trauma treatment");
		eligibleTreatment.setAnimal(eligibleAnimal);
		eligibleTreatment.setSpecialist(traumaSpecialist);
		treatmentRepository.save(eligibleTreatment);

		Animal wrongStatusAnimal = createAnimalForChallenge("AN-CH-2", "RES-CH-2",
				RescueStatus.RELEASED);
		Treatment wrongStatusTreatment = new Treatment(LocalDateTime.of(2026, 8, 21, 11, 0),
				TreatmentType.WOUND_CARE, "Trauma treatment");
		wrongStatusTreatment.setAnimal(wrongStatusAnimal);
		wrongStatusTreatment.setSpecialist(traumaSpecialist);
		treatmentRepository.save(wrongStatusTreatment);

		Animal wrongExpertiseAnimal = createAnimalForChallenge("AN-CH-3", "RES-CH-3",
				RescueStatus.IN_REHABILITATION);
		Treatment wrongExpertiseTreatment = new Treatment(LocalDateTime.of(2026, 8, 21, 12, 0),
				TreatmentType.HYDRATION, "Rehabilitation treatment");
		wrongExpertiseTreatment.setAnimal(wrongExpertiseAnimal);
		wrongExpertiseTreatment.setSpecialist(rehabilitationSpecialist);
		treatmentRepository.save(wrongExpertiseTreatment);

		assertThat(animalRepository.findInStatusTreatedByExpertise(
				RescueStatus.IN_REHABILITATION, "trauma"))
				.extracting(Animal::getAnimalCode)
				.containsExactly("AN-CH-1");
	}

	private Animal createAnimalForChallenge(String animalCode, String caseCode,
			RescueStatus status) {
		RescueCenter center = new RescueCenter("CENTER-" + animalCode,
				"Challenge Center", "Santa Marta");
		RescueCase rescueCase = new RescueCase(caseCode, LocalDate.of(2026, 8, 20),
				"Challenge location", status);
		Animal animal = new Animal(animalCode, "Challenge animal",
				"Testus marinus", AnimalSex.UNKNOWN);
		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);
		rescueCenterRepository.saveAndFlush(center);
		return animal;
	}

	@Test
	void inheritedRepositoryMethodsWork() {
		RescueCenter center = rescueCenterRepository.save(
				new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta"));

		assertThat(center.getId()).isNotNull();
		assertThat(rescueCenterRepository.findById(center.getId())).isPresent();
		assertThat(rescueCenterRepository.existsById(center.getId())).isTrue();
		assertThat(rescueCenterRepository.count()).isEqualTo(1);
	}

	@Test
	void persistsOneCenterWithTwoCases() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		center.addCase(new RescueCase("RES-001", LocalDate.of(2026, 8, 1),
				"Bahia Concha", RescueStatus.ADMITTED));
		center.addCase(new RescueCase("RES-002", LocalDate.of(2026, 8, 2),
				"Taganga", RescueStatus.UNDER_EVALUATION));

		rescueCenterRepository.save(center);

		assertThat(rescueCaseRepository.findByRescueCenterCode("DB-CAR")).hasSize(2);
	}

	@Test
	void persistsRescueCaseAndAnimalOnBothSides() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		RescueCase rescueCase = new RescueCase("RES-2026-001",
				LocalDate.of(2026, 8, 1), "Bahia Concha", RescueStatus.ADMITTED);
		Animal animal = new Animal("AN-2026-001", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);

		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);
		rescueCenterRepository.save(center);

		assertThat(rescueCase.getId()).isNotNull();
		assertThat(animal.getId()).isNotNull();
		assertThat(rescueCase.getAnimal()).isSameAs(animal);
		assertThat(animal.getRescueCase()).isSameAs(rescueCase);
	}

	@Test
	void cascadesMedicalRecordFromAnimal() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		RescueCase rescueCase = new RescueCase("RES-2026-002",
				LocalDate.of(2026, 8, 2), "Taganga", RescueStatus.IN_REHABILITATION);
		Animal animal = new Animal("AN-2026-002", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);
		MedicalRecord medicalRecord = new MedicalRecord(new BigDecimal("28.40"),
				"STABLE", "Left front flipper injury", null);

		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);
		animal.assignMedicalRecord(medicalRecord);
		rescueCenterRepository.save(center);

		assertThat(animal.getId()).isNotNull();
		assertThat(medicalRecord.getId()).isNotNull();
		assertThat(medicalRecord.getAnimal()).isSameAs(animal);
	}

	@Test
	void persistsManyToManySpecialistExpertise() {
		Expertise trauma = expertiseRepository.findByNameIgnoreCase("trauma").orElseThrow();
		Expertise rehabilitation = expertiseRepository
				.findByNameIgnoreCase("REHABILITATION").orElseThrow();
		Specialist specialist = new Specialist("SPEC-001", "Elena", "Vargas",
				"elena@deepblue.org", true);

		specialist.addExpertise(trauma);
		specialist.addExpertise(rehabilitation);
		specialistRepository.save(specialist);

		Specialist saved = specialistRepository.findById(specialist.getId()).orElseThrow();
		assertThat(saved.getExpertiseAreas()).hasSize(2);
	}

	@Test
	void findsCasesByStatusWithQueryMethod() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		center.addCase(new RescueCase("RES-101", LocalDate.of(2026, 8, 1),
				"Location A", RescueStatus.IN_REHABILITATION));
		center.addCase(new RescueCase("RES-102", LocalDate.of(2026, 8, 2),
				"Location B", RescueStatus.READY_FOR_RELEASE));
		center.addCase(new RescueCase("RES-103", LocalDate.of(2026, 8, 3),
				"Location C", RescueStatus.IN_REHABILITATION));
		rescueCenterRepository.save(center);

		assertThat(rescueCaseRepository
				.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION))
				.hasSize(2);
	}

	@Test
	void findsAnimalsByCenterWithQueryMethod() {
		RescueCenter caribbean = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		RescueCase caribbeanCase = new RescueCase("RES-CAR", LocalDate.of(2026, 8, 1),
				"Location A", RescueStatus.IN_REHABILITATION);
		caribbean.addCase(caribbeanCase);
		caribbeanCase.assignAnimal(new Animal("AN-CAR", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE));

		RescueCenter pacific = new RescueCenter("DB-PAC", "Pacific Center", "Buenaventura");
		RescueCase pacificCase = new RescueCase("RES-PAC", LocalDate.of(2026, 8, 2),
				"Location B", RescueStatus.IN_REHABILITATION);
		pacific.addCase(pacificCase);
		pacificCase.assignAnimal(new Animal("AN-PAC", "Sea Lion",
				"Zalophus californianus", AnimalSex.UNKNOWN));

		rescueCenterRepository.saveAll(List.of(caribbean, pacific));

		List<Animal> animals = animalRepository.findByRescueCaseRescueCenterCode("DB-CAR");
		assertThat(animals).extracting(Animal::getAnimalCode).containsExactly("AN-CAR");
	}

	@Test
	void findsActiveSpecialistsByExpertiseWithJpql() {
		Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
		Expertise mammals = expertiseRepository
				.findByNameIgnoreCase("Marine Mammals").orElseThrow();
		Specialist elena = new Specialist("SPEC-101", "Elena", "Vargas",
				"elena101@deepblue.org", true);
		elena.addExpertise(trauma);
		Specialist mateo = new Specialist("SPEC-102", "Mateo", "Ruiz",
				"mateo102@deepblue.org", true);
		mateo.addExpertise(mammals);
		Specialist sofia = new Specialist("SPEC-103", "Sofia", "Diaz",
				"sofia103@deepblue.org", true);
		sofia.addExpertise(trauma);
		Specialist inactive = new Specialist("SPEC-104", "Inactive", "Expert",
				"inactive104@deepblue.org", false);
		inactive.addExpertise(trauma);

		specialistRepository.saveAll(List.of(elena, mateo, sofia, inactive));

		assertThat(specialistRepository.findActiveByExpertise("TRAUMA"))
				.extracting(Specialist::getProfessionalCode)
				.containsExactly("SPEC-103", "SPEC-101");
	}

	@Test
	void findsTreatmentsForAnimalInChronologicalOrder() {
		TestData data = saveTreatmentData();

		List<Treatment> treatments = treatmentRepository
				.findByAnimalIdOrderByPerformedAtAsc(data.animal().getId());

		assertThat(treatments).extracting(Treatment::getType)
				.containsExactly(TreatmentType.WOUND_CARE, TreatmentType.HYDRATION,
						TreatmentType.OBSERVATION);
	}

	@Test
	void findsTreatmentsBetweenDatesWithJpql() {
		saveTreatmentData();

		List<Treatment> treatments = treatmentRepository.findPerformedBetween(
				LocalDateTime.of(2026, 8, 5, 0, 0),
				LocalDateTime.of(2026, 8, 15, 23, 59));

		assertThat(treatments).hasSize(1);
		assertThat(treatments.get(0).getPerformedAt())
				.isEqualTo(LocalDateTime.of(2026, 8, 10, 10, 0));
	}

	@Test
	void rejectsDuplicateAnimalCode() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		RescueCase firstCase = new RescueCase("RES-UNIQUE-1", LocalDate.of(2026, 8, 1),
				"Location A", RescueStatus.ADMITTED);
		Animal firstAnimal = new Animal("AN-100", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);
		center.addCase(firstCase);
		firstCase.assignAnimal(firstAnimal);
		rescueCenterRepository.saveAndFlush(center);

		RescueCase secondCase = new RescueCase("RES-UNIQUE-2", LocalDate.of(2026, 8, 2),
				"Location B", RescueStatus.ADMITTED);
		center.addCase(secondCase);
		rescueCaseRepository.saveAndFlush(secondCase);
		Animal duplicateAnimal = new Animal("AN-100", "Sea Lion",
				"Zalophus californianus", AnimalSex.UNKNOWN);
		secondCase.assignAnimal(duplicateAnimal);

		assertThatThrownBy(() -> animalRepository.saveAndFlush(duplicateAnimal))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsInvalidRescueCenterForeignKey() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into rescue_cases
				(case_code, rescue_date, rescue_location, status, rescue_center_id)
				values ('RES-FK-1', DATE '2026-08-01', 'Unknown', 'ADMITTED', -1)
				"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsInvalidRescueStatusWithCheckConstraint() {
		RescueCenter center = rescueCenterRepository.saveAndFlush(
				new RescueCenter("DB-CHECK", "Check Center", "Santa Marta"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into rescue_cases
				(case_code, rescue_date, rescue_location, status, rescue_center_id)
				values ('RES-CHECK-1', DATE '2026-08-01', 'Unknown', 'INVALID_STATUS', ?)
				""", center.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private TestData saveTreatmentData() {
		RescueCenter center = new RescueCenter("DB-CAR", "Caribbean Center", "Santa Marta");
		RescueCase rescueCase = new RescueCase("RES-TREAT", LocalDate.of(2026, 8, 1),
				"Bahia Concha", RescueStatus.IN_REHABILITATION);
		Animal animal = new Animal("AN-TREAT", "Green Sea Turtle",
				"Chelonia mydas", AnimalSex.FEMALE);
		center.addCase(rescueCase);
		rescueCase.assignAnimal(animal);
		rescueCenterRepository.save(center);

		Specialist elena = specialistRepository.save(new Specialist("SPEC-T1", "Elena",
				"Vargas", "elena.treatment@deepblue.org", true));
		Specialist mateo = specialistRepository.save(new Specialist("SPEC-T2", "Mateo",
				"Ruiz", "mateo.treatment@deepblue.org", true));

		Treatment woundCare = new Treatment(LocalDateTime.of(2026, 8, 1, 10, 0),
				TreatmentType.WOUND_CARE, "Cleaning of wound");
		woundCare.setAnimal(animal);
		woundCare.setSpecialist(elena);
		Treatment hydration = new Treatment(LocalDateTime.of(2026, 8, 10, 10, 0),
				TreatmentType.HYDRATION, "Fluid therapy");
		hydration.setAnimal(animal);
		hydration.setSpecialist(elena);
		Treatment observation = new Treatment(LocalDateTime.of(2026, 8, 20, 10, 0),
				TreatmentType.OBSERVATION, "Routine observation");
		observation.setAnimal(animal);
		observation.setSpecialist(mateo);

		treatmentRepository.saveAll(List.of(woundCare, hydration, observation));
		return new TestData(animal, elena, mateo);
	}

	private record TestData(Animal animal, Specialist elena, Specialist mateo) {
	}
}

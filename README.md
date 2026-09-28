# DeepBlue Rescue

## Descripcion

DeepBlue Rescue es una aplicacion de persistencia para centros de rescate y rehabilitacion de fauna marina. El proyecto implementa el modelo relacional y su mapeo con JPA/Hibernate usando Spring Data JPA, Flyway y PostgreSQL.

El alcance se limita a la capa de persistencia. No incluye controladores REST, servicios, DTO, seguridad, frontend, Kafka ni Docker Compose.

## Tecnologias

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA
- Hibernate ORM
- PostgreSQL
- Flyway
- JUnit 5
- Testcontainers
- Maven

## Modelo de datos

```mermaid
erDiagram
    RESCUE_CENTER ||--o{ RESCUE_CASE : manages
    RESCUE_CASE ||--|| ANIMAL : involves
    ANIMAL ||--|| MEDICAL_RECORD : has
    SPECIALIST }o--o{ EXPERTISE : possesses
    ANIMAL ||--o{ TREATMENT : receives
    SPECIALIST ||--o{ TREATMENT : performs

    RESCUE_CENTER {
        bigint id PK
        varchar code UK
        varchar name
        varchar city
    }
    RESCUE_CASE {
        bigint id PK
        varchar case_code UK
        date rescue_date
        varchar rescue_location
        varchar status
        bigint rescue_center_id FK
    }
    ANIMAL {
        bigint id PK
        varchar animal_code UK
        varchar common_name
        varchar scientific_name
        varchar sex
        varchar tracking_device_code UK
        bigint rescue_case_id FK UK
    }
    MEDICAL_RECORD {
        bigint id PK
        bigint animal_id FK UK
        decimal initial_weight
        varchar initial_condition
        text injuries
        text observations
    }
    SPECIALIST {
        bigint id PK
        varchar professional_code UK
        varchar first_name
        varchar last_name
        varchar email UK
        boolean active
    }
    EXPERTISE {
        bigint id PK
        varchar name UK
    }
    TREATMENT {
        bigint id PK
        bigint animal_id FK
        bigint specialist_id FK
        timestamp performed_at
        varchar type
        text description
    }
```

## Relaciones

- `RescueCenter 1:N RescueCase`: la FK `rescue_center_id` esta en `rescue_cases`.
- `RescueCase 1:1 Animal`: la FK `rescue_case_id` esta en `animals` y es `UNIQUE`.
- `Animal 1:1 MedicalRecord`: la FK `animal_id` esta en `medical_records` y es `UNIQUE`.
- `Specialist N:M Expertise`: se implementa con la tabla intermedia `specialist_expertise` y una PK compuesta.
- `Animal 1:N Treatment`: `treatments.animal_id` es una FK obligatoria.
- `Specialist 1:N Treatment`: `treatments.specialist_id` es una FK obligatoria.

Las entidades mantienen sus dos lados sincronizados mediante metodos como `addCase`, `assignAnimal`, `assignMedicalRecord`, `addExpertise` y `addTreatment`.

## Estructura principal

```text
src/main/java/com/deepblue/rescue
  DeepblueRescueApplication.java
  domain/
  repository/

src/main/resources
  application.yml
  db/migration/
    V1__create_schema.sql
    V2__insert_expertise_catalog.sql
    V3__add_tracking_device_to_animal.sql

src/test/java/com/deepblue/rescue
  PersistenceIntegrationTest.java
  TestcontainersConfiguration.java
```

## Configuracion

La aplicacion utiliza variables de entorno con valores predeterminados para una base local:

```yaml
DB_URL=jdbc:postgresql://localhost:5432/deepblue
DB_USER=postgres
DB_PASSWORD=postgres
```

Hibernate esta configurado con:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Hibernate no crea ni modifica tablas. Solo valida que las entidades coincidan con el esquema que Flyway ya creo.

## Migraciones Flyway

- `V1__create_schema.sql`: crea las ocho tablas, PK, FK, restricciones `UNIQUE`, `NOT NULL`, `CHECK` e indices.
- `V2__insert_expertise_catalog.sql`: inserta `Marine Reptiles`, `Marine Mammals`, `Marine Birds`, `Trauma`, `Rehabilitation` y `Toxicology`.
- `V3__add_tracking_device_to_animal.sql`: agrega `animals.tracking_device_code` como columna nullable y unica cuando tiene valor.

Las migraciones son ejecutadas automaticamente al iniciar Spring Boot y se registran en `flyway_schema_history`.

## Ejecucion

Requisitos:

- JDK 21 configurado en `JAVA_HOME`.
- Docker Desktop iniciado para las pruebas de integracion.
- PostgreSQL local disponible si se inicia la aplicacion fuera de los tests.

Para compilar:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd clean compile
```

Para iniciar la aplicacion:

```powershell
.\mvnw.cmd spring-boot:run
```

## Pruebas de integracion

Las pruebas usan PostgreSQL real en un contenedor `postgres:18-alpine`; no usan H2. La conexion es inyectada mediante `@ServiceConnection`.

Ejecutar todas las pruebas:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd clean test
```

Ejecutar solamente el test integrador:

```powershell
.\mvnw.cmd -Dtest=PersistenceIntegrationTest test
```

## Query Methods implementados

### `RescueCenterRepository`

```java
Optional<RescueCenter> findByCode(String code);
```

### `RescueCaseRepository`

```java
Optional<RescueCase> findByCaseCode(String caseCode);
List<RescueCase> findByStatusOrderByRescueDateAsc(RescueStatus status);
List<RescueCase> findByRescueCenterCode(String centerCode);
List<RescueCase> findByRescueDateAfterOrderByRescueDateDesc(LocalDate date);
```

### `AnimalRepository`

```java
Optional<Animal> findByAnimalCode(String animalCode);
List<Animal> findByCommonNameContainingIgnoreCase(String text);
List<Animal> findByRescueCaseStatus(RescueStatus status);
List<Animal> findByRescueCaseRescueCenterCode(String centerCode);
```

### `ExpertiseRepository`

```java
Optional<Expertise> findByNameIgnoreCase(String name);
```

### `TreatmentRepository`

```java
List<Treatment> findByAnimalIdOrderByPerformedAtAsc(Long animalId);
```

## Consultas JPQL implementadas

### Especialistas activos por expertise

`SpecialistRepository.findActiveByExpertise` usa `JOIN`, `LOWER`, parametro nombrado, `active = true`, `DISTINCT` y orden por apellido y nombre.

### Tratamientos por intervalo

`TreatmentRepository.findPerformedBetween` obtiene tratamientos entre `start` y `end`, ordenados por `performedAt` ascendente.

### Tratamientos de un centro

`TreatmentRepository.findByRescueCenterCode` navega desde `Treatment` hasta `Animal`, `RescueCase` y `RescueCenter`.

### Tratamientos por expertise del especialista

`TreatmentRepository.findBySpecialistExpertise` navega desde `Treatment` hasta `Specialist` y `Expertise`, usando `LOWER` y `DISTINCT`.

### Reto sin guia

`AnimalRepository.findInStatusTreatedByExpertise` obtiene animales que:

- tienen el estado recibido en `RescueStatus`;
- tienen al menos un tratamiento;
- fueron tratados por un especialista con la expertise indicada;
- comparan la expertise ignorando mayusculas;
- no se repiten gracias a `DISTINCT`.

## Pruebas realizadas

`PersistenceIntegrationTest` cubre:

- ejecucion de Flyway V1, V2 y V3;
- metodos heredados `save`, `saveAndFlush`, `findById`, `existsById`, `count` y `saveAll`;
- relaciones 1:N, 1:1 y N:M;
- cascade del expediente medico;
- Query Methods por codigo, estado, centro, texto y fecha;
- consultas JPQL de especialistas y tratamientos;
- restriccion `UNIQUE` para codigos de animal;
- restriccion FK para centros inexistentes;
- restriccion `CHECK` para estados invalidos;
- reto integrador de la tortuga marina;
- reto sin guia de animales en rehabilitacion tratados por especialistas con `Trauma`.

## Checklist

- [x] Java 21 y Spring Boot 4.1.1
- [x] Maven, PostgreSQL, Flyway y Hibernate
- [x] `ddl-auto: validate`
- [x] Migraciones V1, V2 y V3
- [x] Siete entidades JPA y tres enums
- [x] Relaciones 1:N, 1:1, N:M y Treatment N:1
- [x] PK, FK, UNIQUE, NOT NULL y CHECK
- [x] Tabla asociativa `specialist_expertise`
- [x] Siete repositories `JpaRepository`
- [x] Query Methods simples y navegando asociaciones
- [x] Consultas personalizadas con JPQL
- [x] `JOIN`, `LOWER`, parametros nombrados y `DISTINCT`
- [x] Testcontainers con PostgreSQL 18
- [x] `@ServiceConnection`
- [x] Tests de Flyway, relaciones, consultas y constraints
- [x] Reto integrador
- [x] Reto sin guia

## Resultado de validacion

Las pruebas de V3 y el reto integrador se ejecutaron correctamente contra PostgreSQL 18.6 mediante Testcontainers. El reto sin guia tambien fue validado con resultado exitoso:

```text
Tests run: 1, Failures: 0, Errors: 0
```

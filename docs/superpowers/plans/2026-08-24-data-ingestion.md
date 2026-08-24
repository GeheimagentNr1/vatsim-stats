# Datenaufzeichnung (Ingestion & Phasenerkennung) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot application that polls the VATSIM v3 data feed every 15 seconds, persists raw pilot/ATC positions, and derives pilot flight sessions with airport takeoff/landing/touch-and-go events, backed by a daily OurAirports reference-data import — with restart-safe state, email alerting on prolonged failures, and a minimal internal Vaadin view for manual verification.

**Architecture:** Maven multi-module Spring Boot app (Java 21) with five modules: `reference-data` (OurAirports import, Airport/Runway JPA), `detection` (pure-Java phase state machine, no Spring dependency), `ingestion` (VATSIM poller, raw + derived JPA entities, session orchestration wiring `detection` to persistence), `monitoring` (HealthMonitor + email alerting), and `app` (Spring Boot entry point, Flyway migrations, Vaadin verification UI). PostgreSQL + TimescaleDB (via the project's `docker-compose.yml`) is the persistence layer; raw data is always written before any derived interpretation, per the spec's restart-robustness requirement.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA, Flyway, PostgreSQL/TimescaleDB, Vaadin 25.1.4, Lombok, Apache Commons CSV, JUnit 5 + Testcontainers, Spring's `RestClient`.

**Spec:** `docs/superpowers/specs/2026-08-24-data-ingestion-design.md`

## Global Constraints

- Java 21, Spring Boot `4.0.6`, Vaadin `25.1.4`, Lombok `1.18.46` — match versions used in the reference project `vatsim-tools`.
- Poll interval: 15 seconds against `https://data.vatsim.net/v3/vatsim-data.json`.
- Ground detection thresholds (configurable, defaults per spec): groundspeed `< 40kt`, altitude `≤ airport elevation + 200ft`, nearest-airport search radius `5nm`, ground dwell threshold `90s`.
- Session key: CID + Callsign + `logon_time` (never Callsign alone).
- Raw data (`pilot_track_point`, `atc_snapshot`) is always persisted before any derived/interpreted data — never skip raw persistence to save a derived write.
- No runway-level detection, no UI beyond the internal Vaadin verification views, no VATSIM Core API/OAuth — out of scope per spec.
- Health/alert email fires once per failure episode (no repeat spam), on: `vatsim-poll` success older than 5 minutes, or the daily `ourairports-import` failing/not running.

---

## Task 1: Project Scaffolding — Maven Multi-Module Skeleton

**Files:**
- Create: `pom.xml` (parent)
- Create: `reference-data/pom.xml`
- Create: `detection/pom.xml`
- Create: `ingestion/pom.xml`
- Create: `monitoring/pom.xml`
- Create: `app/pom.xml`
- Create: `app/src/main/java/de/secretsoft/vatsim_stats/VatsimStatsApplication.java`
- Create: `app/src/main/resources/application.yml`
- Test: `app/src/test/java/de/secretsoft/vatsim_stats/VatsimStatsApplicationTests.java`

**Interfaces:**
- Produces: Maven coordinates `de.secretsoft.vatsim-stats:{reference-data,detection,ingestion,monitoring,app}:1.0.0-SNAPSHOT`, base Java package `de.secretsoft.vatsim_stats.*`, a bootable Spring Boot app with no DB dependency yet (DB wiring starts in Task 2).

- [ ] **Step 1: Create the parent POM**

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>de.secretsoft.vatsim-stats</groupId>
    <artifactId>vatsim-stats-parentpom</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

        <spring-boot-starter.version>4.0.6</spring-boot-starter.version>
        <vaadin.version>25.1.4</vaadin.version>
        <lombok.version>1.18.46</lombok.version>
        <commons-csv.version>1.11.0</commons-csv.version>
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

    <modules>
        <module>reference-data</module>
        <module>detection</module>
        <module>ingestion</module>
        <module>monitoring</module>
        <module>app</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>de.secretsoft.vatsim-stats</groupId>
                <artifactId>reference-data</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>de.secretsoft.vatsim-stats</groupId>
                <artifactId>detection</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>de.secretsoft.vatsim-stats</groupId>
                <artifactId>ingestion</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>de.secretsoft.vatsim-stats</groupId>
                <artifactId>monitoring</artifactId>
                <version>${project.version}</version>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot-starter.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.vaadin</groupId>
                <artifactId>vaadin-bom</artifactId>
                <version>${vaadin.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-csv</artifactId>
                <version>${commons-csv.version}</version>
            </dependency>
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Create the four library module POMs**

`reference-data/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>de.secretsoft.vatsim-stats</groupId>
        <artifactId>vatsim-stats-parentpom</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>reference-data</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`detection/pom.xml` (deliberately Spring-free, per spec — pure Java library):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>de.secretsoft.vatsim-stats</groupId>
        <artifactId>vatsim-stats-parentpom</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>detection</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.26.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`ingestion/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>de.secretsoft.vatsim-stats</groupId>
        <artifactId>vatsim-stats-parentpom</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ingestion</artifactId>

    <dependencies>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>detection</artifactId>
        </dependency>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>reference-data</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`monitoring/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>de.secretsoft.vatsim-stats</groupId>
        <artifactId>vatsim-stats-parentpom</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>monitoring</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create the app module POM**

`app/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>de.secretsoft.vatsim-stats</groupId>
        <artifactId>vatsim-stats-parentpom</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>app</artifactId>

    <dependencies>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>reference-data</artifactId>
        </dependency>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>detection</artifactId>
        </dependency>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>ingestion</artifactId>
        </dependency>
        <dependency>
            <groupId>de.secretsoft.vatsim-stats</groupId>
            <artifactId>monitoring</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-dev</artifactId>
        </dependency>
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin</artifactId>
        </dependency>
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create the Spring Boot entry point**

`app/src/main/java/de/secretsoft/vatsim_stats/VatsimStatsApplication.java`:

```java
package de.secretsoft.vatsim_stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "de.secretsoft.vatsim_stats")
public class VatsimStatsApplication {

    public static void main( String[] args ) {
        SpringApplication.run( VatsimStatsApplication.class, args );
    }
}
```

- [ ] **Step 5: Create a minimal application.yml (no DB yet)**

`app/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: vatsim-stats

server:
  port: 8080
```

- [ ] **Step 6: Write the smoke test**

`app/src/test/java/de/secretsoft/vatsim_stats/VatsimStatsApplicationTests.java`:

```java
package de.secretsoft.vatsim_stats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VatsimStatsApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 7: Build and verify**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS, all 5 modules built, `contextLoads` passes.

- [ ] **Step 8: Commit**

```bash
git add pom.xml reference-data/pom.xml detection/pom.xml ingestion/pom.xml monitoring/pom.xml app/pom.xml app/src
git commit -m "feat: scaffold vatsim-stats multi-module Maven project"
```

---

## Task 2: Database Wiring + Reference-Data Schema (Airport, Runway)

**Files:**
- Modify: `app/pom.xml` (add JPA/Flyway/Postgres — already added in Task 1, this task adds the connection config)
- Create: `app/src/main/resources/application.yml` (extend with datasource/JPA/Flyway)
- Create: `app/src/main/resources/db/migration/V1__reference_data.sql`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/Airport.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/Runway.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/AirportRepository.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/RunwayRepository.java`
- Test: `app/src/test/java/de/secretsoft/vatsim_stats/referencedata/AirportRepositoryIT.java`

**Interfaces:**
- Consumes: Task 1's bootable app, `docker-compose.yml` Postgres/TimescaleDB instance (`.env` credentials).
- Produces: JPA entities `Airport(icao: String, iata: String, name: String, latitude: double, longitude: double, elevationFt: Integer, isoCountry: String)` and `Runway(id: Long, airportIcao: String, leIdent: String, heIdent: String, leLatitude: Double, leLongitude: Double, heLatitude: Double, heLongitude: Double, lengthFt: Integer, surface: String)`, plus `AirportRepository extends JpaRepository<Airport, String>` and `RunwayRepository extends JpaRepository<Runway, Long>` with `List<Runway> findByAirportIcao(String icao)`. Later tasks use `AirportRepository` for nearest-airport lookups.

- [ ] **Step 1: Extend application.yml with datasource, JPA and Flyway config**

`app/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: vatsim-stats
  datasource:
    url: jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/${POSTGRES_DB:vatsim_stats}
    username: ${POSTGRES_USER:vatsim_stats}
    password: ${POSTGRES_PASSWORD:changeme}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080
```

- [ ] **Step 2: Write the failing repository integration test**

`app/src/test/java/de/secretsoft/vatsim_stats/referencedata/AirportRepositoryIT.java`:

```java
package de.secretsoft.vatsim_stats.referencedata;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest( classes = VatsimStatsApplication.class )
class AirportRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg16" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
    }

    @Autowired
    private AirportRepository airportRepository;

    @Test
    void savesAndReadsAnAirport() {
        Airport airport = Airport.builder()
            .icao( "EDDF" )
            .iata( "FRA" )
            .name( "Frankfurt am Main" )
            .latitude( 50.0264 )
            .longitude( 8.5431 )
            .elevationFt( 364 )
            .isoCountry( "DE" )
            .build();

        airportRepository.save( airport );

        Optional<Airport> found = airportRepository.findById( "EDDF" );
        assertThat( found ).isPresent();
        assertThat( found.get().getName() ).isEqualTo( "Frankfurt am Main" );
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=AirportRepositoryIT`
Expected: FAIL — compile error, `Airport`/`AirportRepository` don't exist yet.

- [ ] **Step 4: Create the Flyway migration**

`app/src/main/resources/db/migration/V1__reference_data.sql`:

```sql
CREATE TABLE airport (
    icao VARCHAR(4) PRIMARY KEY,
    iata VARCHAR(3),
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    elevation_ft INTEGER,
    iso_country VARCHAR(2)
);

CREATE TABLE runway (
    id BIGSERIAL PRIMARY KEY,
    airport_icao VARCHAR(4) NOT NULL REFERENCES airport (icao),
    le_ident VARCHAR(8),
    he_ident VARCHAR(8),
    le_latitude DOUBLE PRECISION,
    le_longitude DOUBLE PRECISION,
    he_latitude DOUBLE PRECISION,
    he_longitude DOUBLE PRECISION,
    length_ft INTEGER,
    surface VARCHAR(64)
);

CREATE INDEX idx_runway_airport_icao ON runway (airport_icao);
```

- [ ] **Step 5: Create the JPA entities**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/Airport.java`:

```java
package de.secretsoft.vatsim_stats.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table( name = "airport" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Airport {

    @Id
    private String icao;

    private String iata;

    @Column( nullable = false )
    private String name;

    @Column( nullable = false )
    private double latitude;

    @Column( nullable = false )
    private double longitude;

    @Column( name = "elevation_ft" )
    private Integer elevationFt;

    @Column( name = "iso_country" )
    private String isoCountry;
}
```

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/Runway.java`:

```java
package de.secretsoft.vatsim_stats.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table( name = "runway" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Runway {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( name = "airport_icao", nullable = false )
    private String airportIcao;

    @Column( name = "le_ident" )
    private String leIdent;

    @Column( name = "he_ident" )
    private String heIdent;

    @Column( name = "le_latitude" )
    private Double leLatitude;

    @Column( name = "le_longitude" )
    private Double leLongitude;

    @Column( name = "he_latitude" )
    private Double heLatitude;

    @Column( name = "he_longitude" )
    private Double heLongitude;

    @Column( name = "length_ft" )
    private Integer lengthFt;

    private String surface;
}
```

- [ ] **Step 6: Create the repositories**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/AirportRepository.java`:

```java
package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, String> {
}
```

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/RunwayRepository.java`:

```java
package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunwayRepository extends JpaRepository<Runway, Long> {

    List<Runway> findByAirportIcao( String airportIcao );
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=AirportRepositoryIT`
Expected: PASS (Testcontainers pulls the `timescale/timescaledb:latest-pg16` image, Flyway runs `V1__reference_data.sql`, entity save/read succeeds).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources reference-data/src/main/java app/src/test
git commit -m "feat: add reference-data schema and Airport/Runway JPA entities"
```

---

## Task 3: OurAirports CSV Parsing

**Files:**
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/AirportCsvRecord.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/RunwayCsvRecord.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsCsvParser.java`
- Test: `reference-data/src/test/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsCsvParserTest.java`

**Interfaces:**
- Produces: `record AirportCsvRecord(String icao, String iata, String name, double latitude, double longitude, Integer elevationFt, String isoCountry)`, `record RunwayCsvRecord(String airportIcao, String leIdent, String heIdent, Double leLatitude, Double leLongitude, Double heLatitude, Double heLongitude, Integer lengthFt, String surface)`, and `OurAirportsCsvParser` with `List<AirportCsvRecord> parseAirports(Reader csv)` and `List<RunwayCsvRecord> parseRunways(Reader csv)`. Only rows with `type` in `{large_airport, medium_airport, small_airport}` and a non-blank `ident`/`gps_code` are included for airports; only non-closed (`closed != "1"`) runways with a resolvable ICAO are included. Task 4's importer consumes these lists directly.

- [ ] **Step 1: Write the failing parser test with real OurAirports-shaped fixtures**

`reference-data/src/test/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsCsvParserTest.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OurAirportsCsvParserTest {

    private static final String AIRPORTS_CSV = """
        id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords
        3622,EDDF,large_airport,"Frankfurt am Main Airport",50.026421,8.543125,364,EU,DE,DE-HE,Frankfurt am Main,yes,EDDF,FRA,,,,
        3623,EDXX,heliport,"Some Heliport",51.0,9.0,10,EU,DE,DE-HE,Nowhere,no,EDXX,,,,,
        3624,,small_airport,"No Ident Airport",52.0,10.0,5,EU,DE,DE-HE,Nowhere,no,,,,,,
        """;

    private static final String RUNWAYS_CSV = """
        id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft
        70172,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,
        70173,3622,EDDF,3000,100,"Asphalt",1,1,18,50.01,8.50,364,180,,36,50.02,8.51,364,0,
        """;

    private final OurAirportsCsvParser parser = new OurAirportsCsvParser();

    @Test
    void parsesOnlyRealAirportsWithAnIdent() {
        List<AirportCsvRecord> airports = parser.parseAirports( new StringReader( AIRPORTS_CSV ) );

        assertThat( airports ).hasSize( 1 );
        AirportCsvRecord frankfurt = airports.get( 0 );
        assertThat( frankfurt.icao() ).isEqualTo( "EDDF" );
        assertThat( frankfurt.iata() ).isEqualTo( "FRA" );
        assertThat( frankfurt.name() ).isEqualTo( "Frankfurt am Main Airport" );
        assertThat( frankfurt.latitude() ).isEqualTo( 50.026421 );
        assertThat( frankfurt.longitude() ).isEqualTo( 8.543125 );
        assertThat( frankfurt.elevationFt() ).isEqualTo( 364 );
        assertThat( frankfurt.isoCountry() ).isEqualTo( "DE" );
    }

    @Test
    void parsesOnlyNonClosedRunways() {
        List<RunwayCsvRecord> runways = parser.parseRunways( new StringReader( RUNWAYS_CSV ) );

        assertThat( runways ).hasSize( 1 );
        RunwayCsvRecord runway = runways.get( 0 );
        assertThat( runway.airportIcao() ).isEqualTo( "EDDF" );
        assertThat( runway.leIdent() ).isEqualTo( "07C" );
        assertThat( runway.heIdent() ).isEqualTo( "25C" );
        assertThat( runway.lengthFt() ).isEqualTo( 13123 );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl reference-data test -Dtest=OurAirportsCsvParserTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Create the record types**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/AirportCsvRecord.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

public record AirportCsvRecord(
    String icao,
    String iata,
    String name,
    double latitude,
    double longitude,
    Integer elevationFt,
    String isoCountry ) {
}
```

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/RunwayCsvRecord.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

public record RunwayCsvRecord(
    String airportIcao,
    String leIdent,
    String heIdent,
    Double leLatitude,
    Double leLongitude,
    Double heLatitude,
    Double heLongitude,
    Integer lengthFt,
    String surface ) {
}
```

- [ ] **Step 4: Implement the parser**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsCsvParser.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OurAirportsCsvParser {

    private static final Set<String> RELEVANT_AIRPORT_TYPES =
        Set.of( "large_airport", "medium_airport", "small_airport" );

    public List<AirportCsvRecord> parseAirports( Reader csv ) {
        List<AirportCsvRecord> result = new ArrayList<>();
        try( CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord( true ).build().parse( csv ) ) {
            for( CSVRecord record : parser ) {
                String icao = firstNonBlank( record.get( "ident" ), record.get( "gps_code" ) );
                String type = record.get( "type" );
                if( icao == null || icao.isBlank() || !RELEVANT_AIRPORT_TYPES.contains( type ) ) {
                    continue;
                }
                result.add( new AirportCsvRecord(
                    icao,
                    blankToNull( record.get( "iata_code" ) ),
                    record.get( "name" ),
                    Double.parseDouble( record.get( "latitude_deg" ) ),
                    Double.parseDouble( record.get( "longitude_deg" ) ),
                    parseIntOrNull( record.get( "elevation_ft" ) ),
                    blankToNull( record.get( "iso_country" ) )
                ) );
            }
        } catch( IOException e ) {
            throw new UncheckedIOException( e );
        }
        return result;
    }

    public List<RunwayCsvRecord> parseRunways( Reader csv ) {
        List<RunwayCsvRecord> result = new ArrayList<>();
        try( CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord( true ).build().parse( csv ) ) {
            for( CSVRecord record : parser ) {
                String icao = record.get( "airport_ident" );
                boolean closed = "1".equals( record.get( "closed" ) );
                if( icao == null || icao.isBlank() || closed ) {
                    continue;
                }
                result.add( new RunwayCsvRecord(
                    icao,
                    blankToNull( record.get( "le_ident" ) ),
                    blankToNull( record.get( "he_ident" ) ),
                    parseDoubleOrNull( record.get( "le_latitude_deg" ) ),
                    parseDoubleOrNull( record.get( "le_longitude_deg" ) ),
                    parseDoubleOrNull( record.get( "he_latitude_deg" ) ),
                    parseDoubleOrNull( record.get( "he_longitude_deg" ) ),
                    parseIntOrNull( record.get( "length_ft" ) ),
                    blankToNull( record.get( "surface" ) )
                ) );
            }
        } catch( IOException e ) {
            throw new UncheckedIOException( e );
        }
        return result;
    }

    private static String firstNonBlank( String a, String b ) {
        if( a != null && !a.isBlank() ) {
            return a;
        }
        return ( b != null && !b.isBlank() ) ? b : null;
    }

    private static String blankToNull( String value ) {
        return ( value == null || value.isBlank() ) ? null : value;
    }

    private static Integer parseIntOrNull( String value ) {
        if( value == null || value.isBlank() ) {
            return null;
        }
        return (int) Double.parseDouble( value );
    }

    private static Double parseDoubleOrNull( String value ) {
        return ( value == null || value.isBlank() ) ? null : Double.parseDouble( value );
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl reference-data test -Dtest=OurAirportsCsvParserTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports reference-data/src/test
git commit -m "feat: add OurAirports CSV parser for airports and runways"
```

---

## Task 4: OurAirports Importer Service + Daily Scheduled Job

**Files:**
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportResult.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportService.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsScheduledImportJob.java`
- Modify: `reference-data/pom.xml` (add `spring-boot-starter-web` for `RestClient` download support)
- Test: `reference-data/src/test/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportServiceTest.java`

**Interfaces:**
- Consumes: `OurAirportsCsvParser` (Task 3), `AirportRepository`/`RunwayRepository` (Task 2).
- Produces: `record OurAirportsImportResult(int airportsUpserted, int runwaysUpserted)`, `OurAirportsImportService` with `OurAirportsImportResult importFrom(Reader airportsCsv, Reader runwaysCsv)` (pure upsert logic, no network) and `OurAirportsImportResult importFromOurAirports()` (downloads the two CSVs via `RestClient` then delegates). Task 13's `HealthMonitor` wraps calls to `importFromOurAirports()` to record success/failure.

- [ ] **Step 1: Add the web starter dependency to reference-data**

Edit `reference-data/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- [ ] **Step 2: Write the failing importer test (upsert logic only, no network)**

`reference-data/src/test/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportServiceTest.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import de.secretsoft.vatsim_stats.referencedata.Runway;
import de.secretsoft.vatsim_stats.referencedata.RunwayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OurAirportsImportServiceTest {

    private static final String AIRPORTS_CSV = """
        id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords
        3622,EDDF,large_airport,"Frankfurt am Main Airport",50.026421,8.543125,364,EU,DE,DE-HE,Frankfurt am Main,yes,EDDF,FRA,,,,
        """;

    private static final String RUNWAYS_CSV = """
        id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft
        70172,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,
        """;

    private AirportRepository airportRepository;
    private RunwayRepository runwayRepository;
    private OurAirportsImportService service;

    @BeforeEach
    void setUp() {
        airportRepository = mock( AirportRepository.class );
        runwayRepository = mock( RunwayRepository.class );
        when( airportRepository.findById( any() ) ).thenReturn( Optional.empty() );
        when( runwayRepository.findByAirportIcao( any() ) ).thenReturn( List.of() );
        service = new OurAirportsImportService( new OurAirportsCsvParser(), airportRepository, runwayRepository );
    }

    @Test
    void upsertsParsedAirportsAndRunways() {
        OurAirportsImportResult result = service.importFrom(
            new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( result.airportsUpserted() ).isEqualTo( 1 );
        assertThat( result.runwaysUpserted() ).isEqualTo( 1 );

        ArgumentCaptor<Airport> airportCaptor = ArgumentCaptor.forClass( Airport.class );
        verify( airportRepository ).save( airportCaptor.capture() );
        assertThat( airportCaptor.getValue().getIcao() ).isEqualTo( "EDDF" );

        ArgumentCaptor<Runway> runwayCaptor = ArgumentCaptor.forClass( Runway.class );
        verify( runwayRepository ).save( runwayCaptor.capture() );
        assertThat( runwayCaptor.getValue().getAirportIcao() ).isEqualTo( "EDDF" );
    }
}
```

- [ ] **Step 3: Add Mockito test dependency**

Edit `reference-data/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q -pl reference-data test -Dtest=OurAirportsImportServiceTest`
Expected: FAIL — `OurAirportsImportService`/`OurAirportsImportResult` don't exist yet.

- [ ] **Step 5: Implement the import service**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportResult.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

public record OurAirportsImportResult( int airportsUpserted, int runwaysUpserted ) {
}
```

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportService.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import de.secretsoft.vatsim_stats.referencedata.Runway;
import de.secretsoft.vatsim_stats.referencedata.RunwayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OurAirportsImportService {

    private static final String AIRPORTS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv";
    private static final String RUNWAYS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/runways.csv";

    private final OurAirportsCsvParser parser;
    private final AirportRepository airportRepository;
    private final RunwayRepository runwayRepository;

    public OurAirportsImportResult importFromOurAirports() {
        RestClient client = RestClient.builder()
            .requestFactory( timeoutingRequestFactory() )
            .build();

        try( Reader airportsCsv = new InputStreamReader( download( client, AIRPORTS_CSV_URL ), StandardCharsets.UTF_8 );
             Reader runwaysCsv = new InputStreamReader( download( client, RUNWAYS_CSV_URL ), StandardCharsets.UTF_8 ) ) {
            return importFrom( airportsCsv, runwaysCsv );
        } catch( java.io.IOException e ) {
            throw new java.io.UncheckedIOException( e );
        }
    }

    public OurAirportsImportResult importFrom( Reader airportsCsv, Reader runwaysCsv ) {
        List<AirportCsvRecord> airports = parser.parseAirports( airportsCsv );
        for( AirportCsvRecord record : airports ) {
            Airport airport = airportRepository.findById( record.icao() )
                .orElseGet( () -> Airport.builder().icao( record.icao() ).build() );
            airport.setIata( record.iata() );
            airport.setName( record.name() );
            airport.setLatitude( record.latitude() );
            airport.setLongitude( record.longitude() );
            airport.setElevationFt( record.elevationFt() );
            airport.setIsoCountry( record.isoCountry() );
            airportRepository.save( airport );
        }

        List<RunwayCsvRecord> runways = parser.parseRunways( runwaysCsv );
        for( RunwayCsvRecord record : runways ) {
            Runway runway = Runway.builder()
                .airportIcao( record.airportIcao() )
                .leIdent( record.leIdent() )
                .heIdent( record.heIdent() )
                .leLatitude( record.leLatitude() )
                .leLongitude( record.leLongitude() )
                .heLatitude( record.heLatitude() )
                .heLongitude( record.heLongitude() )
                .lengthFt( record.lengthFt() )
                .surface( record.surface() )
                .build();
            runwayRepository.save( runway );
        }

        return new OurAirportsImportResult( airports.size(), runways.size() );
    }

    private java.io.InputStream download( RestClient client, String url ) {
        byte[] body = client.get().uri( url ).retrieve().body( byte[].class );
        return new java.io.ByteArrayInputStream( body != null ? body : new byte[0] );
    }

    private SimpleClientHttpRequestFactory timeoutingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout( (int) Duration.ofSeconds( 30 ).toMillis() );
        factory.setReadTimeout( (int) Duration.ofSeconds( 60 ).toMillis() );
        return factory;
    }
}
```

Note: `importFrom(Reader, Reader)` does **not** delete runways that disappeared from the source (e.g. permanently closed runways already filtered on import) — full replace-on-import is deliberately out of scope here; re-running the daily job simply adds/updates. If duplicate runway rows from repeated imports become a problem in practice, add a unique constraint on `(airport_icao, le_ident, he_ident)` and switch to a delete-and-reinsert-per-airport strategy — tracked as a follow-up, not blocking this task.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -pl reference-data test -Dtest=OurAirportsImportServiceTest`
Expected: PASS

- [ ] **Step 7: Create the daily scheduled job**

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsScheduledImportJob.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OurAirportsScheduledImportJob {

    private final OurAirportsImportService importService;

    @Scheduled( cron = "0 30 3 * * *" )
    public void run() {
        try {
            OurAirportsImportResult result = importService.importFromOurAirports();
            log.info( "OurAirports import finished: {} airports, {} runways",
                result.airportsUpserted(), result.runwaysUpserted() );
        } catch( Exception e ) {
            log.error( "OurAirports import failed, keeping previous data", e );
        }
    }
}
```

(Wiring this job's success/failure into `HealthMonitor` happens in Task 15 once the `monitoring` module exists — `reference-data` cannot depend on `monitoring` without creating a cycle, so Task 15 publishes a Spring `ApplicationEvent` from this job instead, which `monitoring` listens for without either module depending on the other.)

- [ ] **Step 8: Build the whole project to confirm nothing else broke**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add reference-data/pom.xml reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports reference-data/src/test
git commit -m "feat: add OurAirports import service and daily scheduled job"
```

---

## Task 5: Nearest-Airport Lookup (Haversine) — `detection` module

**Files:**
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportRef.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/NearestAirportLookup.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/Haversine.java`
- Test: `detection/src/test/java/de/secretsoft/vatsim_stats/detection/HaversineTest.java`

**Interfaces:**
- Produces: `record AirportRef(String icao, double latitude, double longitude, double elevationFt)`, `interface NearestAirportLookup { Optional<AirportRef> findNearest(double latitude, double longitude, double radiusNm); }` (the contract the `detection` module depends on — Task 10 in `ingestion` implements it against `AirportRepository`), and `final class Haversine { static double distanceNm(double lat1, double lon1, double lat2, double lon2); }`. Task 6's state machine takes a `NearestAirportLookup` in its constructor.

- [ ] **Step 1: Write the failing Haversine test**

`detection/src/test/java/de/secretsoft/vatsim_stats/detection/HaversineTest.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineTest {

    @Test
    void distanceBetweenFrankfurtAndMunichIsAbout201Nm() {
        double distance = Haversine.distanceNm( 50.026421, 8.543125, 48.353783, 11.786086 );

        assertThat( distance ).isCloseTo( 201.0, within( 3.0 ) );
    }

    @Test
    void distanceToSelfIsZero() {
        double distance = Haversine.distanceNm( 50.026421, 8.543125, 50.026421, 8.543125 );

        assertThat( distance ).isCloseTo( 0.0, within( 0.0001 ) );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl detection test -Dtest=HaversineTest`
Expected: FAIL — `Haversine` doesn't exist yet.

- [ ] **Step 3: Implement Haversine**

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/Haversine.java`:

```java
package de.secretsoft.vatsim_stats.detection;

public final class Haversine {

    private static final double EARTH_RADIUS_NM = 3440.065;

    private Haversine() {
    }

    public static double distanceNm( double lat1, double lon1, double lat2, double lon2 ) {
        double lat1Rad = Math.toRadians( lat1 );
        double lat2Rad = Math.toRadians( lat2 );
        double deltaLatRad = Math.toRadians( lat2 - lat1 );
        double deltaLonRad = Math.toRadians( lon2 - lon1 );

        double a = Math.sin( deltaLatRad / 2 ) * Math.sin( deltaLatRad / 2 )
            + Math.cos( lat1Rad ) * Math.cos( lat2Rad )
            * Math.sin( deltaLonRad / 2 ) * Math.sin( deltaLonRad / 2 );
        double c = 2 * Math.atan2( Math.sqrt( a ), Math.sqrt( 1 - a ) );

        return EARTH_RADIUS_NM * c;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl detection test -Dtest=HaversineTest`
Expected: PASS

- [ ] **Step 5: Create the AirportRef record and lookup interface**

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportRef.java`:

```java
package de.secretsoft.vatsim_stats.detection;

public record AirportRef( String icao, double latitude, double longitude, double elevationFt ) {
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/NearestAirportLookup.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.util.Optional;

public interface NearestAirportLookup {

    Optional<AirportRef> findNearest( double latitude, double longitude, double radiusNm );
}
```

- [ ] **Step 6: Build to confirm compilation**

Run: `mvn -q -pl detection install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add detection/src/main/java detection/src/test
git commit -m "feat: add Haversine distance and nearest-airport lookup contract"
```

---

## Task 6: Pilot Phase State Machine (TAKEOFF / LANDING / TOUCH_AND_GO / LOW_APPROACH)

This is the core fachlich logic from the spec's "Phasenerkennung" section. Pure Java, no Spring, fully unit-testable with synthetic sequences.

**Files:**
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/Phase.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportEventType.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportEvent.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/TrackSample.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/PhaseDetectionConfig.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/PhaseSnapshot.java`
- Create: `detection/src/main/java/de/secretsoft/vatsim_stats/detection/PilotPhaseStateMachine.java`
- Test: `detection/src/test/java/de/secretsoft/vatsim_stats/detection/PilotPhaseStateMachineTest.java`

**Interfaces:**
- Consumes: `AirportRef`, `NearestAirportLookup`, `Haversine` (Task 5).
- Produces: `enum Phase { AIRBORNE, GROUND_PENDING, ON_GROUND }`, `enum AirportEventType { TAKEOFF, LANDING, TOUCH_AND_GO, LOW_APPROACH }`, `record AirportEvent(String airportIcao, AirportEventType type, Instant timestamp)`, `record TrackSample(Instant timestamp, double latitude, double longitude, double altitudeFt, double groundspeedKt)`, `record PhaseDetectionConfig(double groundspeedThresholdKt, double altitudeAglThresholdFt, double nearestAirportRadiusNm, Duration groundDwellThreshold)` with static `PhaseDetectionConfig.defaults()`, `record PhaseSnapshot(Phase phase, String pendingAirportIcao, Instant pendingSince, boolean pendingTouchedDown, String groundAirportIcao)`, and `class PilotPhaseStateMachine` with constructor `PilotPhaseStateMachine(PhaseDetectionConfig config, NearestAirportLookup lookup)`, static factory `PilotPhaseStateMachine.reconstruct(PhaseDetectionConfig config, NearestAirportLookup lookup, PhaseSnapshot snapshot)`, instance methods `List<AirportEvent> process(TrackSample sample)`, `List<AirportEvent> onDisappearedFromFeed()`, and `PhaseSnapshot snapshot()`. Task 10 (session orchestration) and Task 12 (restart reconstruction) depend directly on these exact names.

- [ ] **Step 1: Write the failing state machine tests**

`detection/src/test/java/de/secretsoft/vatsim_stats/detection/PilotPhaseStateMachineTest.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PilotPhaseStateMachineTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant T0 = Instant.parse( "2026-08-24T10:00:00Z" );
    private static final PhaseDetectionConfig CONFIG = PhaseDetectionConfig.defaults();

    private static final NearestAirportLookup ALWAYS_EDDF = ( lat, lon, radius ) -> Optional.of( EDDF );
    private static final NearestAirportLookup NEVER_FOUND = ( lat, lon, radius ) -> Optional.empty();

    private TrackSample sample( int offsetSeconds, double altitudeFt, double groundspeedKt ) {
        return new TrackSample( T0.plusSeconds( offsetSeconds ), 50.0, 8.5, altitudeFt, groundspeedKt );
    }

    @Test
    void firstSampleEstablishesPhaseWithoutEmittingAnEvent() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        List<AirportEvent> events = machine.process( sample( 0, 3000, 250 ) );

        assertThat( events ).isEmpty();
    }

    @Test
    void takeoffEmittedImmediatelyWhenLeavingConfirmedGround() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 550, 5 ) );
        List<AirportEvent> takeoff = machine.process( sample( 15, 550, 5 ) );
        assertThat( takeoff ).isEmpty();

        List<AirportEvent> events = machine.process( sample( 30, 3000, 180 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.TAKEOFF, T0.plusSeconds( 30 ) ) );
    }

    @Test
    void landingEmittedAfterGroundDwellThresholdIsReached() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 3000, 200 ) );

        List<AirportEvent> events = List.of();
        for( int offset = 30; offset <= 105; offset += 15 ) {
            events = machine.process( sample( offset, 550, 15 ) );
            assertThat( events ).as( "offset " + offset ).isEmpty();
        }

        events = machine.process( sample( 120, 550, 15 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0.plusSeconds( 30 ) ) );
    }

    @Test
    void touchAndGoEmittedWhenClimbingOutBeforeDwellWithGroundspeedBelowThreshold() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 15 ) );
        machine.process( sample( 30, 550, 10 ) );

        List<AirportEvent> events = machine.process( sample( 45, 3000, 180 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.TOUCH_AND_GO, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void lowApproachEmittedWhenClimbingOutBeforeDwellWithGroundspeedNeverBelowThreshold() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 95 ) );
        machine.process( sample( 30, 550, 90 ) );

        List<AirportEvent> events = machine.process( sample( 45, 3000, 200 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LOW_APPROACH, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void disappearingFromFeedWhileGroundPendingEmitsLanding() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 15 ) );

        List<AirportEvent> events = machine.onDisappearedFromFeed();

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void noNearbyAirportKeepsAircraftAirborneRegardlessOfAltitudeAndSpeed() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, NEVER_FOUND );

        machine.process( sample( 0, 3000, 250 ) );
        List<AirportEvent> events = machine.process( sample( 15, 50, 5 ) );

        assertThat( events ).isEmpty();
        assertThat( machine.snapshot().phase() ).isEqualTo( Phase.AIRBORNE );
    }

    @Test
    void reconstructResumesFromAPersistedSnapshotWithoutReplayingHistory() {
        PhaseSnapshot snapshot = new PhaseSnapshot(
            Phase.GROUND_PENDING, "EDDF", T0, true, null );
        PilotPhaseStateMachine machine =
            PilotPhaseStateMachine.reconstruct( CONFIG, ALWAYS_EDDF, snapshot );

        List<AirportEvent> events = machine.process( sample( 90, 550, 15 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0 ) );
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl detection test -Dtest=PilotPhaseStateMachineTest`
Expected: FAIL — none of the classes exist yet.

- [ ] **Step 3: Create the small supporting types**

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/Phase.java`:

```java
package de.secretsoft.vatsim_stats.detection;

public enum Phase {
    AIRBORNE,
    GROUND_PENDING,
    ON_GROUND
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportEventType.java`:

```java
package de.secretsoft.vatsim_stats.detection;

public enum AirportEventType {
    TAKEOFF,
    LANDING,
    TOUCH_AND_GO,
    LOW_APPROACH
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/AirportEvent.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record AirportEvent( String airportIcao, AirportEventType type, Instant timestamp ) {
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/TrackSample.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record TrackSample(
    Instant timestamp,
    double latitude,
    double longitude,
    double altitudeFt,
    double groundspeedKt ) {
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/PhaseDetectionConfig.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.time.Duration;

public record PhaseDetectionConfig(
    double groundspeedThresholdKt,
    double altitudeAglThresholdFt,
    double nearestAirportRadiusNm,
    Duration groundDwellThreshold ) {

    public static PhaseDetectionConfig defaults() {
        return new PhaseDetectionConfig( 40.0, 200.0, 5.0, Duration.ofSeconds( 90 ) );
    }
}
```

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/PhaseSnapshot.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record PhaseSnapshot(
    Phase phase,
    String pendingAirportIcao,
    Instant pendingSince,
    boolean pendingTouchedDown,
    String groundAirportIcao ) {
}
```

- [ ] **Step 4: Implement the state machine**

`detection/src/main/java/de/secretsoft/vatsim_stats/detection/PilotPhaseStateMachine.java`:

```java
package de.secretsoft.vatsim_stats.detection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PilotPhaseStateMachine {

    private final PhaseDetectionConfig config;
    private final NearestAirportLookup lookup;

    private Phase phase;
    private String pendingAirportIcao;
    private Instant pendingSince;
    private boolean pendingTouchedDown;
    private String groundAirportIcao;

    public PilotPhaseStateMachine( PhaseDetectionConfig config, NearestAirportLookup lookup ) {
        this.config = config;
        this.lookup = lookup;
        this.phase = null;
    }

    public static PilotPhaseStateMachine reconstruct(
        PhaseDetectionConfig config, NearestAirportLookup lookup, PhaseSnapshot snapshot ) {

        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( config, lookup );
        machine.phase = snapshot.phase();
        machine.pendingAirportIcao = snapshot.pendingAirportIcao();
        machine.pendingSince = snapshot.pendingSince();
        machine.pendingTouchedDown = snapshot.pendingTouchedDown();
        machine.groundAirportIcao = snapshot.groundAirportIcao();
        return machine;
    }

    public List<AirportEvent> process( TrackSample sample ) {
        Optional<AirportRef> nearest =
            lookup.findNearest( sample.latitude(), sample.longitude(), config.nearestAirportRadiusNm() );
        boolean nearGround = nearest.isPresent()
            && sample.altitudeFt() <= nearest.get().elevationFt() + config.altitudeAglThresholdFt();
        boolean belowGroundspeed = sample.groundspeedKt() < config.groundspeedThresholdKt();

        if( phase == null ) {
            phase = nearGround ? Phase.ON_GROUND : Phase.AIRBORNE;
            groundAirportIcao = nearGround ? nearest.get().icao() : null;
            return List.of();
        }

        return switch( phase ) {
            case AIRBORNE -> handleAirborne( sample, nearest, nearGround, belowGroundspeed );
            case GROUND_PENDING -> handleGroundPending( sample, nearGround, belowGroundspeed );
            case ON_GROUND -> handleOnGround( sample, nearGround );
        };
    }

    public List<AirportEvent> onDisappearedFromFeed() {
        if( phase == Phase.GROUND_PENDING ) {
            AirportEvent landing = new AirportEvent( pendingAirportIcao, AirportEventType.LANDING, pendingSince );
            phase = Phase.ON_GROUND;
            groundAirportIcao = pendingAirportIcao;
            clearPending();
            return List.of( landing );
        }
        return List.of();
    }

    public PhaseSnapshot snapshot() {
        return new PhaseSnapshot( phase, pendingAirportIcao, pendingSince, pendingTouchedDown, groundAirportIcao );
    }

    private List<AirportEvent> handleAirborne(
        TrackSample sample, Optional<AirportRef> nearest, boolean nearGround, boolean belowGroundspeed ) {

        if( nearGround ) {
            phase = Phase.GROUND_PENDING;
            pendingAirportIcao = nearest.get().icao();
            pendingSince = sample.timestamp();
            pendingTouchedDown = belowGroundspeed;
        }
        return List.of();
    }

    private List<AirportEvent> handleGroundPending(
        TrackSample sample, boolean nearGround, boolean belowGroundspeed ) {

        if( !nearGround ) {
            AirportEventType type = pendingTouchedDown ? AirportEventType.TOUCH_AND_GO : AirportEventType.LOW_APPROACH;
            AirportEvent event = new AirportEvent( pendingAirportIcao, type, pendingSince );
            phase = Phase.AIRBORNE;
            clearPending();
            return List.of( event );
        }

        pendingTouchedDown = pendingTouchedDown || belowGroundspeed;

        if( !Duration.between( pendingSince, sample.timestamp() ).minus( config.groundDwellThreshold() ).isNegative() ) {
            AirportEvent event = new AirportEvent( pendingAirportIcao, AirportEventType.LANDING, pendingSince );
            groundAirportIcao = pendingAirportIcao;
            phase = Phase.ON_GROUND;
            clearPending();
            return List.of( event );
        }

        return List.of();
    }

    private List<AirportEvent> handleOnGround( TrackSample sample, boolean nearGround ) {
        if( !nearGround ) {
            AirportEvent event = new AirportEvent( groundAirportIcao, AirportEventType.TAKEOFF, sample.timestamp() );
            phase = Phase.AIRBORNE;
            groundAirportIcao = null;
            return List.of( event );
        }
        return List.of();
    }

    private void clearPending() {
        pendingAirportIcao = null;
        pendingSince = null;
        pendingTouchedDown = false;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -pl detection test -Dtest=PilotPhaseStateMachineTest`
Expected: PASS — all 8 tests green.

- [ ] **Step 6: Commit**

```bash
git add detection/src/main/java/de/secretsoft/vatsim_stats/detection detection/src/test
git commit -m "feat: add pilot phase state machine with takeoff/landing/touch-and-go/low-approach detection"
```

---

## Task 7: Ingestion Database Schema (TimescaleDB Hypertables) + JPA Entities

**Files:**
- Create: `app/src/main/resources/db/migration/V2__ingestion.sql`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotTrackPoint.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSnapshot.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/SessionStatus.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSession.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEvent.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSession.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotTrackPointRepository.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSnapshotRepository.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepository.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEventRepository.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSessionRepository.java`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepositoryIT.java`

**Interfaces:**
- Consumes: `AirportEventType` (Task 6, reused directly — no duplicate enum), Task 2's Flyway/Testcontainers pattern.
- Produces: entities `PilotTrackPoint(id: Long, recordedAt: Instant, cid: Long, callsign: String, logonTime: Instant, latitude: double, longitude: double, altitudeFt: int, groundspeedKt: int, heading: Integer, transponder: String, qnhMb: Integer, flightPlanDeparture: String, flightPlanDestination: String, aircraftShort: String)`; `AtcSnapshot(id, recordedAt, cid, callsign, logonTime, frequency, facility, visualRange, latitude, longitude)`; `enum SessionStatus { ACTIVE, COMPLETED }`; `PilotSession(id: Long, cid: Long, callsign: String, logonTime: Instant, sequenceNumber: int, plannedDeparture: String, plannedDestination: String, aircraftShort: String, status: SessionStatus, startedAt: Instant, endedAt: Instant)`; `PilotAirportEvent(id: Long, pilotSession: PilotSession, airportIcao: String, eventType: AirportEventType, occurredAt: Instant)`; `AtcSession(id, cid, callsign, logonTime, facility, startedAt, endedAt)`; and their `JpaRepository` interfaces with the finder methods listed in Step 5/6 below, used by Tasks 9–12.

- [ ] **Step 1: Write the failing repository test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepositoryIT.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest( classes = VatsimStatsApplication.class )
class PilotSessionRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg16" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
    }

    @Autowired
    private PilotSessionRepository pilotSessionRepository;

    @Autowired
    private PilotTrackPointRepository pilotTrackPointRepository;

    @Test
    void savesSessionAndFindsItByNaturalKey() {
        Instant logonTime = Instant.parse( "2026-08-24T10:00:00Z" );
        PilotSession session = PilotSession.builder()
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( logonTime )
            .sequenceNumber( 0 )
            .status( SessionStatus.ACTIVE )
            .startedAt( logonTime )
            .build();

        pilotSessionRepository.save( session );

        Optional<PilotSession> found = pilotSessionRepository
            .findByCidAndCallsignAndLogonTimeAndSequenceNumber( 123456L, "DLH400", logonTime, 0 );
        assertThat( found ).isPresent();
        assertThat( found.get().getStatus() ).isEqualTo( SessionStatus.ACTIVE );
    }

    @Test
    void findsMostRecentTrackPointsForRestartReconstruction() {
        Instant logonTime = Instant.parse( "2026-08-24T10:00:00Z" );
        for( int i = 0; i < 3; i++ ) {
            pilotTrackPointRepository.save( PilotTrackPoint.builder()
                .recordedAt( logonTime.plusSeconds( i * 15L ) )
                .cid( 123456L )
                .callsign( "DLH400" )
                .logonTime( logonTime )
                .latitude( 50.0 )
                .longitude( 8.5 )
                .altitudeFt( 3000 )
                .groundspeedKt( 250 )
                .build() );
        }

        List<PilotTrackPoint> recent = pilotTrackPointRepository
            .findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc( 123456L, "DLH400", logonTime );

        assertThat( recent ).hasSize( 3 );
        assertThat( recent.get( 0 ).getRecordedAt() ).isEqualTo( logonTime.plusSeconds( 30 ) );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=PilotSessionRepositoryIT`
Expected: FAIL — none of the entities/repositories exist yet.

- [ ] **Step 3: Create the Flyway migration with TimescaleDB hypertables**

`app/src/main/resources/db/migration/V2__ingestion.sql`:

```sql
CREATE TABLE pilot_track_point (
    id BIGSERIAL,
    recorded_at TIMESTAMPTZ NOT NULL,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    altitude_ft INTEGER NOT NULL,
    groundspeed_kt INTEGER NOT NULL,
    heading INTEGER,
    transponder VARCHAR(8),
    qnh_mb INTEGER,
    flight_plan_departure VARCHAR(8),
    flight_plan_destination VARCHAR(8),
    aircraft_short VARCHAR(16),
    PRIMARY KEY (id, recorded_at)
);
SELECT create_hypertable('pilot_track_point', by_range('recorded_at'));
CREATE INDEX idx_pilot_track_point_session
    ON pilot_track_point (cid, callsign, logon_time, recorded_at DESC);

CREATE TABLE atc_snapshot (
    id BIGSERIAL,
    recorded_at TIMESTAMPTZ NOT NULL,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    frequency VARCHAR(16),
    facility INTEGER,
    visual_range INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    PRIMARY KEY (id, recorded_at)
);
SELECT create_hypertable('atc_snapshot', by_range('recorded_at'));
CREATE INDEX idx_atc_snapshot_session
    ON atc_snapshot (cid, callsign, logon_time, recorded_at DESC);

CREATE TABLE pilot_session (
    id BIGSERIAL PRIMARY KEY,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    sequence_number INTEGER NOT NULL DEFAULT 0,
    planned_departure VARCHAR(8),
    planned_destination VARCHAR(8),
    aircraft_short VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    UNIQUE (cid, callsign, logon_time, sequence_number)
);

CREATE TABLE pilot_airport_event (
    id BIGSERIAL PRIMARY KEY,
    pilot_session_id BIGINT NOT NULL REFERENCES pilot_session (id),
    airport_icao VARCHAR(8) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_pilot_airport_event_session ON pilot_airport_event (pilot_session_id);

CREATE TABLE atc_session (
    id BIGSERIAL PRIMARY KEY,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    facility INTEGER,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    UNIQUE (cid, callsign, logon_time)
);
```

`pilot_session.sequence_number` disambiguates multiple flights under one unbroken connection (same `logon_time`) — e.g. a pilot who lands, refiles a new flight plan, and departs again without disconnecting. It starts at `0` and is incremented by the orchestration logic in Task 10 whenever it opens a new leg on top of an already-`COMPLETED` session for the same `(cid, callsign, logon_time)`.

- [ ] **Step 4: Create the raw-data entities**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotTrackPoint.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "pilot_track_point" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotTrackPoint {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( name = "recorded_at", nullable = false )
    private Instant recordedAt;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    @Column( nullable = false )
    private double latitude;

    @Column( nullable = false )
    private double longitude;

    @Column( name = "altitude_ft", nullable = false )
    private int altitudeFt;

    @Column( name = "groundspeed_kt", nullable = false )
    private int groundspeedKt;

    private Integer heading;

    private String transponder;

    @Column( name = "qnh_mb" )
    private Integer qnhMb;

    @Column( name = "flight_plan_departure" )
    private String flightPlanDeparture;

    @Column( name = "flight_plan_destination" )
    private String flightPlanDestination;

    @Column( name = "aircraft_short" )
    private String aircraftShort;
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSnapshot.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "atc_snapshot" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtcSnapshot {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( name = "recorded_at", nullable = false )
    private Instant recordedAt;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    private String frequency;

    private Integer facility;

    @Column( name = "visual_range" )
    private Integer visualRange;

    private Double latitude;

    private Double longitude;
}
```

- [ ] **Step 5: Create the derived-data entities**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/SessionStatus.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

public enum SessionStatus {
    ACTIVE,
    COMPLETED
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSession.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "pilot_session" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotSession {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    @Builder.Default
    @Column( name = "sequence_number", nullable = false )
    private int sequenceNumber = 0;

    @Column( name = "planned_departure" )
    private String plannedDeparture;

    @Column( name = "planned_destination" )
    private String plannedDestination;

    @Column( name = "aircraft_short" )
    private String aircraftShort;

    @Enumerated( EnumType.STRING )
    @Column( nullable = false )
    private SessionStatus status;

    @Column( name = "started_at", nullable = false )
    private Instant startedAt;

    @Column( name = "ended_at" )
    private Instant endedAt;
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEvent.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import de.secretsoft.vatsim_stats.detection.AirportEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "pilot_airport_event" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotAirportEvent {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @ManyToOne( fetch = FetchType.LAZY )
    @JoinColumn( name = "pilot_session_id", nullable = false )
    private PilotSession pilotSession;

    @Column( name = "airport_icao", nullable = false )
    private String airportIcao;

    @Enumerated( EnumType.STRING )
    @Column( name = "event_type", nullable = false )
    private AirportEventType eventType;

    @Column( name = "occurred_at", nullable = false )
    private Instant occurredAt;
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSession.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "atc_session" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtcSession {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    private Integer facility;

    @Column( name = "started_at", nullable = false )
    private Instant startedAt;

    @Column( name = "ended_at" )
    private Instant endedAt;
}
```

- [ ] **Step 6: Create the repositories**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotTrackPointRepository.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PilotTrackPointRepository extends JpaRepository<PilotTrackPoint, Long> {

    List<PilotTrackPoint> findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSnapshotRepository.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AtcSnapshotRepository extends JpaRepository<AtcSnapshot, Long> {
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepository.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PilotSessionRepository extends JpaRepository<PilotSession, Long> {

    Optional<PilotSession> findByCidAndCallsignAndLogonTimeAndSequenceNumber(
        Long cid, String callsign, Instant logonTime, int sequenceNumber );

    Optional<PilotSession> findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
        Long cid, String callsign, Instant logonTime );

    List<PilotSession> findByStatus( SessionStatus status );
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEventRepository.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotAirportEventRepository extends JpaRepository<PilotAirportEvent, Long> {
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSessionRepository.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AtcSessionRepository extends JpaRepository<AtcSession, Long> {

    Optional<AtcSession> findByCidAndCallsignAndLogonTime( Long cid, String callsign, Instant logonTime );

    List<AtcSession> findByEndedAtIsNull();
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=PilotSessionRepositoryIT`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/db/migration/V2__ingestion.sql ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain ingestion/src/test
git commit -m "feat: add ingestion schema (hypertables) and raw/derived JPA entities"
```

---

## Task 8: VATSIM v3 Data Feed Client + DTOs

**Files:**
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimFlightPlan.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimPilot.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimController.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeed.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimFeedException.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeedClient.java`
- Create: `ingestion/src/test/resources/vatsim-data-sample.json`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeedClientTest.java`

**Interfaces:**
- Produces: `record VatsimFlightPlan(String departure, String arrival, String aircraftShort)`, `record VatsimPilot(long cid, String callsign, double latitude, double longitude, int altitude, int groundspeed, Integer heading, String transponder, Integer qnhMb, Instant logonTime, VatsimFlightPlan flightPlan)`, `record VatsimController(long cid, String callsign, String frequency, Integer facility, Integer visualRange, Double latitude, Double longitude, Instant logonTime)`, `record VatsimDataFeed(List<VatsimPilot> pilots, List<VatsimController> controllers)`, unchecked `VatsimFeedException`, and `class VatsimDataFeedClient` with `VatsimDataFeed fetchCurrent()` (throws `VatsimFeedException` on any network/parse failure — Task 9's poller catches this). Deserialization is tolerant of unknown/missing fields (matches the reference project's `@JsonIgnoreProperties(ignoreUnknown = true)` convention).

- [ ] **Step 1: Add a small real-shaped fixture**

`ingestion/src/test/resources/vatsim-data-sample.json`:

```json
{
  "pilots": [
    {
      "cid": 123456,
      "callsign": "DLH400",
      "latitude": 50.0264,
      "longitude": 8.5431,
      "altitude": 3000,
      "groundspeed": 180,
      "heading": 270,
      "transponder": "2000",
      "qnh_mb": 1013,
      "logon_time": "2026-08-24T09:45:00.0000000Z",
      "flight_plan": {
        "flight_rules": "I",
        "aircraft": "A320/M-SDE2E3FGHIRWXY/LB1",
        "aircraft_short": "A320",
        "departure": "EDDF",
        "arrival": "EDDM"
      }
    },
    {
      "cid": 654321,
      "callsign": "DEABC",
      "latitude": 48.35,
      "longitude": 11.78,
      "altitude": 1200,
      "groundspeed": 60,
      "heading": 90,
      "transponder": "7000",
      "qnh_mb": 1015,
      "logon_time": "2026-08-24T09:50:00.0000000Z",
      "flight_plan": null
    }
  ],
  "controllers": [
    {
      "cid": 111222,
      "callsign": "EDDF_TWR",
      "frequency": "119.900",
      "facility": 4,
      "visual_range": 50,
      "logon_time": "2026-08-24T09:00:00.0000000Z"
    }
  ]
}
```

- [ ] **Step 2: Write the failing deserialization test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeedClientTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VatsimDataFeedClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule( new JavaTimeModule() );

    @Test
    void deserializesPilotsAndControllersFromRealShapedFeed() throws Exception {
        try( InputStream in = getClass().getResourceAsStream( "/vatsim-data-sample.json" ) ) {
            VatsimDataFeed feed = objectMapper.readValue( in, VatsimDataFeed.class );

            assertThat( feed.pilots() ).hasSize( 2 );
            VatsimPilot dlh400 = feed.pilots().get( 0 );
            assertThat( dlh400.cid() ).isEqualTo( 123456L );
            assertThat( dlh400.callsign() ).isEqualTo( "DLH400" );
            assertThat( dlh400.logonTime() ).isEqualTo( Instant.parse( "2026-08-24T09:45:00Z" ) );
            assertThat( dlh400.flightPlan() ).isNotNull();
            assertThat( dlh400.flightPlan().aircraftShort() ).isEqualTo( "A320" );
            assertThat( dlh400.flightPlan().departure() ).isEqualTo( "EDDF" );

            VatsimPilot vfrPilot = feed.pilots().get( 1 );
            assertThat( vfrPilot.flightPlan() ).isNull();

            assertThat( feed.controllers() ).hasSize( 1 );
            assertThat( feed.controllers().get( 0 ).callsign() ).isEqualTo( "EDDF_TWR" );
            assertThat( feed.controllers().get( 0 ).facility() ).isEqualTo( 4 );
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=VatsimDataFeedClientTest`
Expected: FAIL — DTO classes don't exist yet.

- [ ] **Step 4: Create the DTOs**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimFlightPlan.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimFlightPlan(
    String departure,
    String arrival,
    @JsonProperty( "aircraft_short" ) String aircraftShort ) {
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimPilot.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimPilot(
    long cid,
    String callsign,
    double latitude,
    double longitude,
    int altitude,
    int groundspeed,
    Integer heading,
    String transponder,
    @JsonProperty( "qnh_mb" ) Integer qnhMb,
    @JsonProperty( "logon_time" ) Instant logonTime,
    @JsonProperty( "flight_plan" ) VatsimFlightPlan flightPlan ) {
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimController.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimController(
    long cid,
    String callsign,
    String frequency,
    Integer facility,
    @JsonProperty( "visual_range" ) Integer visualRange,
    Double latitude,
    Double longitude,
    @JsonProperty( "logon_time" ) Instant logonTime ) {
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeed.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimDataFeed( List<VatsimPilot> pilots, List<VatsimController> controllers ) {
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=VatsimDataFeedClientTest`
Expected: PASS

- [ ] **Step 6: Add the HTTP client**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimFeedException.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

public class VatsimFeedException extends RuntimeException {

    public VatsimFeedException( String message, Throwable cause ) {
        super( message, cause );
    }
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi/VatsimDataFeedClient.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class VatsimDataFeedClient {

    private static final String FEED_URL = "https://data.vatsim.net/v3/vatsim-data.json";

    private final RestClient restClient;

    public VatsimDataFeedClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout( (int) Duration.ofSeconds( 10 ).toMillis() );
        requestFactory.setReadTimeout( (int) Duration.ofSeconds( 10 ).toMillis() );
        this.restClient = RestClient.builder().requestFactory( requestFactory ).build();
    }

    public VatsimDataFeed fetchCurrent() {
        try {
            VatsimDataFeed feed = restClient.get().uri( FEED_URL ).retrieve().body( VatsimDataFeed.class );
            if( feed == null ) {
                throw new VatsimFeedException( "VATSIM feed returned an empty body", null );
            }
            return feed;
        } catch( Exception e ) {
            throw new VatsimFeedException( "Failed to fetch/parse VATSIM data feed", e );
        }
    }
}
```

- [ ] **Step 7: Build to confirm compilation**

Run: `mvn -q -pl ingestion -am install`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/vatsimapi ingestion/src/test
git commit -m "feat: add VATSIM v3 data feed client and DTOs"
```

---

## Task 9: Ingestion Poller — Raw Data Persistence Every 15 Seconds

Per spec: raw data is bulk-inserted **before** any derived logic runs, individual bad records are skipped without discarding the cycle, and feed/DB failures skip the cycle rather than retrying aggressively.

**Files:**
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/PollResult.java`
- Modify: `app/src/main/resources/application.yml` (poll interval property)
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`

**Interfaces:**
- Consumes: `VatsimDataFeedClient` (Task 8), `PilotTrackPointRepository`, `AtcSnapshotRepository` (Task 7).
- Produces: `record PollResult(int trackPointsSaved, int atcSnapshotsSaved, int recordsSkipped)`, `class IngestionPoller` with `@Scheduled` method `void poll()` and a directly-callable `PollResult pollOnce()` (used by tests and, in Task 14, by the `HealthMonitor` success hook). Task 10 wraps `pollOnce()`'s output to drive session orchestration.

- [ ] **Step 1: Write the failing poller test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimController;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFeedException;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionPollerTest {

    private VatsimDataFeedClient feedClient;
    private PilotTrackPointRepository trackPointRepository;
    private AtcSnapshotRepository atcSnapshotRepository;
    private IngestionPoller poller;

    @BeforeEach
    void setUp() {
        feedClient = mock( VatsimDataFeedClient.class );
        trackPointRepository = mock( PilotTrackPointRepository.class );
        atcSnapshotRepository = mock( AtcSnapshotRepository.class );
        poller = new IngestionPoller( feedClient, trackPointRepository, atcSnapshotRepository );
    }

    @Test
    void savesAllValidPilotsAndControllersFromOneCycle() {
        VatsimPilot pilot = new VatsimPilot(
            123456L, "DLH400", 50.0264, 8.5431, 3000, 180, 270, "2000", 1013,
            Instant.parse( "2026-08-24T09:45:00Z" ),
            new VatsimFlightPlan( "EDDF", "EDDM", "A320" ) );
        VatsimController controller = new VatsimController(
            111222L, "EDDF_TWR", "119.900", 4, 50, null, null,
            Instant.parse( "2026-08-24T09:00:00Z" ) );
        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( pilot ), List.of( controller ) ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isEqualTo( 1 );
        assertThat( result.atcSnapshotsSaved() ).isEqualTo( 1 );
        assertThat( result.recordsSkipped() ).isEqualTo( 0 );

        ArgumentCaptor<List<PilotTrackPoint>> trackPointsCaptor = ArgumentCaptor.forClass( List.class );
        verify( trackPointRepository ).saveAll( trackPointsCaptor.capture() );
        PilotTrackPoint saved = trackPointsCaptor.getValue().get( 0 );
        assertThat( saved.getCid() ).isEqualTo( 123456L );
        assertThat( saved.getCallsign() ).isEqualTo( "DLH400" );
        assertThat( saved.getFlightPlanDeparture() ).isEqualTo( "EDDF" );
        assertThat( saved.getAircraftShort() ).isEqualTo( "A320" );

        ArgumentCaptor<List<AtcSnapshot>> atcCaptor = ArgumentCaptor.forClass( List.class );
        verify( atcSnapshotRepository ).saveAll( atcCaptor.capture() );
        assertThat( atcCaptor.getValue().get( 0 ).getCallsign() ).isEqualTo( "EDDF_TWR" );
    }

    @Test
    void skipsAPilotWithABlankCallsignWithoutFailingTheWholeCycle() {
        VatsimPilot valid = new VatsimPilot(
            1L, "DLH400", 50.0, 8.5, 3000, 180, 270, "2000", 1013, Instant.now(), null );
        VatsimPilot invalid = new VatsimPilot(
            2L, "  ", 50.0, 8.5, 3000, 180, 270, "2000", 1013, Instant.now(), null );
        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( valid, invalid ), List.of() ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isEqualTo( 1 );
        assertThat( result.recordsSkipped() ).isEqualTo( 1 );
    }

    @Test
    void returnsAnEmptyResultWithoutThrowingWhenTheFeedFails() {
        when( feedClient.fetchCurrent() ).thenThrow( new VatsimFeedException( "boom", null ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isZero();
        assertThat( result.atcSnapshotsSaved() ).isZero();
        assertThat( result.recordsSkipped() ).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: FAIL — `IngestionPoller`/`PollResult` don't exist yet.

- [ ] **Step 3: Implement PollResult and the poller**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/PollResult.java`:

```java
package de.secretsoft.vatsim_stats.ingestion;

public record PollResult( int trackPointsSaved, int atcSnapshotsSaved, int recordsSkipped ) {

    public static final PollResult EMPTY = new PollResult( 0, 0, 0 );
}
```

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`:

```java
package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimController;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionPoller {

    private final VatsimDataFeedClient feedClient;
    private final PilotTrackPointRepository trackPointRepository;
    private final AtcSnapshotRepository atcSnapshotRepository;

    @Scheduled( fixedRateString = "${vatsim.poll-interval-ms:15000}" )
    public void poll() {
        pollOnce();
    }

    @Transactional
    public PollResult pollOnce() {
        VatsimDataFeed feed;
        try {
            feed = feedClient.fetchCurrent();
        } catch( Exception e ) {
            log.warn( "Skipping poll cycle: failed to fetch VATSIM data feed", e );
            return PollResult.EMPTY;
        }

        Instant recordedAt = Instant.now();
        int skipped = 0;

        List<PilotTrackPoint> trackPoints = new ArrayList<>();
        for( VatsimPilot pilot : feed.pilots() ) {
            if( pilot.callsign() == null || pilot.callsign().isBlank() ) {
                skipped++;
                continue;
            }
            trackPoints.add( toTrackPoint( pilot, recordedAt ) );
        }

        List<AtcSnapshot> atcSnapshots = new ArrayList<>();
        for( VatsimController controller : feed.controllers() ) {
            if( controller.callsign() == null || controller.callsign().isBlank() ) {
                skipped++;
                continue;
            }
            atcSnapshots.add( toAtcSnapshot( controller, recordedAt ) );
        }

        if( !trackPoints.isEmpty() ) {
            trackPointRepository.saveAll( trackPoints );
        }
        if( !atcSnapshots.isEmpty() ) {
            atcSnapshotRepository.saveAll( atcSnapshots );
        }

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
    }

    private PilotTrackPoint toTrackPoint( VatsimPilot pilot, Instant recordedAt ) {
        VatsimFlightPlan flightPlan = pilot.flightPlan();
        return PilotTrackPoint.builder()
            .recordedAt( recordedAt )
            .cid( pilot.cid() )
            .callsign( pilot.callsign() )
            .logonTime( pilot.logonTime() )
            .latitude( pilot.latitude() )
            .longitude( pilot.longitude() )
            .altitudeFt( pilot.altitude() )
            .groundspeedKt( pilot.groundspeed() )
            .heading( pilot.heading() )
            .transponder( pilot.transponder() )
            .qnhMb( pilot.qnhMb() )
            .flightPlanDeparture( flightPlan != null ? flightPlan.departure() : null )
            .flightPlanDestination( flightPlan != null ? flightPlan.arrival() : null )
            .aircraftShort( flightPlan != null ? flightPlan.aircraftShort() : null )
            .build();
    }

    private AtcSnapshot toAtcSnapshot( VatsimController controller, Instant recordedAt ) {
        return AtcSnapshot.builder()
            .recordedAt( recordedAt )
            .cid( controller.cid() )
            .callsign( controller.callsign() )
            .logonTime( controller.logonTime() )
            .frequency( controller.frequency() )
            .facility( controller.facility() )
            .visualRange( controller.visualRange() )
            .latitude( controller.latitude() )
            .longitude( controller.longitude() )
            .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Add the poll interval property**

Edit `app/src/main/resources/application.yml`, add at the top level:

```yaml
vatsim:
  poll-interval-ms: 15000
```

- [ ] **Step 6: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion ingestion/src/test app/src/main/resources/application.yml
git commit -m "feat: add ingestion poller persisting raw pilot/ATC data every 15s"
```

---

## Task 10: Nearest-Airport DB Lookup Adapter

Wires the `detection` module's `NearestAirportLookup` contract (Task 5) to the real `AirportRepository` (Task 2), using a bounding-box SQL prefilter plus Haversine ranking in Java — avoids scanning the whole `airport` table on every one of the ~15s cycles.

**Files:**
- Modify: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/AirportRepository.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/AirportRepositoryLookup.java`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/AirportRepositoryLookupTest.java`

**Interfaces:**
- Consumes: `AirportRepository` (Task 2), `NearestAirportLookup`/`AirportRef`/`Haversine` (Task 5).
- Produces: `AirportRepository.findByLatitudeBetweenAndLongitudeBetween(double, double, double, double): List<Airport>`, and `class AirportRepositoryLookup implements NearestAirportLookup` — the production bean Task 11 injects into `PilotSessionOrchestrator`.

- [ ] **Step 1: Add the bounding-box finder to AirportRepository**

Edit `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/AirportRepository.java`, replace its full content with:

```java
package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirportRepository extends JpaRepository<Airport, String> {

    List<Airport> findByLatitudeBetweenAndLongitudeBetween(
        double minLatitude, double maxLatitude, double minLongitude, double maxLongitude );
}
```

- [ ] **Step 2: Write the failing lookup test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/AirportRepositoryLookupTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirportRepositoryLookupTest {

    private AirportRepository airportRepository;
    private AirportRepositoryLookup lookup;

    @BeforeEach
    void setUp() {
        airportRepository = mock( AirportRepository.class );
        lookup = new AirportRepositoryLookup( airportRepository );
    }

    @Test
    void returnsTheClosestCandidateWithinRadius() {
        Airport frankfurt = Airport.builder()
            .icao( "EDDF" ).name( "Frankfurt" ).latitude( 50.0264 ).longitude( 8.5431 ).elevationFt( 364 ).build();
        Airport egelsbach = Airport.builder()
            .icao( "EDFE" ).name( "Egelsbach" ).latitude( 49.9601 ).longitude( 8.6461 ).elevationFt( 384 ).build();
        when( airportRepository.findByLatitudeBetweenAndLongitudeBetween( anyDouble(), anyDouble(), anyDouble(), anyDouble() ) )
            .thenReturn( List.of( frankfurt, egelsbach ) );

        Optional<AirportRef> nearest = lookup.findNearest( 50.03, 8.55, 10.0 );

        assertThat( nearest ).isPresent();
        assertThat( nearest.get().icao() ).isEqualTo( "EDDF" );
        assertThat( nearest.get().elevationFt() ).isEqualTo( 364.0 );
    }

    @Test
    void returnsEmptyWhenNoCandidateIsWithinRadius() {
        Airport farAway = Airport.builder()
            .icao( "KJFK" ).name( "JFK" ).latitude( 40.6413 ).longitude( -73.7781 ).elevationFt( 13 ).build();
        when( airportRepository.findByLatitudeBetweenAndLongitudeBetween( anyDouble(), anyDouble(), anyDouble(), anyDouble() ) )
            .thenReturn( List.of( farAway ) );

        Optional<AirportRef> nearest = lookup.findNearest( 50.03, 8.55, 5.0 );

        assertThat( nearest ).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=AirportRepositoryLookupTest`
Expected: FAIL — `AirportRepositoryLookup` doesn't exist yet.

- [ ] **Step 4: Implement the lookup**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/AirportRepositoryLookup.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.Haversine;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AirportRepositoryLookup implements NearestAirportLookup {

    private final AirportRepository airportRepository;

    @Override
    public Optional<AirportRef> findNearest( double latitude, double longitude, double radiusNm ) {
        double latDeltaDeg = radiusNm / 60.0;
        double lonDeltaDeg = latDeltaDeg / Math.max( 0.1, Math.cos( Math.toRadians( latitude ) ) );

        List<Airport> candidates = airportRepository.findByLatitudeBetweenAndLongitudeBetween(
            latitude - latDeltaDeg, latitude + latDeltaDeg,
            longitude - lonDeltaDeg, longitude + lonDeltaDeg );

        Airport nearest = null;
        double nearestDistanceNm = Double.MAX_VALUE;
        for( Airport candidate : candidates ) {
            double distanceNm = Haversine.distanceNm( latitude, longitude, candidate.getLatitude(), candidate.getLongitude() );
            if( distanceNm <= radiusNm && distanceNm < nearestDistanceNm ) {
                nearest = candidate;
                nearestDistanceNm = distanceNm;
            }
        }

        if( nearest == null ) {
            return Optional.empty();
        }
        double elevationFt = nearest.getElevationFt() != null ? nearest.getElevationFt() : 0;
        return Optional.of( new AirportRef( nearest.getIcao(), nearest.getLatitude(), nearest.getLongitude(), elevationFt ) );
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=AirportRepositoryLookupTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/AirportRepository.java ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session ingestion/src/test
git commit -m "feat: add DB-backed nearest-airport lookup with bounding-box prefilter"
```

---

## Task 11: Pilot Session Orchestrator

Wires `PilotPhaseStateMachine` (Task 6) to persistence, applying the spec's session-boundary rules: a session is keyed by CID+Callsign+`logon_time`; a new **leg** (`sequence_number`) opens either when a `COMPLETED` session receives a new/changed flight plan (refile after landing, still on the ground), or — as a fallback covering pure-VFR pilots who never file a plan — when a `TAKEOFF` event is observed while the attached session is still `COMPLETED` from a prior full-stop landing. Diversions/local-IFR destination changes while `ACTIVE`/airborne only update the current session's planned fields, never split it.

**Files:**
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/SessionKey.java`
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestrator.java`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestratorTest.java`

**Interfaces:**
- Consumes: `PilotPhaseStateMachine`, `TrackSample`, `AirportEvent`, `AirportEventType`, `PhaseDetectionConfig` (Task 6); `NearestAirportLookup` (Task 5, implemented by Task 10's `AirportRepositoryLookup`); `PilotTrackPoint`, `PilotSession`, `SessionStatus`, `PilotAirportEvent`, `PilotSessionRepository`, `PilotAirportEventRepository` (Task 7).
- Produces: `record SessionKey(long cid, String callsign, Instant logonTime)`, `class PilotSessionOrchestrator` with `@Transactional void processTrackPoints(List<PilotTrackPoint> trackPoints)`. Task 12 (Poller wiring + restart reconstruction) calls `processTrackPoints(...)` after each raw-data save and seeds this class's internal state maps on startup.

- [ ] **Step 1: Write the failing orchestrator tests**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestratorTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PilotSessionOrchestratorTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );

    private final NearestAirportLookup lookup = ( lat, lon, radius ) -> Optional.of( EDDF );

    private InMemoryPilotSessionRepository sessionRepository;
    private PilotAirportEventRepository eventRepository;
    private PilotSessionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        sessionRepository = new InMemoryPilotSessionRepository();
        eventRepository = mock( PilotAirportEventRepository.class );
        when( eventRepository.save( org.mockito.ArgumentMatchers.any() ) )
            .thenAnswer( invocation -> invocation.getArgument( 0 ) );
        orchestrator = new PilotSessionOrchestrator( sessionRepository, eventRepository, lookup );
    }

    private PilotTrackPoint point( int offsetSeconds, double altitudeFt, double groundspeedKt,
                                    String departure, String destination ) {
        return PilotTrackPoint.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .latitude( 50.0 )
            .longitude( 8.5 )
            .altitudeFt( (int) altitudeFt )
            .groundspeedKt( (int) groundspeedKt )
            .flightPlanDeparture( departure )
            .flightPlanDestination( destination )
            .aircraftShort( "A320" )
            .build();
    }

    @Test
    void createsAnActiveSessionOnFirstTrackPoint() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( session.getSequenceNumber() ).isZero();
        assertThat( session.getPlannedDeparture() ).isEqualTo( "EDDF" );
        assertThat( session.getPlannedDestination() ).isEqualTo( "EDDM" );
    }

    @Test
    void completesSessionAfterLandingDwellThresholdAndOpensNewLegOnRefile() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );
        for( int offset = 15; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( point( offset, 550, 15, "EDDF", "EDDM" ) ) );
        }

        PilotSession firstLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( firstLeg.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        assertThat( firstLeg.getSequenceNumber() ).isZero();

        orchestrator.processTrackPoints( List.of( point( 135, 550, 5, "EDDF", "EDDL" ) ) );

        PilotSession secondLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( secondLeg.getSequenceNumber() ).isEqualTo( 1 );
        assertThat( secondLeg.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( secondLeg.getPlannedDestination() ).isEqualTo( "EDDL" );
    }

    @Test
    void opensNewLegOnTakeoffAfterCompletedSessionEvenWithoutAFlightPlanChange() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, null, null ) ) );
        for( int offset = 15; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( point( offset, 550, 15, null, null ) ) );
        }
        PilotSession firstLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( firstLeg.getStatus() ).isEqualTo( SessionStatus.COMPLETED );

        orchestrator.processTrackPoints( List.of( point( 135, 3000, 180, null, null ) ) );

        PilotSession secondLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( secondLeg.getSequenceNumber() ).isEqualTo( 1 );
        assertThat( secondLeg.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
    }

    @Test
    void diversionWhileAirborneUpdatesTheSameSessionInstead() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );
        orchestrator.processTrackPoints( List.of( point( 15, 3000, 250, "EDDF", "EDDL" ) ) );

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getSequenceNumber() ).isZero();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( session.getPlannedDestination() ).isEqualTo( "EDDL" );
    }

}
```

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/InMemoryPilotSessionRepository.java` (a hand-written test double — full `JpaRepository` surface isn't needed, so the untested methods fail loudly instead of silently doing the wrong thing; shared by this task's test and Task 13's restart-reconstruction test):

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryPilotSessionRepository implements PilotSessionRepository {

    private final Map<Long, PilotSession> byId = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<PilotSession> findByCidAndCallsignAndLogonTimeAndSequenceNumber(
        Long cid, String callsign, Instant logonTime, int sequenceNumber ) {
        return byId.values().stream()
            .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                && s.getLogonTime().equals( logonTime ) && s.getSequenceNumber() == sequenceNumber )
            .findFirst();
    }

    @Override
    public Optional<PilotSession> findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
        Long cid, String callsign, Instant logonTime ) {
        return byId.values().stream()
            .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                && s.getLogonTime().equals( logonTime ) )
            .max( Comparator.comparingInt( PilotSession::getSequenceNumber ) );
    }

    @Override
    public List<PilotSession> findByStatus( SessionStatus status ) {
        return byId.values().stream().filter( s -> s.getStatus() == status ).toList();
    }

    @Override
    public <S extends PilotSession> S save( S entity ) {
        if( entity.getId() == null ) {
            entity.setId( sequence.incrementAndGet() );
        }
        byId.put( entity.getId(), entity );
        return entity;
    }

    @Override
    public Optional<PilotSession> findById( Long id ) {
        return Optional.ofNullable( byId.get( id ) );
    }

    // Remaining JpaRepository methods are unused by the orchestrator and intentionally left
    // unimplemented for this in-memory test double.
    @Override
    public <S extends PilotSession> List<S> saveAll( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public boolean existsById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAll() { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAllById( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public long count() { throw new UnsupportedOperationException(); }
    @Override
    public void deleteById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public void delete( PilotSession entity ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllById( Iterable<? extends Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAll( Iterable<? extends PilotSession> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAll() { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAll( org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
    @Override
    public org.springframework.data.domain.Page<PilotSession> findAll( org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
    @Override
    public void flush() { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> S saveAndFlush( S entity ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> saveAllAndFlush( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllInBatch( Iterable<PilotSession> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllByIdInBatch( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getOne( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getReferenceById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> Optional<S> findOne( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> findAll( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> org.springframework.data.domain.Page<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> long count( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> boolean exists( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession, R> R findBy( org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction ) { throw new UnsupportedOperationException(); }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=PilotSessionOrchestratorTest`
Expected: FAIL — `SessionKey`/`PilotSessionOrchestrator` don't exist yet.

- [ ] **Step 3: Create SessionKey**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/SessionKey.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import java.time.Instant;

public record SessionKey( long cid, String callsign, Instant logonTime ) {
}
```

- [ ] **Step 4: Implement the orchestrator**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestrator.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportEvent;
import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.detection.PhaseDetectionConfig;
import de.secretsoft.vatsim_stats.detection.PilotPhaseStateMachine;
import de.secretsoft.vatsim_stats.detection.TrackSample;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class PilotSessionOrchestrator {

    private final PilotSessionRepository pilotSessionRepository;
    private final PilotAirportEventRepository pilotAirportEventRepository;
    private final NearestAirportLookup airportLookup;

    private final ConcurrentMap<SessionKey, PilotPhaseStateMachine> stateMachines = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, PilotSession> currentSessions = new ConcurrentHashMap<>();

    @Transactional
    public void processTrackPoints( List<PilotTrackPoint> trackPoints ) {
        for( PilotTrackPoint point : trackPoints ) {
            handleTrackPoint( point );
        }
    }

    void seed( SessionKey key, PilotPhaseStateMachine machine, PilotSession session ) {
        stateMachines.put( key, machine );
        currentSessions.put( key, session );
    }

    private void handleTrackPoint( PilotTrackPoint point ) {
        SessionKey key = new SessionKey( point.getCid(), point.getCallsign(), point.getLogonTime() );
        PilotPhaseStateMachine machine = stateMachines.computeIfAbsent(
            key, k -> new PilotPhaseStateMachine( PhaseDetectionConfig.defaults(), airportLookup ) );
        PilotSession session = currentSessions.computeIfAbsent( key, k -> loadOrCreateSession( k, point ) );

        session = openNewLegIfFlightPlanChanged( session, point );
        session = updatePlannedFieldsIfActive( session, point );

        TrackSample sample = new TrackSample(
            point.getRecordedAt(), point.getLatitude(), point.getLongitude(),
            point.getAltitudeFt(), point.getGroundspeedKt() );
        List<AirportEvent> events = machine.process( sample );

        for( AirportEvent event : events ) {
            if( event.type() == AirportEventType.TAKEOFF && session.getStatus() == SessionStatus.COMPLETED ) {
                session = openNewLeg( session, point, event.timestamp() );
            }

            pilotAirportEventRepository.save( PilotAirportEvent.builder()
                .pilotSession( session )
                .airportIcao( event.airportIcao() )
                .eventType( event.type() )
                .occurredAt( event.timestamp() )
                .build() );

            if( event.type() == AirportEventType.LANDING ) {
                session.setStatus( SessionStatus.COMPLETED );
                session.setEndedAt( event.timestamp() );
                session = pilotSessionRepository.save( session );
            }
        }

        currentSessions.put( key, session );
    }

    private PilotSession loadOrCreateSession( SessionKey key, PilotTrackPoint point ) {
        return pilotSessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( key.cid(), key.callsign(), key.logonTime() )
            .orElseGet( () -> createSession( key, 0, point.getFlightPlanDeparture(), point.getFlightPlanDestination(),
                point.getAircraftShort(), point.getRecordedAt() ) );
    }

    private PilotSession createSession(
        SessionKey key, int sequenceNumber, String plannedDeparture, String plannedDestination,
        String aircraftShort, Instant startedAt ) {

        PilotSession session = PilotSession.builder()
            .cid( key.cid() )
            .callsign( key.callsign() )
            .logonTime( key.logonTime() )
            .sequenceNumber( sequenceNumber )
            .plannedDeparture( plannedDeparture )
            .plannedDestination( plannedDestination )
            .aircraftShort( aircraftShort )
            .status( SessionStatus.ACTIVE )
            .startedAt( startedAt )
            .build();
        return pilotSessionRepository.save( session );
    }

    private PilotSession openNewLeg( PilotSession completed, PilotTrackPoint point, Instant startedAt ) {
        return createSession(
            new SessionKey( completed.getCid(), completed.getCallsign(), completed.getLogonTime() ),
            completed.getSequenceNumber() + 1,
            point.getFlightPlanDeparture(), point.getFlightPlanDestination(), point.getAircraftShort(),
            startedAt );
    }

    private PilotSession openNewLegIfFlightPlanChanged( PilotSession session, PilotTrackPoint point ) {
        if( session.getStatus() != SessionStatus.COMPLETED ) {
            return session;
        }
        boolean hasAnyPlan = point.getFlightPlanDeparture() != null || point.getFlightPlanDestination() != null;
        boolean changed = !Objects.equals( point.getFlightPlanDeparture(), session.getPlannedDeparture() )
            || !Objects.equals( point.getFlightPlanDestination(), session.getPlannedDestination() );
        if( hasAnyPlan && changed ) {
            return openNewLeg( session, point, point.getRecordedAt() );
        }
        return session;
    }

    private PilotSession updatePlannedFieldsIfActive( PilotSession session, PilotTrackPoint point ) {
        if( session.getStatus() != SessionStatus.ACTIVE ) {
            return session;
        }
        boolean changed = false;
        if( !Objects.equals( session.getPlannedDeparture(), point.getFlightPlanDeparture() ) ) {
            session.setPlannedDeparture( point.getFlightPlanDeparture() );
            changed = true;
        }
        if( !Objects.equals( session.getPlannedDestination(), point.getFlightPlanDestination() ) ) {
            session.setPlannedDestination( point.getFlightPlanDestination() );
            changed = true;
        }
        if( !Objects.equals( session.getAircraftShort(), point.getAircraftShort() ) ) {
            session.setAircraftShort( point.getAircraftShort() );
            changed = true;
        }
        if( changed ) {
            session = pilotSessionRepository.save( session );
        }
        return session;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=PilotSessionOrchestratorTest`
Expected: PASS — all 4 tests green.

- [ ] **Step 6: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session ingestion/src/test
git commit -m "feat: add pilot session orchestrator wiring phase detection to persistence"
```

---

## Task 12: Wire Session Orchestration into the Poller

Per spec, raw persistence always happens **before** derived logic. This task extends `IngestionPoller` (Task 9) to call `PilotSessionOrchestrator.processTrackPoints(...)` with the just-saved track points, immediately after the raw-data transaction succeeds.

**Files:**
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`
- Modify: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`

**Interfaces:**
- Consumes: `PilotSessionOrchestrator.processTrackPoints(List<PilotTrackPoint>)` (Task 11).
- Produces: `IngestionPoller` now takes a 4th constructor argument `PilotSessionOrchestrator sessionOrchestrator`; `pollOnce()`'s contract (`PollResult`) is unchanged.

- [ ] **Step 1: Update the poller test to inject and verify the orchestrator call**

Edit `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`:

Add the import `import de.secretsoft.vatsim_stats.ingestion.session.PilotSessionOrchestrator;` and `import static org.mockito.Mockito.verify;` (already present), then change `setUp()` and add one assertion:

```java
    private PilotSessionOrchestrator sessionOrchestrator;

    @BeforeEach
    void setUp() {
        feedClient = mock( VatsimDataFeedClient.class );
        trackPointRepository = mock( PilotTrackPointRepository.class );
        atcSnapshotRepository = mock( AtcSnapshotRepository.class );
        sessionOrchestrator = mock( PilotSessionOrchestrator.class );
        poller = new IngestionPoller( feedClient, trackPointRepository, atcSnapshotRepository, sessionOrchestrator );
    }
```

In `savesAllValidPilotsAndControllersFromOneCycle()`, add after the existing assertions:

```java
        verify( sessionOrchestrator ).processTrackPoints( trackPointsCaptor.getValue() );
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: FAIL — constructor signature mismatch / orchestrator never called.

- [ ] **Step 3: Update IngestionPoller to call the orchestrator**

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`:

Add the import `import de.secretsoft.vatsim_stats.ingestion.session.PilotSessionOrchestrator;`, add the field and constructor is generated by `@RequiredArgsConstructor` (no manual constructor to edit):

```java
    private final PilotSessionOrchestrator sessionOrchestrator;
```

Place it directly below the `atcSnapshotRepository` field declaration. Then change the end of `pollOnce()` from:

```java
        if( !trackPoints.isEmpty() ) {
            trackPointRepository.saveAll( trackPoints );
        }
        if( !atcSnapshots.isEmpty() ) {
            atcSnapshotRepository.saveAll( atcSnapshots );
        }

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

to:

```java
        if( !trackPoints.isEmpty() ) {
            trackPointRepository.saveAll( trackPoints );
        }
        if( !atcSnapshots.isEmpty() ) {
            atcSnapshotRepository.saveAll( atcSnapshots );
        }

        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java
git commit -m "feat: run session orchestration after each raw ingestion cycle"
```

---

## Task 13: Restart-Safe State Reconstruction

Per spec: on startup, rebuild each `ACTIVE` pilot session's in-memory phase state from its last few raw track points, so a restart never fabricates a spurious `TAKEOFF`/`LANDING` event and never loses track of where a pilot's flight actually stands.

**Files:**
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestrator.java`
- Modify: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestratorTest.java`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionRestartReconstructionTest.java`

**Interfaces:**
- Consumes: `PilotTrackPointRepository.findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(...)` (Task 7), `PilotPhaseStateMachine` (Task 6).
- Produces: `PilotSessionOrchestrator` gains a 4th constructor dependency `PilotTrackPointRepository` and a `@PostConstruct void reconstructActiveSessions()` that runs once at bean initialization — before Spring's scheduler can fire the first `@Scheduled` poll, since scheduling only starts after all singleton beans are initialized.

- [ ] **Step 1: Update the existing orchestrator test's constructor calls**

Edit `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestratorTest.java`: add the import `import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;`, then in `setUp()` change:

```java
        orchestrator = new PilotSessionOrchestrator( sessionRepository, eventRepository, lookup );
```

to:

```java
        orchestrator = new PilotSessionOrchestrator(
            sessionRepository, eventRepository, lookup, mock( PilotTrackPointRepository.class ) );
```

- [ ] **Step 2: Write the failing restart-reconstruction test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionRestartReconstructionTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PilotSessionRestartReconstructionTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );
    private static final NearestAirportLookup ALWAYS_EDDF = ( lat, lon, radius ) -> Optional.of( EDDF );

    @Test
    void resumesAGroundPendingSessionAfterRestartAndEmitsLandingOnceDwellIsReached() {
        InMemoryPilotSessionRepository sessionRepository = new InMemoryPilotSessionRepository();
        PilotSession activeSession = sessionRepository.save( PilotSession.builder()
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .sequenceNumber( 0 )
            .status( SessionStatus.ACTIVE )
            .startedAt( LOGON )
            .build() );

        PilotTrackPointRepository trackPointRepository = mock( PilotTrackPointRepository.class );
        when( trackPointRepository.findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
            123456L, "DLH400", LOGON ) ).thenReturn( List.of(
            trackPoint( 30, 550, 15 ),
            trackPoint( 15, 3000, 250 ),
            trackPoint( 0, 3000, 250 )
        ) );

        PilotAirportEventRepository eventRepository = mock( PilotAirportEventRepository.class );
        when( eventRepository.save( any() ) ).thenAnswer( invocation -> invocation.getArgument( 0 ) );

        PilotSessionOrchestrator orchestrator = new PilotSessionOrchestrator(
            sessionRepository, eventRepository, ALWAYS_EDDF, trackPointRepository );
        orchestrator.reconstructActiveSessions();

        for( int offset = 45; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( trackPoint( offset, 550, 15 ) ) );
        }

        ArgumentCaptor<PilotAirportEvent> eventCaptor = ArgumentCaptor.forClass( PilotAirportEvent.class );
        verify( eventRepository ).save( eventCaptor.capture() );
        assertThat( eventCaptor.getValue().getEventType() ).isEqualTo( AirportEventType.LANDING );
        assertThat( eventCaptor.getValue().getOccurredAt() ).isEqualTo( LOGON.plusSeconds( 30 ) );

        PilotSession updated = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( updated.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
    }

    private static PilotTrackPoint trackPoint( int offsetSeconds, double altitudeFt, double groundspeedKt ) {
        return PilotTrackPoint.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .latitude( 50.0 )
            .longitude( 8.5 )
            .altitudeFt( (int) altitudeFt )
            .groundspeedKt( (int) groundspeedKt )
            .build();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q -pl ingestion test -Dtest=PilotSessionRestartReconstructionTest,PilotSessionOrchestratorTest`
Expected: FAIL — 4-arg constructor and `reconstructActiveSessions()` don't exist yet.

- [ ] **Step 4: Add the reconstruction dependency and method**

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/PilotSessionOrchestrator.java`:

Add imports `import de.secretsoft.vatsim_stats.detection.TrackSample;` (already present), `import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;`, `import jakarta.annotation.PostConstruct;`, `import java.util.Collections;`.

Add the field directly below `airportLookup`:

```java
    private final PilotTrackPointRepository pilotTrackPointRepository;
```

Add this method (placed after `processTrackPoints`):

```java
    @PostConstruct
    void reconstructActiveSessions() {
        for( PilotSession session : pilotSessionRepository.findByStatus( SessionStatus.ACTIVE ) ) {
            SessionKey key = new SessionKey( session.getCid(), session.getCallsign(), session.getLogonTime() );
            List<PilotTrackPoint> recentPointsNewestFirst = pilotTrackPointRepository
                .findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
                    session.getCid(), session.getCallsign(), session.getLogonTime() );

            PilotPhaseStateMachine machine =
                new PilotPhaseStateMachine( PhaseDetectionConfig.defaults(), airportLookup );
            List<PilotTrackPoint> chronological = new java.util.ArrayList<>( recentPointsNewestFirst );
            Collections.reverse( chronological );
            for( PilotTrackPoint point : chronological ) {
                machine.process( new TrackSample(
                    point.getRecordedAt(), point.getLatitude(), point.getLongitude(),
                    point.getAltitudeFt(), point.getGroundspeedKt() ) );
            }

            stateMachines.put( key, machine );
            currentSessions.put( key, session );
        }
    }
```

Remove the now-redundant package-private `seed(...)` helper method (this method replaces its only purpose) and update the class's constructor to include the new field — since the class uses `@RequiredArgsConstructor`, simply placing the new `private final` field declaration is enough; Lombok regenerates the constructor with all four fields in declaration order (`pilotSessionRepository, pilotAirportEventRepository, airportLookup, pilotTrackPointRepository`), matching the test call `new PilotSessionOrchestrator(sessionRepository, eventRepository, lookup, mock(PilotTrackPointRepository.class))`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -pl ingestion test -Dtest=PilotSessionRestartReconstructionTest,PilotSessionOrchestratorTest`
Expected: PASS

- [ ] **Step 6: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session
git commit -m "feat: reconstruct pilot phase state from recent track points on startup"
```

---

## Task 14: ATC Session Tracking (Login/Logout)

Per spec, ATC needs no phase model: a session opens when CID+Callsign+`logon_time` first appears, and closes (using the last-seen snapshot's timestamp, not "now") once it stops appearing in the feed. Restart-safe via the same `findByEndedAtIsNull()` reload pattern as pilot sessions.

**Files:**
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/AtcSessionTracker.java`
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`
- Modify: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`
- Test: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/AtcSessionTrackerTest.java`

**Interfaces:**
- Consumes: `SessionKey` (Task 11), `AtcSnapshot`, `AtcSession`, `AtcSessionRepository` (Task 7).
- Produces: `class AtcSessionTracker` with `@Transactional void processSnapshots(List<AtcSnapshot> snapshots)` and `@PostConstruct void reconstructOpenSessions()`. `IngestionPoller` gets a 5th constructor dependency and calls `processSnapshots(...)` alongside `sessionOrchestrator.processTrackPoints(...)`.

- [ ] **Step 1: Write the failing tracker test**

`ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/session/AtcSessionTrackerTest.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AtcSessionTrackerTest {

    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );

    private FakeAtcSessionRepository repository;
    private AtcSessionTracker tracker;

    @BeforeEach
    void setUp() {
        repository = new FakeAtcSessionRepository();
        tracker = new AtcSessionTracker( repository );
    }

    private AtcSnapshot snapshot( int offsetSeconds ) {
        return AtcSnapshot.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 111222L )
            .callsign( "EDDF_TWR" )
            .logonTime( LOGON )
            .frequency( "119.900" )
            .facility( 4 )
            .build();
    }

    @Test
    void opensASessionOnFirstAppearanceAndDoesNotDuplicateIt() {
        tracker.processSnapshots( List.of( snapshot( 0 ) ) );
        tracker.processSnapshots( List.of( snapshot( 15 ) ) );

        List<AtcSession> all = repository.all();
        assertThat( all ).hasSize( 1 );
        assertThat( all.get( 0 ).getEndedAt() ).isNull();
        assertThat( all.get( 0 ).getStartedAt() ).isEqualTo( LOGON );
    }

    @Test
    void closesTheSessionWithTheLastSeenTimestampWhenTheControllerDisappears() {
        tracker.processSnapshots( List.of( snapshot( 0 ) ) );
        tracker.processSnapshots( List.of( snapshot( 15 ) ) );
        tracker.processSnapshots( List.of() );

        AtcSession session = repository.all().get( 0 );
        assertThat( session.getEndedAt() ).isEqualTo( LOGON.plusSeconds( 15 ) );
    }

    @Test
    void reconstructsOpenSessionsOnStartup() {
        repository.save( AtcSession.builder()
            .cid( 111222L ).callsign( "EDDF_TWR" ).logonTime( LOGON )
            .facility( 4 ).startedAt( LOGON ).build() );

        AtcSessionTracker restarted = new AtcSessionTracker( repository );
        restarted.reconstructOpenSessions();
        restarted.processSnapshots( List.of() );

        AtcSession session = repository.all().get( 0 );
        assertThat( session.getEndedAt() ).isEqualTo( LOGON );
    }

    private static class FakeAtcSessionRepository implements AtcSessionRepository {

        private final Map<Long, AtcSession> byId = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        List<AtcSession> all() {
            return List.copyOf( byId.values() );
        }

        @Override
        public Optional<AtcSession> findByCidAndCallsignAndLogonTime( Long cid, String callsign, Instant logonTime ) {
            return byId.values().stream()
                .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                    && s.getLogonTime().equals( logonTime ) )
                .findFirst();
        }

        @Override
        public List<AtcSession> findByEndedAtIsNull() {
            return byId.values().stream().filter( s -> s.getEndedAt() == null ).toList();
        }

        @Override
        public <S extends AtcSession> S save( S entity ) {
            if( entity.getId() == null ) {
                entity.setId( sequence.incrementAndGet() );
            }
            byId.put( entity.getId(), entity );
            return entity;
        }

        @Override
        public Optional<AtcSession> findById( Long id ) { return Optional.ofNullable( byId.get( id ) ); }
        @Override
        public <S extends AtcSession> List<S> saveAll( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public boolean existsById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAll() { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAllById( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public long count() { throw new UnsupportedOperationException(); }
        @Override
        public void deleteById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public void delete( AtcSession entity ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllById( Iterable<? extends Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAll( Iterable<? extends AtcSession> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAll( org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
        @Override
        public org.springframework.data.domain.Page<AtcSession> findAll( org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
        @Override
        public void flush() { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> S saveAndFlush( S entity ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> saveAllAndFlush( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllInBatch( Iterable<AtcSession> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllByIdInBatch( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getOne( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getReferenceById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> Optional<S> findOne( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> findAll( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> org.springframework.data.domain.Page<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> long count( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> boolean exists( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession, R> R findBy( org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction ) { throw new UnsupportedOperationException(); }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=AtcSessionTrackerTest`
Expected: FAIL — `AtcSessionTracker` doesn't exist yet.

- [ ] **Step 3: Implement the tracker**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/session/AtcSessionTracker.java`:

```java
package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class AtcSessionTracker {

    private final AtcSessionRepository atcSessionRepository;

    private final ConcurrentMap<SessionKey, AtcSession> openSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, Instant> lastSeenAt = new ConcurrentHashMap<>();

    @PostConstruct
    void reconstructOpenSessions() {
        for( AtcSession session : atcSessionRepository.findByEndedAtIsNull() ) {
            SessionKey key = new SessionKey( session.getCid(), session.getCallsign(), session.getLogonTime() );
            openSessions.put( key, session );
            lastSeenAt.put( key, session.getStartedAt() );
        }
    }

    @Transactional
    public void processSnapshots( List<AtcSnapshot> snapshots ) {
        Set<SessionKey> seenThisCycle = new HashSet<>();
        for( AtcSnapshot snapshot : snapshots ) {
            SessionKey key = new SessionKey( snapshot.getCid(), snapshot.getCallsign(), snapshot.getLogonTime() );
            seenThisCycle.add( key );
            lastSeenAt.put( key, snapshot.getRecordedAt() );
            openSessions.computeIfAbsent( key, k -> createSession( k, snapshot ) );
        }
        closeSessionsNotSeen( seenThisCycle );
    }

    private AtcSession createSession( SessionKey key, AtcSnapshot snapshot ) {
        AtcSession session = AtcSession.builder()
            .cid( key.cid() )
            .callsign( key.callsign() )
            .logonTime( key.logonTime() )
            .facility( snapshot.getFacility() )
            .startedAt( snapshot.getRecordedAt() )
            .build();
        return atcSessionRepository.save( session );
    }

    private void closeSessionsNotSeen( Set<SessionKey> seenThisCycle ) {
        for( SessionKey key : Set.copyOf( openSessions.keySet() ) ) {
            if( !seenThisCycle.contains( key ) ) {
                AtcSession session = openSessions.get( key );
                session.setEndedAt( lastSeenAt.get( key ) );
                atcSessionRepository.save( session );
                openSessions.remove( key );
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=AtcSessionTrackerTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Wire the tracker into the poller**

Edit `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`: add the import `import de.secretsoft.vatsim_stats.ingestion.session.AtcSessionTracker;`, add a field `private AtcSessionTracker atcSessionTracker;`, in `setUp()` add `atcSessionTracker = mock( AtcSessionTracker.class );` and change the `poller = new IngestionPoller(...)` call to pass it as the 5th argument, and in `savesAllValidPilotsAndControllersFromOneCycle()` add:

```java
        verify( atcSessionTracker ).processSnapshots( atcCaptor.getValue() );
```

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`: add the import `import de.secretsoft.vatsim_stats.ingestion.session.AtcSessionTracker;`, add the field below `sessionOrchestrator`:

```java
    private final AtcSessionTracker atcSessionTracker;
```

and change the end of `pollOnce()` from:

```java
        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

to:

```java
        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }
        atcSessionTracker.processSnapshots( atcSnapshots );

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

(`atcSessionTracker.processSnapshots` is called unconditionally, even with an empty list, so that controllers disappearing from the feed are still detected and closed.)

- [ ] **Step 6: Run poller tests to verify they still pass**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: PASS

- [ ] **Step 7: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion
git commit -m "feat: add ATC session login/logout tracking"
```

---

## Task 15: Success Events for `vatsim-poll` and `ourairports-import`

Publishes a plain Spring `ApplicationEvent` from `ingestion` and `reference-data` on every successful cycle/import. `monitoring` (Task 16) will listen for these — keeping the dependency one-directional (`monitoring` depends on `ingestion`/`reference-data`, never the reverse, avoiding the cycle Task 4 flagged).

**Files:**
- Create: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/PollCycleSucceededEvent.java`
- Create: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportSucceededEvent.java`
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`
- Modify: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`
- Modify: `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsScheduledImportJob.java`

**Interfaces:**
- Produces: `record PollCycleSucceededEvent(Instant occurredAt)`, `record OurAirportsImportSucceededEvent(Instant occurredAt)`. Task 16's `IngestionHealthListener` (`@EventListener`) consumes both.

- [ ] **Step 1: Create the event records**

`ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/PollCycleSucceededEvent.java`:

```java
package de.secretsoft.vatsim_stats.ingestion;

import java.time.Instant;

public record PollCycleSucceededEvent( Instant occurredAt ) {
}
```

`reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsImportSucceededEvent.java`:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import java.time.Instant;

public record OurAirportsImportSucceededEvent( Instant occurredAt ) {
}
```

- [ ] **Step 2: Update the poller test for the new constructor argument**

Edit `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`: add the import `import org.springframework.context.ApplicationEventPublisher;`, add a field `private ApplicationEventPublisher eventPublisher;`, in `setUp()` add `eventPublisher = mock( ApplicationEventPublisher.class );` and pass it as the 6th constructor argument, and in `savesAllValidPilotsAndControllersFromOneCycle()` add:

```java
        verify( eventPublisher ).publishEvent( org.mockito.ArgumentMatchers.any( PollCycleSucceededEvent.class ) );
```

Also add, in `returnsAnEmptyResultWithoutThrowingWhenTheFeedFails()`, right before the closing brace:

```java
        verify( eventPublisher, org.mockito.Mockito.never() ).publishEvent( org.mockito.ArgumentMatchers.any() );
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: FAIL — 6-arg constructor doesn't exist yet, event never published.

- [ ] **Step 4: Publish the event from IngestionPoller**

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`: add the import `import org.springframework.context.ApplicationEventPublisher;`, add the field below `atcSessionTracker`:

```java
    private final ApplicationEventPublisher eventPublisher;
```

Change the end of `pollOnce()` from:

```java
        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }
        atcSessionTracker.processSnapshots( atcSnapshots );

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

to:

```java
        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }
        atcSessionTracker.processSnapshots( atcSnapshots );

        eventPublisher.publishEvent( new PollCycleSucceededEvent( recordedAt ) );
        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: PASS

- [ ] **Step 6: Publish the event from the OurAirports import job**

Edit `reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports/OurAirportsScheduledImportJob.java`, replace its full content with:

```java
package de.secretsoft.vatsim_stats.referencedata.ourairports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OurAirportsScheduledImportJob {

    private final OurAirportsImportService importService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled( cron = "0 30 3 * * *" )
    public void run() {
        try {
            OurAirportsImportResult result = importService.importFromOurAirports();
            log.info( "OurAirports import finished: {} airports, {} runways",
                result.airportsUpserted(), result.runwaysUpserted() );
            eventPublisher.publishEvent( new OurAirportsImportSucceededEvent( Instant.now() ) );
        } catch( Exception e ) {
            log.error( "OurAirports import failed, keeping previous data", e );
        }
    }
}
```

- [ ] **Step 7: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion reference-data/src/main/java/de/secretsoft/vatsim_stats/referencedata/ourairports ingestion/src/test
git commit -m "feat: publish success events for ingestion poll cycles and OurAirports imports"
```

---

## Task 16: HealthMonitor + Email Alerting

Per spec: alert once per failure episode (never repeat spam), an optional recovery email, thresholds of 5 minutes for `vatsim-poll` and "the daily import didn't run" for `ourairports-import`.

**Files:**
- Modify: `monitoring/pom.xml` (add dependencies on `ingestion` and `reference-data` — this is the one-directional dependency Task 15 set up for)
- Create: `monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/HealthMonitor.java`
- Create: `monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/IngestionHealthListener.java`
- Create: `monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/HealthAlertService.java`
- Modify: `app/src/main/resources/application.yml` (mail + alert recipient config)
- Test: `monitoring/src/test/java/de/secretsoft/vatsim_stats/monitoring/HealthMonitorTest.java`
- Test: `monitoring/src/test/java/de/secretsoft/vatsim_stats/monitoring/HealthAlertServiceTest.java`

**Interfaces:**
- Consumes: `PollCycleSucceededEvent` (Task 15, `ingestion`), `OurAirportsImportSucceededEvent` (Task 15, `reference-data`).
- Produces: `class HealthMonitor` with `void recordSuccess(String source, Instant at)`, `void recordSuccess(String source)`, `boolean isOverdue(String source, Duration threshold, Instant now)`, `boolean isAlerted(String source)`, `void markAlerted(String source)`, `void clearAlert(String source)`; `class IngestionHealthListener` with `@EventListener` methods wiring both events to `recordSuccess`; `class HealthAlertService` with `@Scheduled void checkHealth()`.

- [ ] **Step 1: Add cross-module dependencies to monitoring/pom.xml**

Edit `monitoring/pom.xml`, add inside `<dependencies>` (before the existing `spring-boot-starter-mail` entry):

```xml
<dependency>
    <groupId>de.secretsoft.vatsim-stats</groupId>
    <artifactId>ingestion</artifactId>
</dependency>
<dependency>
    <groupId>de.secretsoft.vatsim-stats</groupId>
    <artifactId>reference-data</artifactId>
</dependency>
```

- [ ] **Step 2: Write the failing HealthMonitor test**

`monitoring/src/test/java/de/secretsoft/vatsim_stats/monitoring/HealthMonitorTest.java`:

```java
package de.secretsoft.vatsim_stats.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HealthMonitorTest {

    private static final Duration THRESHOLD = Duration.ofMinutes( 5 );

    @Test
    void isOverdueWhenNoSuccessWasEverRecorded() {
        HealthMonitor monitor = new HealthMonitor();

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, Instant.now() ) ).isTrue();
    }

    @Test
    void isNotOverdueRightAfterASuccess() {
        HealthMonitor monitor = new HealthMonitor();
        Instant now = Instant.parse( "2026-08-24T10:00:00Z" );
        monitor.recordSuccess( "vatsim-poll", now );

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, now.plus( Duration.ofMinutes( 2 ) ) ) ).isFalse();
    }

    @Test
    void isOverdueOnceThresholdElapsesSinceLastSuccess() {
        HealthMonitor monitor = new HealthMonitor();
        Instant now = Instant.parse( "2026-08-24T10:00:00Z" );
        monitor.recordSuccess( "vatsim-poll", now );

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, now.plus( Duration.ofMinutes( 6 ) ) ) ).isTrue();
    }

    @Test
    void alertLifecycleTracksMarkedAndClearedState() {
        HealthMonitor monitor = new HealthMonitor();

        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isFalse();
        monitor.markAlerted( "vatsim-poll" );
        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isTrue();
        monitor.clearAlert( "vatsim-poll" );
        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl monitoring test -Dtest=HealthMonitorTest`
Expected: FAIL — `HealthMonitor` doesn't exist yet.

- [ ] **Step 4: Implement HealthMonitor**

`monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/HealthMonitor.java`:

```java
package de.secretsoft.vatsim_stats.monitoring;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HealthMonitor {

    private final Map<String, Instant> lastSuccessAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> alerted = new ConcurrentHashMap<>();

    public void recordSuccess( String source ) {
        recordSuccess( source, Instant.now() );
    }

    public void recordSuccess( String source, Instant at ) {
        lastSuccessAt.put( source, at );
    }

    public boolean isOverdue( String source, Duration threshold, Instant now ) {
        Instant last = lastSuccessAt.get( source );
        return last == null || Duration.between( last, now ).compareTo( threshold ) > 0;
    }

    public boolean isAlerted( String source ) {
        return alerted.getOrDefault( source, false );
    }

    public void markAlerted( String source ) {
        alerted.put( source, true );
    }

    public void clearAlert( String source ) {
        alerted.put( source, false );
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl monitoring test -Dtest=HealthMonitorTest`
Expected: PASS

- [ ] **Step 6: Create the event listener**

`monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/IngestionHealthListener.java`:

```java
package de.secretsoft.vatsim_stats.monitoring;

import de.secretsoft.vatsim_stats.ingestion.PollCycleSucceededEvent;
import de.secretsoft.vatsim_stats.referencedata.ourairports.OurAirportsImportSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestionHealthListener {

    public static final String VATSIM_POLL_SOURCE = "vatsim-poll";
    public static final String OURAIRPORTS_IMPORT_SOURCE = "ourairports-import";

    private final HealthMonitor healthMonitor;

    @EventListener
    public void onPollCycleSucceeded( PollCycleSucceededEvent event ) {
        healthMonitor.recordSuccess( VATSIM_POLL_SOURCE, event.occurredAt() );
    }

    @EventListener
    public void onOurAirportsImportSucceeded( OurAirportsImportSucceededEvent event ) {
        healthMonitor.recordSuccess( OURAIRPORTS_IMPORT_SOURCE, event.occurredAt() );
    }
}
```

- [ ] **Step 7: Write the failing HealthAlertService test**

`monitoring/src/test/java/de/secretsoft/vatsim_stats/monitoring/HealthAlertServiceTest.java`:

```java
package de.secretsoft.vatsim_stats.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HealthAlertServiceTest {

    private HealthMonitor healthMonitor;
    private JavaMailSender mailSender;
    private HealthAlertService alertService;

    @BeforeEach
    void setUp() {
        healthMonitor = new HealthMonitor();
        mailSender = mock( JavaMailSender.class );
        alertService = new HealthAlertService( healthMonitor, mailSender, "ops@example.com" );
    }

    @Test
    void doesNotAlertWhileWithinThreshold() {
        healthMonitor.recordSuccess( IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now() );
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );

        alertService.checkHealth();

        verify( mailSender, never() ).send( any( SimpleMailMessage.class ) );
    }

    @Test
    void sendsExactlyOneAlertPerFailureEpisode() {
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );
        healthMonitor.recordSuccess(
            IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now().minus( java.time.Duration.ofMinutes( 10 ) ) );

        alertService.checkHealth();
        alertService.checkHealth();

        verify( mailSender, times( 1 ) ).send( any( SimpleMailMessage.class ) );
    }

    @Test
    void sendsARecoveryEmailAfterAlertingThenRecovering() {
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );
        healthMonitor.recordSuccess(
            IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now().minus( java.time.Duration.ofMinutes( 10 ) ) );
        alertService.checkHealth();

        healthMonitor.recordSuccess( IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now() );
        alertService.checkHealth();

        verify( mailSender, times( 2 ) ).send( any( SimpleMailMessage.class ) );
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `mvn -q -pl monitoring test -Dtest=HealthAlertServiceTest`
Expected: FAIL — `HealthAlertService` doesn't exist yet.

- [ ] **Step 9: Implement HealthAlertService**

`monitoring/src/main/java/de/secretsoft/vatsim_stats/monitoring/HealthAlertService.java`:

```java
package de.secretsoft.vatsim_stats.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class HealthAlertService {

    private static final Duration VATSIM_POLL_THRESHOLD = Duration.ofMinutes( 5 );
    private static final Duration OURAIRPORTS_IMPORT_THRESHOLD = Duration.ofHours( 30 );

    private final HealthMonitor healthMonitor;
    private final JavaMailSender mailSender;
    private final String alertRecipient;

    public HealthAlertService(
        HealthMonitor healthMonitor,
        JavaMailSender mailSender,
        @Value( "${monitoring.alert.to}" ) String alertRecipient ) {
        this.healthMonitor = healthMonitor;
        this.mailSender = mailSender;
        this.alertRecipient = alertRecipient;
    }

    @Scheduled( fixedRate = 60000 )
    public void checkHealth() {
        check( IngestionHealthListener.VATSIM_POLL_SOURCE, VATSIM_POLL_THRESHOLD );
        check( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, OURAIRPORTS_IMPORT_THRESHOLD );
    }

    private void check( String source, Duration threshold ) {
        Instant now = Instant.now();
        boolean overdue = healthMonitor.isOverdue( source, threshold, now );

        if( overdue && !healthMonitor.isAlerted( source ) ) {
            send( "vatsim-stats: " + source + " is failing",
                source + " has not succeeded within the last " + threshold + ". Check the application logs." );
            healthMonitor.markAlerted( source );
        } else if( !overdue && healthMonitor.isAlerted( source ) ) {
            send( "vatsim-stats: " + source + " has recovered", source + " is succeeding again." );
            healthMonitor.clearAlert( source );
        }
    }

    private void send( String subject, String text ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo( alertRecipient );
        message.setSubject( subject );
        message.setText( text );
        mailSender.send( message );
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `mvn -q -pl monitoring test -Dtest=HealthAlertServiceTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 11: Add mail/alert configuration**

Edit `app/src/main/resources/application.yml`, add at the top level:

```yaml
monitoring:
  alert:
    to: ${ALERT_EMAIL_TO:ops@example.com}

spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

Note: this adds a second top-level `spring:` block. YAML permits repeated top-level keys to be merged only if using Spring's relaxed multi-document binding is not in play here — merge this into the **existing** `spring:` block from Task 2 instead of adding a duplicate key (duplicate top-level `spring:` keys are invalid YAML and the second one would silently win). The full merged `app/src/main/resources/application.yml` after this step:

```yaml
spring:
  application:
    name: vatsim-stats
  datasource:
    url: jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/${POSTGRES_DB:vatsim_stats}
    username: ${POSTGRES_USER:vatsim_stats}
    password: ${POSTGRES_PASSWORD:changeme}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

vatsim:
  poll-interval-ms: 15000

monitoring:
  alert:
    to: ${ALERT_EMAIL_TO:ops@example.com}

server:
  port: 8080
```

- [ ] **Step 12: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 13: Commit**

```bash
git add monitoring/pom.xml monitoring/src app/src/main/resources/application.yml
git commit -m "feat: add health monitoring with email alerting on prolonged ingestion/import failures"
```

---

## Task 17: Verification UI (Vaadin, internal)

Per spec: a minimal, deliberately unpolished internal Vaadin view for manually checking recorded data during development — no paging/filtering, no design effort, not a preview of the future Vue UI.

**Files:**
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepository.java`
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEventRepository.java`
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSessionRepository.java`
- Create: `app/src/main/java/de/secretsoft/vatsim_stats/app/ui/MainLayout.java`
- Create: `app/src/main/java/de/secretsoft/vatsim_stats/app/ui/PilotSessionsView.java`
- Create: `app/src/main/java/de/secretsoft/vatsim_stats/app/ui/AtcSessionsView.java`

**Interfaces:**
- Consumes: `PilotSessionRepository`, `PilotAirportEventRepository`, `AtcSessionRepository` (Task 7).
- Produces: two routes, `/` (`PilotSessionsView`) and `/atc-sessions` (`AtcSessionsView`), reachable by running the app locally. No automated test — verified manually per Step 5 below (UI wiring has no meaningful unit-test surface; this matches the spec's own Testing-Strategie, which scopes automated tests to detection/ingestion, not this debug view).

- [ ] **Step 1: Add the finder methods needed by the views**

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotSessionRepository.java`, add inside the interface:

```java
    List<PilotSession> findTop200ByOrderByStartedAtDesc();
```

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/PilotAirportEventRepository.java`, replace its full content with:

```java
package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PilotAirportEventRepository extends JpaRepository<PilotAirportEvent, Long> {

    List<PilotAirportEvent> findByPilotSessionOrderByOccurredAt( PilotSession pilotSession );
}
```

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain/AtcSessionRepository.java`, add inside the interface:

```java
    List<AtcSession> findTop200ByOrderByStartedAtDesc();
```

- [ ] **Step 2: Create the main layout**

`app/src/main/java/de/secretsoft/vatsim_stats/app/ui/MainLayout.java`:

```java
package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLayout;

public class MainLayout extends AppLayout implements RouterLayout {

    public MainLayout() {
        addToNavbar( new HorizontalLayout( new DrawerToggle(), new H1( "vatsim-stats — Verifikation" ) ) );

        SideNav nav = new SideNav();
        nav.addItem( new SideNavItem( "Pilot Sessions", PilotSessionsView.class ) );
        nav.addItem( new SideNavItem( "ATC Sessions", AtcSessionsView.class ) );
        addToDrawer( nav );
    }
}
```

- [ ] **Step 3: Create the pilot sessions view with drill-down**

`app/src/main/java/de/secretsoft/vatsim_stats/app/ui/PilotSessionsView.java`:

```java
package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;

@Route( value = "", layout = MainLayout.class )
public class PilotSessionsView extends VerticalLayout {

    public PilotSessionsView( PilotSessionRepository pilotSessionRepository,
                               PilotAirportEventRepository pilotAirportEventRepository ) {
        Grid<PilotSession> sessionGrid = new Grid<>( PilotSession.class, false );
        sessionGrid.addColumn( PilotSession::getCallsign ).setHeader( "Callsign" );
        sessionGrid.addColumn( PilotSession::getCid ).setHeader( "CID" );
        sessionGrid.addColumn( PilotSession::getSequenceNumber ).setHeader( "Leg" );
        sessionGrid.addColumn( PilotSession::getPlannedDeparture ).setHeader( "Planned Dep" );
        sessionGrid.addColumn( PilotSession::getPlannedDestination ).setHeader( "Planned Dest" );
        sessionGrid.addColumn( PilotSession::getAircraftShort ).setHeader( "Aircraft" );
        sessionGrid.addColumn( PilotSession::getStatus ).setHeader( "Status" );
        sessionGrid.addColumn( PilotSession::getStartedAt ).setHeader( "Started" );
        sessionGrid.addColumn( PilotSession::getEndedAt ).setHeader( "Ended" );
        sessionGrid.setItems( pilotSessionRepository.findTop200ByOrderByStartedAtDesc() );
        sessionGrid.setHeightFull();

        Grid<PilotAirportEvent> eventGrid = new Grid<>( PilotAirportEvent.class, false );
        eventGrid.addColumn( PilotAirportEvent::getAirportIcao ).setHeader( "Airport" );
        eventGrid.addColumn( PilotAirportEvent::getEventType ).setHeader( "Event" );
        eventGrid.addColumn( PilotAirportEvent::getOccurredAt ).setHeader( "Occurred" );
        eventGrid.setHeightFull();

        sessionGrid.asSingleSelect().addValueChangeListener( change -> {
            PilotSession selected = change.getValue();
            eventGrid.setItems( selected == null
                ? java.util.List.of()
                : pilotAirportEventRepository.findByPilotSessionOrderByOccurredAt( selected ) );
        } );

        setSizeFull();
        add( sessionGrid, eventGrid );
        setFlexGrow( 1, sessionGrid );
        setFlexGrow( 1, eventGrid );
    }
}
```

- [ ] **Step 4: Create the ATC sessions view**

`app/src/main/java/de/secretsoft/vatsim_stats/app/ui/AtcSessionsView.java`:

```java
package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;

@Route( value = "atc-sessions", layout = MainLayout.class )
public class AtcSessionsView extends VerticalLayout {

    public AtcSessionsView( AtcSessionRepository atcSessionRepository ) {
        Grid<AtcSession> grid = new Grid<>( AtcSession.class, false );
        grid.addColumn( AtcSession::getCallsign ).setHeader( "Callsign" );
        grid.addColumn( AtcSession::getCid ).setHeader( "CID" );
        grid.addColumn( AtcSession::getFacility ).setHeader( "Facility" );
        grid.addColumn( AtcSession::getStartedAt ).setHeader( "Started" );
        grid.addColumn( AtcSession::getEndedAt ).setHeader( "Ended" );
        grid.setItems( atcSessionRepository.findTop200ByOrderByStartedAtDesc() );
        grid.setSizeFull();

        setSizeFull();
        add( grid );
    }
}
```

- [ ] **Step 5: Manually verify against the local docker-compose database**

Run: `docker compose up -d` (from the project root, requires `.env` copied from `.env.example`), then `mvn -q -pl app -am spring-boot:run`. Open `http://localhost:8080/` in a browser — confirm the pilot sessions grid loads (empty is fine on a fresh database) and selecting a row (once data exists) populates the event grid below it. Open `http://localhost:8080/atc-sessions` and confirm it loads without error. Stop the app (Ctrl+C).

- [ ] **Step 6: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/domain app/src/main/java/de/secretsoft/vatsim_stats/app/ui
git commit -m "feat: add internal Vaadin verification UI for pilot and ATC sessions"
```

---

## Task 18: Inject a Testable Clock into the Poller

The dwell-threshold logic (Task 6) needs 90 simulated seconds to elapse to produce a `LANDING`. `IngestionPoller` currently stamps every cycle with `Instant.now()`, which makes that scenario untestable without literally sleeping 90+ real seconds. This task swaps in an injectable `java.time.Clock`, defaulting to the real system clock in production and swappable for a controllable one in Task 19's end-to-end test.

**Files:**
- Modify: `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`
- Modify: `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`
- Modify: `app/src/main/java/de/secretsoft/vatsim_stats/VatsimStatsApplication.java`

**Interfaces:**
- Produces: `IngestionPoller` gains a 7th constructor dependency `java.time.Clock clock`, using `clock.instant()` instead of `Instant.now()`. `VatsimStatsApplication` exposes a `@Bean Clock clock() { return Clock.systemUTC(); }` that Task 19's test overrides.

- [ ] **Step 1: Update the poller test's constructor calls**

Edit `ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java`: add the import `import java.time.Clock;`, and change every `new IngestionPoller( ... )` call to append `, Clock.systemUTC()` as the final argument.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: FAIL — constructor signature mismatch.

- [ ] **Step 3: Add the Clock dependency to IngestionPoller**

Edit `ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java`: add the import `import java.time.Clock;`, add the field below `eventPublisher`:

```java
    private final Clock clock;
```

and change:

```java
        Instant recordedAt = Instant.now();
```

to:

```java
        Instant recordedAt = clock.instant();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl ingestion test -Dtest=IngestionPollerTest`
Expected: PASS

- [ ] **Step 5: Expose the production Clock bean**

Edit `app/src/main/java/de/secretsoft/vatsim_stats/VatsimStatsApplication.java`, replace its full content with:

```java
package de.secretsoft.vatsim_stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "de.secretsoft.vatsim_stats")
public class VatsimStatsApplication {

    public static void main( String[] args ) {
        SpringApplication.run( VatsimStatsApplication.class, args );
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 6: Build the whole project**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ingestion/src/main/java/de/secretsoft/vatsim_stats/ingestion/IngestionPoller.java ingestion/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionPollerTest.java app/src/main/java/de/secretsoft/vatsim_stats/VatsimStatsApplication.java
git commit -m "refactor: inject a testable Clock into the ingestion poller"
```

---

## Task 19: End-to-End Integration Test (Feed → Poller → Orchestrator → Database)

Exercises the full Spring wiring together against a real TimescaleDB (Testcontainers): a mocked VATSIM feed simulates a complete flight (airborne → sustained ground contact past the dwell threshold), and the test asserts the resulting `PilotSession`/`PilotAirportEvent` rows. Restart-reconstruction *logic* is already fully unit-tested in Task 13; this test's job is proving the real Spring beans are wired correctly end to end, not re-testing that logic.

**Files:**
- Test: `app/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionEndToEndIT.java`

**Interfaces:**
- Consumes: the full `app` Spring context (Task 1–18), `VatsimDataFeedClient` (mocked via `@MockBean`), a test-local `Clock` override.

- [ ] **Step 1: Write the end-to-end test**

`app/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionEndToEndIT.java`:

```java
package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest( classes = { VatsimStatsApplication.class, IngestionEndToEndIT.TestClockConfig.class } )
class IngestionEndToEndIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg16" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
    }

    @TestConfiguration
    static class TestClockConfig {
        static final MutableClock CLOCK = new MutableClock( Instant.parse( "2026-08-24T10:00:00Z" ) );

        @Bean
        @Primary
        Clock clock() {
            return CLOCK;
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock( Instant initial ) {
            this.instant = initial;
        }

        void advance( Duration duration ) {
            instant = instant.plus( duration );
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone( ZoneId zone ) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @MockBean
    private VatsimDataFeedClient feedClient;

    @Autowired
    private IngestionPoller poller;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private PilotSessionRepository pilotSessionRepository;

    @Autowired
    private PilotAirportEventRepository pilotAirportEventRepository;

    @BeforeEach
    void setUp() {
        TestClockConfig.CLOCK.instant = Instant.parse( "2026-08-24T10:00:00Z" );
        airportRepository.save( Airport.builder()
            .icao( "EDDF" ).name( "Frankfurt" ).latitude( 50.0264 ).longitude( 8.5431 ).elevationFt( 364 ).build() );
    }

    private VatsimPilot pilot( double altitudeFt, double groundspeedKt ) {
        return new VatsimPilot(
            123456L, "DLH400", 50.0264, 8.5431, (int) altitudeFt, (int) groundspeedKt, 270, "2000", 1013,
            Instant.parse( "2026-08-24T09:45:00Z" ),
            new VatsimFlightPlan( "EDDF", "EDDM", "A320" ) );
    }

    @Test
    void aFullFlightIsRecordedAsACompletedSessionWithTakeoffAndLandingEvents() {
        when( feedClient.fetchCurrent() )
            .thenReturn( new VatsimDataFeed( List.of( pilot( 3000, 250 ) ), List.of() ) )
            .thenReturn( new VatsimDataFeed( List.of( pilot( 3000, 250 ) ), List.of() ) );
        poller.pollOnce();
        TestClockConfig.CLOCK.advance( Duration.ofSeconds( 15 ) );
        poller.pollOnce();

        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( pilot( 550, 15 ) ), List.of() ) );
        for( int i = 0; i < 7; i++ ) {
            TestClockConfig.CLOCK.advance( Duration.ofSeconds( 15 ) );
            poller.pollOnce();
        }

        PilotSession session = pilotSessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
                123456L, "DLH400", Instant.parse( "2026-08-24T09:45:00Z" ) )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.COMPLETED );

        List<PilotAirportEvent> events = pilotAirportEventRepository.findByPilotSessionOrderByOccurredAt( session );
        assertThat( events ).hasSize( 1 );
        assertThat( events.get( 0 ).getEventType() ).isEqualTo( AirportEventType.LANDING );
        assertThat( events.get( 0 ).getAirportIcao() ).isEqualTo( "EDDF" );
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -q -pl app -am test -Dtest=IngestionEndToEndIT`
Expected: PASS — confirms the full Spring wiring (poller → orchestrator → phase detection → DB-backed airport lookup → repositories) produces a `COMPLETED` session with one `LANDING` event.

- [ ] **Step 3: Run the entire test suite one final time**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS, every test across all 5 modules green.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/de/secretsoft/vatsim_stats/ingestion/IngestionEndToEndIT.java
git commit -m "test: add end-to-end ingestion integration test against TimescaleDB"
```

---

## Task 20: Update CLAUDE.md to Reflect the Implemented Project

`CLAUDE.md` currently documents only the pre-implementation architecture decisions (tech stack choices, rejected alternatives). Now that the Maven multi-module project, schema, and ingestion pipeline actually exist, run the `claude-md-improver` skill so the file reflects the real project structure — module layout, build/run commands, key files — the way it does for the reference project `vatsim-tools`. Doing this now (rather than before Task 1) means the improver has actual code to check the documentation against instead of an empty repository.

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- None — this task produces no code, only documentation.

- [ ] **Step 1: Run the CLAUDE.md improver**

Invoke the `claude-md-improver` skill against the repository root. Let it audit `CLAUDE.md` against the current state of the code (module structure from Task 1, schema from Tasks 2/7, build/run commands, key services) and apply its suggested updates. Keep the existing "Kernfunktionen"/"Entscheidung: Tech-Stack" sections (still accurate design history) and have it add or update sections covering: project structure (the 5 Maven modules), build/run commands (`mvn clean install`, `mvn -pl app -am spring-boot:run`, `docker compose up -d`), the database migration approach (Flyway, TimescaleDB hypertables), and key files a future contributor would need (state machine, orchestrator, poller).

- [ ] **Step 2: Review the diff**

Read the updated `CLAUDE.md` and confirm it accurately reflects the project as built through Task 19 — no stale references to unimplemented features, no contradictions with `docs/superpowers/specs/2026-08-24-data-ingestion-design.md`.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md to reflect the implemented ingestion pipeline"
```

<p align="center">
  <img src="img/quietly_logo_upscaled.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

##### [BACK](../README.md)

## Cos'e Quietly

Quietly e un plugin Maven per progetti Quarkus/Hibernate che genera test JUnit/RestAssured per i filtri REST basandosi
sui metadati Hibernate presenti sulle entity JPA.

In pratica:

- scansiona le classi `@Entity`
- legge `@Filter` e `@FilterDef`
- ricostruisce nome filtro, campo, parametro e tipo
- trova il REST service atteso
- diagnostica prerequisiti mancanti
- genera o aggiorna una classe `*FiltersTest`
- genera test smoke CRUD convenzionali in classi `*CrudTest`
- aggiunge solo i test mancanti
- produce report Markdown e JSON

Quietly e pensato per progetti Quarkus con Hibernate ORM/Panache, endpoint REST e test integration.

## Installazione Locale

Nel repository Quietly:

```bash
mvn clean install
```

Questo installa nel repository Maven locale:

```text
ua.quietly:quietly-core:1.0
ua.quietly:quietly-test-support:1.0
ua.quietly:quietly-maven-plugin:1.0
```

## Setup Nel Progetto Target

Aggiungi il supporto runtime ai test:

```xml
<dependency>
    <groupId>ua.quietly</groupId>
    <artifactId>quietly-test-support</artifactId>
    <version>1.0</version>
    <scope>test</scope>
</dependency>
```

Aggiungi il plugin:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>ua.quietly</groupId>
            <artifactId>quietly-maven-plugin</artifactId>
            <version>1.0</version>
            <configuration>
                <basePackage>it.ness.gestioneinterinali</basePackage>
                <entityPackagePattern>${basePackage}.entities</entityPackagePattern>
                <servicePackagePattern>${basePackage}.services.rs</servicePackagePattern>
                <serviceNamePattern>${entitySimpleName}ServiceRs</serviceNamePattern>
                <testOutputDirectory>${project.build.directory}/generated-test-sources/quietly</testOutputDirectory>
                <reportFile>${project.build.directory}/quietly/filters-report.md</reportFile>
                <disabledByDefault>false</disabledByDefault>
                <failOnMissingService>false</failOnMissingService>
                <failOnUnresolvedField>false</failOnUnresolvedField>
                <fieldResolutionMode>FUZZY</fieldResolutionMode>
                <dryRun>false</dryRun>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Comandi

Scansiona filtri e scrive report senza generare test:

```bash
mvn compile quietly:scan
```

Esegue una diagnosi piu completa del progetto:

```bash
mvn compile quietly:doctor
```

Per far fallire la build quando `doctor` trova problemi:

```xml
<failOnProblems>true</failOnProblems>
```

Genera o aggiorna i test:

```bash
mvn compile quietly:filter-tests
```

Genera o aggiorna smoke test CRUD REST:

```bash
mvn compile quietly:crud-tests
```

Esegui un giro senza scrivere test, utile su progetti grandi:

```xml
<dryRun>true</dryRun>
```

poi:

```bash
mvn compile quietly:filter-tests
```

In `dryRun=true`, Quietly scansiona e scrive il report, ma non crea o modifica file di test.

### Differenza Tra I Goal

| Goal                   | Scrive test             | Scopo                                                       |
|------------------------|-------------------------|-------------------------------------------------------------|
| `quietly:scan`         | No                      | Inventario filtri, report Markdown/JSON                     |
| `quietly:doctor`       | No                      | Diagnostica di service, campi, fixture SQL e test esistenti |
| `quietly:filter-tests` | Si, salvo `dryRun=true` | Generazione incrementale dei test                           |
| `quietly:crud-tests`   | Si, salvo `dryRun=true` | Smoke test REST CRUD convenzionali                          |

## Cosa Viene Generato

Per una entity:

```java
@Entity
@FilterDef(name = "obj.status", parameters = @ParamDef(name = "status", type = String.class))
@Filter(name = "obj.status", condition = "status = :status")
public class Customer { ... }
```

Quietly genera una classe simile:

```java
@QuarkusTest
@TestHTTPEndpoint(CustomerServiceRs.class)
public class CustomerFiltersTest extends FilterTestBase {

    @Inject
    private EntityManager em;

    @BeforeEach
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void beforeEach() throws IOException {
        Customer.deleteAll();
        Customer.getEntityManager()
                .createNativeQuery(SqlUtils.loadSqlFromClasspath("sql/" + Customer.TABLE_NAME + ".sql"))
                .executeUpdate();
    }

    /**
     * @quietly-generated filter="obj.status"
     */
    @Test
    @TestSecurity(user = "test")
    public void obj_status_filter_test() {
        String paramValue = test_entity(em, Customer.class, "status IS NOT NULL").status;
        Assertions.assertNotNull(paramValue);
        assert_filter_works("obj.status", paramValue, Customer.class);
    }
}
```

I metodi generati hanno un marker Javadoc `@quietly-generated`. Se il metodo esiste gia ma non ha il marker, Quietly lo
aggiunge alla run successiva.

## CRUD Smoke Tests

`quietly:crud-tests` genera una classe separata `*CrudTest` per entity con REST service risolto.

Nella prima versione genera test baseline per endpoint REST convenzionali:

- `GET` sulla collection: si aspetta `200`
- `GET /{id}` con id inesistente: si aspetta `404`

Esempio:

```java
@QuarkusTest
@TestHTTPEndpoint(CustomerServiceRs.class)
public class CustomerCrudTest {

    /**
     * @quietly-generated crud="list"
     */
    @Test
    @TestSecurity(user = "test")
    public void list_endpoint_returns_success_test() {
        Response response = RestAssured.given()
                .when()
                .get()
                .then()
                .extract()
                .response();
        Assertions.assertEquals(200, response.statusCode());
    }
}
```

Per ora Quietly non genera `POST`, `PUT` o `DELETE` CRUD: servono payload validi, DTO e regole progetto-specifiche. La
scelta e intenzionale per evitare test fragili o inventati.

## Nomi Filtro Supportati

Quietly supporta sia nomi semplici:

```text
obj.status
like.name
from.createdAt
to.createdAt
nil.deletedAt
not_nil.customerId
```

sia nomi namespaced:

```text
customer.obj.fornitore_uuid
```

In un nome namespaced, Quietly considera l'ultimo segmento come campo:

```text
prefix = customer.obj
field  = fornitore_uuid
```

Il nome del metodo viene sanitizzato per essere Java valido:

```java
customer_obj_fornitore_uuid_filter_test()
```

## Configurazione

| Parametro               | Default                            | Significato                                                |
|-------------------------|------------------------------------|------------------------------------------------------------|
| `basePackage`           | derivato dal package entity        | Package base usato nei pattern                             |
| `entityPackagePattern`  | convenzione legacy `.model`        | Pattern package entity                                     |
| `servicePackagePattern` | `.services.rs`                     | Pattern package REST service                               |
| `serviceNamePattern`    | `${entitySimpleName}ServiceRs`     | Pattern nome REST service                                  |
| `testOutputDirectory`   | `src/test/java`                    | Directory in cui generare i test                           |
| `reportFile`            | `target/quietly/filters-report.md` | Report Markdown                                            |
| `disabledByDefault`     | `false`                            | Se `true`, aggiunge `@Disabled` ai test generati           |
| `failOnMissingService`  | `true`                             | Se `false`, salta entity senza service                     |
| `failOnUnresolvedField` | `true`                             | Se `false`, salta filtri con campo non risolto             |
| `fieldResolutionMode`   | `STRICT`                           | `STRICT` o `FUZZY`                                         |
| `dryRun`                | `false`                            | Se `true`, non scrive test                                 |
| `failOnProblems`        | `false`                            | Solo `quietly:doctor`: fallisce la build se trova problemi |

Placeholder supportati:

```text
${basePackage}
${entitySimpleName}
```

## STRICT E FUZZY

`STRICT` e il default. Risolve solo campi esatti e deterministici.

`FUZZY` prova a trovare il campo piu simile, ma non sceglie se ci sono match ambigui. In quel caso:

- con `failOnUnresolvedField=true`, la build fallisce
- con `failOnUnresolvedField=false`, il filtro viene saltato e finisce nel report

## Report

Quietly scrive sempre un report Markdown:

```text
target/quietly/filters-report.md
```

Esempio:

```markdown
| Entity | Capability | Subject | Status | Details |
| --- | --- | --- | --- | --- |
| Customer | FILTER_TEST | obj.status | GENERATED | Generated test method. |
| Worker | FILTER_TEST | obj.data_riferimento | SKIPPED_UNRESOLVED_FIELD | ... |
| UtenteFattura | DIAGNOSTIC | missing-service | SKIPPED_MISSING_SERVICE | ... |
```

Stati principali:

| Stato                           | Significato                                    |
|---------------------------------|------------------------------------------------|
| `GENERATED`                     | Test generato                                  |
| `DISCOVERED`                    | Filtro scoperto da `quietly:scan`              |
| `OK`                            | Diagnostica positiva da `quietly:doctor`       |
| `EXISTING`                      | Metodo gia presente                            |
| `UPDATED_MARKER`                | Metodo esistente aggiornato con marker Quietly |
| `STALE_GENERATED_TEST`          | Metodo generato per un filtro non piu scoperto |
| `SKIPPED_MISSING_SERVICE`       | REST service non trovato                       |
| `SKIPPED_UNRESOLVED_FIELD`      | Campo filtro non risolto                       |
| `MISSING_SQL_FIXTURE`           | Fixture SQL attesa non trovata                 |
| `MISSING_TABLE_NAME`            | Entity senza costante pubblica `TABLE_NAME`    |
| `SKIPPED_INVALID_EXISTING_FILE` | File esistente non contiene la classe attesa   |

Quietly scrive anche il report JSON accanto al Markdown:

```text
target/quietly/filters-report.json
```

Per `quietly:crud-tests`, se `reportFile` non e configurato, il report predefinito e:

```text
target/quietly/crud-report.md
target/quietly/crud-report.json
```

Il JSON contiene una summary specifica per il goal, usabile da CI o script.

Esempio `quietly:doctor`:

```json
{
  "summary": {
    "analyzedFilters": 10,
    "readyFilters": 9,
    "readinessPercent": 90.0,
    "existingGeneratedTests": 8,
    "generationCoveragePercent": 80.0,
    "diagnostics": 3,
    "problems": 1
  }
}
```

I conteggi sono basati sull'identita logica `(entity, capability, subject)`: piu eventi sullo stesso filtro o sulla
stessa operazione non gonfiano il totale.

`scan` mostra esclusivamente l'inventario dei filtri. `doctor` calcola sia la readiness rispetto a service e campi sia
la generation coverage basata sui test Quietly esistenti. `filter-tests` genera o aggiorna i test e riporta la copertura
ottenuta.

Una percentuale con denominatore zero vale `0.00%`, non `100%`.

## Requisiti Del Progetto Target

Quietly oggi assume:

- Quarkus test runtime
- Hibernate ORM / Panache
- entity annotate con `@Entity`
- filtri Hibernate con `@Filter` e `@FilterDef`
- service REST compatibile con `@TestHTTPEndpoint`
- entity con `deleteAll()` e `getEntityManager()`
- costante `TABLE_NAME`
- fixture SQL in `src/test/resources/sql/<TABLE_NAME>.sql`
- endpoint REST che accetta query param con lo stesso nome del filtro

## Idempotenza

Quietly e progettato per essere rieseguito:

- non duplica metodi gia generati
- non elimina codice custom
- aggiunge metodi mancanti
- aggiunge `beforeEach()` se manca
- aggiunge il marker `@quietly-generated` ai metodi esistenti riconosciuti
- segnala come `STALE_GENERATED_TEST` i metodi generati per filtri non piu presenti
- mantiene separati i test filtro `*FiltersTest` e i test CRUD `*CrudTest`

## Troubleshooting

### Non genera nulla

Controlla il report:

```text
target/quietly/filters-report.md
```

Spesso il motivo e `SKIPPED_MISSING_SERVICE` o `SKIPPED_UNRESOLVED_FIELD`.

### REST service non trovato

Messaggio tipico:

```text
Entity Customer has Hibernate filters but no matching REST service was found.
Expected com.acme.services.rs.CustomerServiceRs.
```

Correggi `servicePackagePattern` / `serviceNamePattern`, oppure usa:

```xml
<failOnMissingService>false</failOnMissingService>
```

### Campo filtro non risolto

Con `STRICT`, il nome campo deve esistere esattamente nella entity.

Per onboarding su legacy puoi usare:

```xml
<fieldResolutionMode>FUZZY</fieldResolutionMode>
<failOnUnresolvedField>false</failOnUnresolvedField>
```

### Voglio solo vedere cosa farebbe

Usa:

```xml
<dryRun>true</dryRun>
```

## Architettura

| Modulo                 | Responsabilita                                      |
|------------------------|-----------------------------------------------------|
| `quietly-core`         | scanning entity e metadati Hibernate                |
| `quietly-maven-plugin` | Mojo Maven, configurazione, generazione AST, report |
| `quietly-test-support` | base class e helper runtime per i test generati     |

## Stato Attuale

Quietly e focalizzato su Quarkus/Hibernate/Panache. Include generazione test filtri, smoke test CRUD convenzionali,
scan, doctor, report Markdown/JSON e rilevazione dei test generati stale. Non include ancora supporto Spring o report
HTML.

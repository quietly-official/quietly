<p align="center">
  <img src="img/quietly_hd_dark_theme.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

##### [BACK](../README.md)

## Cos'e Quietly

Quietly e un plugin Maven per progetti Quarkus/Hibernate che genera test JUnit/RestAssured per i filtri REST basandosi sui metadati Hibernate presenti sulle entity JPA.

In pratica:

- scansiona le classi `@Entity`
- legge `@Filter` e `@FilterDef`
- ricostruisce nome filtro, campo, parametro e tipo
- trova il REST service atteso
- genera o aggiorna una classe `*FiltersTest`
- aggiunge solo i test mancanti
- produce un report Markdown della generazione

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

Genera o aggiorna i test:

```bash
mvn compile quietly:filter-tests
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

I metodi generati hanno un marker Javadoc `@quietly-generated`. Se il metodo esiste gia ma non ha il marker, Quietly lo aggiunge alla run successiva.

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

| Parametro | Default | Significato |
| --- | --- | --- |
| `basePackage` | derivato dal package entity | Package base usato nei pattern |
| `entityPackagePattern` | convenzione legacy `.model` | Pattern package entity |
| `servicePackagePattern` | `.services.rs` | Pattern package REST service |
| `serviceNamePattern` | `${entitySimpleName}ServiceRs` | Pattern nome REST service |
| `testOutputDirectory` | `src/test/java` | Directory in cui generare i test |
| `reportFile` | `target/quietly/filters-report.md` | Report Markdown |
| `disabledByDefault` | `false` | Se `true`, aggiunge `@Disabled` ai test generati |
| `failOnMissingService` | `true` | Se `false`, salta entity senza service |
| `failOnUnresolvedField` | `true` | Se `false`, salta filtri con campo non risolto |
| `fieldResolutionMode` | `STRICT` | `STRICT` o `FUZZY` |
| `dryRun` | `false` | Se `true`, non scrive test |

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
| Entity | Filter | Status | Details |
| --- | --- | --- | --- |
| Customer | obj.status | GENERATED | Generated test method. |
| Worker | obj.data_riferimento | SKIPPED_UNRESOLVED_FIELD | ... |
| UtenteFattura | * | SKIPPED_MISSING_SERVICE | ... |
```

Stati principali:

| Stato | Significato |
| --- | --- |
| `GENERATED` | Test generato |
| `EXISTING` | Metodo gia presente |
| `UPDATED_MARKER` | Metodo esistente aggiornato con marker Quietly |
| `SKIPPED_MISSING_SERVICE` | REST service non trovato |
| `SKIPPED_UNRESOLVED_FIELD` | Campo filtro non risolto |
| `SKIPPED_INVALID_EXISTING_FILE` | File esistente non contiene la classe attesa |

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

| Modulo | Responsabilita |
| --- | --- |
| `quietly-core` | scanning entity e metadati Hibernate |
| `quietly-maven-plugin` | Mojo Maven, configurazione, generazione AST, report |
| `quietly-test-support` | base class e helper runtime per i test generati |

## Stato Attuale

Quietly e focalizzato su Quarkus/Hibernate/Panache. Non include ancora supporto Spring, report HTML o goal separati tipo `doctor`.

# SRC Help Memory

Documento interno para entender rapidamente el contenido de `src` del proyecto `jpa-rsql-search`.

Base de trabajo:
- Branch de diagnostico: `codex/src-documentation`
- Branch de ejecucion v2: `codex/v2-modular-architecture`
- Fuente sincronizada: `origin/master` en `e7c236a`
- Modulo Maven: `io.github.ggomarighetti:jpa-rsql-search:1.0.1`
- Java: 17
- Stack principal: Spring Boot 4.1, Spring Data JPA, Hibernate Validator, nstdio RSQL parser, Perplexhub RSQL JPA.

La ejecucion v2 usa este inventario como baseline, pero reemplaza el modulo
monolitico por el reactor descrito en `FINDINGS_RESTRUCTURING_PLAN.md`.

## Formato De Ficha

Cada archivo se documenta con esta estructura para que la ayuda memoria sea uniforme y facil de actualizar:

```text
### path/del/archivo
- Tipo:
- Package:
- Feature / area:
- Rol:
- Responsabilidades:
- Colabora con:
- Superficie publica:
- Notas para futuras tareas:
```

Campos:
- `Tipo`: clase, record, enum, interface, test, fixture, recurso, metadata, corpus, etc.
- `Package`: package Java o ruta logica para recursos.
- `Feature / area`: bloque funcional al que pertenece, por ejemplo definicion de busqueda, RSQL, autoconfiguracion, validacion, compilacion, tests de integracion.
- `Rol`: razon de existencia del archivo en una frase.
- `Responsabilidades`: comportamiento concreto que concentra.
- `Colabora con`: dependencias internas relevantes y contratos que consume o expone.
- `Superficie publica`: si forma parte del API publico, infraestructura interna, test support o recurso.
- `Notas para futuras tareas`: riesgos, invariantes, puntos de extension o cosas que conviene recordar al modificarlo.

## Mapa Inicial De Areas

- `src/main/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure`: auto-configuracion Spring Boot y propiedades globales.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/compile`: orquestacion de validacion y compilacion de entrada publica a `Specification`/`Pageable`.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/definition`: modelo declarativo de una busqueda por entidad/caso de uso.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/exception`: excepciones y DTOs de error para validaciones y protecciones.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/filter`: operadores y reglas de filtrado por campo.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/jpa`: validacion runtime de definiciones contra el metamodelo JPA.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/page`: reglas de paginacion.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/policy`: limites globales de proteccion.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/query`: query textual opcional.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql`: AST, motor y adaptadores RSQL.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/sort`: reglas de ordenamiento.
- `src/main/java/io/github/ggomarighetti/jparsqlsearch/validation`: validacion con Bean Validation/Hibernate Validator.
- `src/main/resources`: metadata de auto-configuracion Spring Boot.
- `src/test/java`: tests unitarios, property-based, integracion Postgres y fixtures de dominio.
- `src/test/resources`: configuracion JUnit y corpus de regresion RSQL.

## Inventario Secuencial

### src/main/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchAutoConfiguration.java
- Tipo: auto-configuracion Spring Boot package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: autoconfiguracion principal de la libreria.
- Rol: publica los beans de alto nivel que conectan definiciones, validacion JPA y compilacion de busquedas.
- Responsabilidades: habilita `JpaRsqlSearchProperties`; crea `SearchDefinitionFactory` con la policy configurada; crea `JpaSearchDefinitionValidator` si hay `EntityManagerFactory`; crea `SearchCompiler` si existe `SearchRsqlEngine`; respeta `@ConditionalOnMissingBean`.
- Colabora con: `JpaRsqlSearchProperties`, `SearchDefinitionFactory`, `JpaSearchDefinitionValidator`, `SearchRsqlEngine`, `SearchCompiler`, `SearchDefinitionValidator`.
- Superficie publica: infraestructura Spring Boot auto-configurable; la clase no es API publica Java.
- Notas para futuras tareas: mantener el orden despues de Hibernate JPA y despues de `JpaRsqlSearchRsqlAutoConfiguration`; cualquier nuevo validador debe poder llegar por `ObjectProvider<SearchDefinitionValidator>`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchProperties.java
- Tipo: `@ConfigurationProperties` package-private con clases anidadas.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: configuracion externa y traduccion a `SearchPolicy`.
- Rol: modela todas las propiedades `jpa.rsql.search.*` y las convierte a la policy interna usada por compiler/factory/guards.
- Responsabilidades: agrupa settings de `rsql`, `filter`, `filter.like`, `paging`, `paging.page`, `paging.slice`, `sorting`, `query` y `paths`; inicializa defaults desde `SearchPolicy.defaults()`; expone getters/setters compatibles con binding Spring; arma `toPolicy()` como fuente unica de conversion.
- Colabora con: `SearchPolicy` y las auto-configuraciones que necesitan una policy efectiva.
- Superficie publica: contrato de configuracion Spring Boot y metadata para consumidores; clase Java package-private.
- Notas para futuras tareas: cuando se agregue un limite en `SearchPolicy`, actualizar tambien defaults, binding, `toPolicy()` y tests de metadata; es facil olvidar la simetria entre propiedades, policy y documentacion generada.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchRsqlAutoConfiguration.java
- Tipo: auto-configuracion Spring Boot package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: wiring del motor RSQL y backend Perplexhub.
- Rol: construye la infraestructura RSQL por defecto cuando `jpa.rsql.search.rsql.enabled=true` o no se configura.
- Responsabilidades: registra `RSQLJPASupport` con entity managers existentes o compartidos desde factories; crea `PerplexhubRsqlBackendOptions`; publica `RsqlBackendAdapter` basado en Perplexhub; construye `SearchRsqlEngine` con `ConversionService` unico o `ApplicationConversionService` enriquecido con beans; aplica `SearchRsqlEngineCustomizer` ordenados.
- Colabora con: Perplexhub `RSQLJPASupport`, `PerplexhubRsqlBackendAdapter`, `SearchRsqlEngineBuilder`, Spring `ConversionService`, `ListableBeanFactory`.
- Superficie publica: infraestructura auto-configurable y puntos de extension por beans (`RsqlBackendAdapter`, `SearchRsqlEngineCustomizer`, `ConversionService`).
- Notas para futuras tareas: conservar los `@ConditionalOnMissingBean` para permitir override de aplicaciones; si hay multiples `ConversionService`, cae al conversion service propio para evitar ambiguedad.

### src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
- Tipo: recurso de metadata Spring Boot.
- Package: ruta logica `META-INF/spring`.
- Feature / area: descubrimiento de auto-configuraciones.
- Rol: registra las auto-configuraciones que Spring Boot debe importar desde el jar.
- Responsabilidades: lista `JpaRsqlSearchRsqlAutoConfiguration` y `JpaRsqlSearchAutoConfiguration` en ese orden.
- Colabora con: mecanismo de auto-configuracion de Spring Boot 3/4.
- Superficie publica: metadata de runtime del starter/libreria.
- Notas para futuras tareas: si se agrega o renombra una auto-configuracion, actualizar este archivo o el jar no la publicara automaticamente.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/CompiledSearch.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: resultado de compilacion.
- Rol: transporta los artefactos validados que la aplicacion pasa al repositorio Spring Data JPA.
- Responsabilidades: contiene `Specification<T>` y `Pageable`; rechaza ambos valores nulos en el constructor compacto.
- Colabora con: `SearchCompiler`, Spring Data `Specification`, Spring Data `Pageable`.
- Superficie publica: API publica principal.
- Notas para futuras tareas: mantenerlo pequeno e inmutable; cualquier dato extra agregado aqui se vuelve parte del contrato publico.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlRulesValidator.java
- Tipo: clase interna package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: validacion semantica del AST RSQL.
- Rol: recorre el AST parseado y verifica que cada comparacion respete operadores, campos y limites de policy.
- Responsabilidades: controla presupuesto de nodos/profundidad; valida operadores registrados y aridad; exige selector declarado y filtering habilitado; registra comparaciones y ORs en `SearchProtectionContext`; valida argumentos via `FilterOperator`; traduce errores de filtro a `RsqlValidationError`.
- Colabora con: `SearchDefinition`, `SearchField`, `SearchProtectionContext`, `RsqlOperatorRegistry`, `FilterValidationError`, AST de `cz.jirutka.rsql.parser`.
- Superficie publica: infraestructura interna de `RsqlSearchGuard`.
- Notas para futuras tareas: el registro de proteccion ocurre antes de algunas validaciones semanticas para que inputs sobredimensionados no queden escondidos detras de errores de selector; conservar ese orden.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlSearchGuard.java
- Tipo: clase interna package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: guard de filtros RSQL.
- Rol: valida y compila un filtro RSQL completo a `Specification<T>` con protecciones y validacion de definicion.
- Responsabilidades: ejecuta preflight de longitud/profundidad de parentesis; cachea definiciones ya validadas con `WeakHashMap`; corre validators runtime y `SearchRsqlEngine.validate`; parsea AST; delega reglas a `RsqlRulesValidator`; detecta si la consulta necesita `distinct`; envuelve fallos diferidos de `Specification.toPredicate`.
- Colabora con: `SearchRsqlEngine`, `SearchDefinitionValidator`, `RsqlRulesValidator`, `SearchProtectionContext`, `RsqlCompilationRequest`, excepciones RSQL.
- Superficie publica: interna, aunque tiene constructores utiles para tests de paquete.
- Notas para futuras tareas: el cache por instancia de `SearchDefinition` evita revalidar definiciones long-lived; si la definicion es mutable en el futuro, este cache seria peligroso.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchCompilationMode.java
- Tipo: enum package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: modo de compilacion.
- Rol: distingue compilacion `PAGE` con count de compilacion `SLICE` sin count.
- Responsabilidades: provee los dos modos usados por `SearchCompiler` y `SearchProtectionContext`.
- Colabora con: `SearchCompiler`, `SearchProtectionContext`, `SearchPageableGuard`.
- Superficie publica: interna.
- Notas para futuras tareas: agregar un modo nuevo implica revisar validaciones de paging/count y combinaciones de request.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchCompiler.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: fachada principal de compilacion.
- Rol: punto de entrada recomendado para transformar `filter`, `query`, `Pageable` y una `SearchDefinition<T>` en `CompiledSearch<T>`.
- Responsabilidades: crea guards con engine/policy; valida definicion; arma `SearchProtectionContext`; combina specifications obligatorias con filtro RSQL y query textual; valida pageable/sort; aplica sorting por Criteria cuando hay subtype/treat; cierra validaciones cross-request con `completeRequest()`.
- Colabora con: `RsqlSearchGuard`, `SearchQueryGuard`, `SearchPageableGuard`, `SearchSpecificationSorting`, `SearchDefinition`, `SearchPolicy`, `SearchRsqlEngine`.
- Superficie publica: API publica principal de runtime.
- Notas para futuras tareas: mantener `compile`/`compileSlice` como entradas publicas; las specs obligatorias se combinan con `AND` antes de filtro/query, y nulos deben seguir rechazandose para evitar predicates ambiguos.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchPageableGuard.java
- Tipo: clase interna package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: validacion de pageable y sort.
- Rol: valida page/size/unpaged/sort contra policy global/local y traduce aliases publicos a paths JPA.
- Responsabilidades: limita page, size y offset; convierte unpaged aceptado en `PageRequest` acotado; exige paging habilitado en la definicion; ejecuta reglas Bean Validation de page/size; valida cantidad y duplicados de sort; exige campos sortable y direcciones permitidas; registra topologia de sort en `SearchProtectionContext`.
- Colabora con: `SearchDefinition`, `SearchField`, `SearchSorting`, `SearchPolicy`, `SearchProtectionContext`, `SearchPageableValidationException`.
- Superficie publica: interna.
- Notas para futuras tareas: el sort devuelto cambia `property` de alias publico a path real; cualquier feature que lea `Pageable` despues de compilar debe asumir paths JPA.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchProtectionContext.java
- Tipo: clase mutable package-private por request.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: estado y limites cross-cutting de seguridad.
- Rol: acumula metricas del request mientras se validan filtro, query y pageable, y dispara `SearchProtectionException` cuando una combinacion excede policy.
- Responsabilidades: cuenta comparaciones, argumentos, OR branches, negaciones, rangos, joins, to-many paths, relation sorts, distinct, query y unpaged; valida patrones LIKE y wildcards; aplica reglas de count para `PAGE`; aplica reglas de `SLICE`; controla combinaciones query + to-many/query + relation sort/query + unpaged.
- Colabora con: `RsqlRulesValidator`, `SearchPageableGuard`, `SearchQueryGuard`, `RsqlSearchGuard`, `SearchPolicy`, `SearchPath.Topology`.
- Superficie publica: interna.
- Notas para futuras tareas: este archivo es el punto central de hardening; al agregar limites nuevos, probar interacciones entre filtro, pageable y query, no solo la regla aislada.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchQueryGuard.java
- Tipo: clase interna package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: validacion de query textual.
- Rol: valida el parametro query opcional y lo convierte a `Specification<T>` mediante la definicion.
- Responsabilidades: acepta query vacia como `Specification.unrestricted()`; verifica policy `query.enabled`; registra longitud/minimos; exige `SearchQuery` habilitada y reglas si `requireValidator`; prefija violaciones como `query`; envuelve fallos diferidos del predicate.
- Colabora con: `SearchDefinition`, `SearchQuery`, `SearchProtectionContext`, `RuleViolation`, `SearchQueryValidationException`.
- Superficie publica: interna.
- Notas para futuras tareas: una query no vacia sin specification valida debe fallar como reglas prohibidas; conservar el wrapper para errores que aparecen recien dentro de JPA Criteria.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchSpecificationSorting.java
- Tipo: util interno package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: sorting Criteria para casos que Spring Data no puede expresar bien.
- Rol: aplica `orderBy` dentro de la `Specification` cuando el sort necesita `CriteriaBuilder.treat` por subtype.
- Responsabilidades: detecta sort con campos que tienen subtype; remueve sort del `Pageable` validado para evitar doble ordenamiento; construye paths por segmentos; aplica ignore-case con `lower`; emula null handling con `selectCase`; evita orderBy en count queries.
- Colabora con: `SearchDefinition`, `SearchField`, `SearchPath`, JPA Criteria, Spring `Sort`.
- Superficie publica: interna.
- Notas para futuras tareas: cualquier extension de sorting por subtipos debe preservar la omision en count queries y la limpieza del sort en `Pageable`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/definition/SearchDefinition.java
- Tipo: clase publica final con DSL interna.
- Package: `io.github.ggomarighetti.jparsqlsearch.definition`.
- Feature / area: contrato declarativo de busqueda.
- Rol: representa una definicion inmutable para una entidad/caso de uso: campos publicos, paging, query y limites.
- Responsabilidades: mantiene fields por selector en orden de declaracion; expone mapas de paths/operadores/direcciones; resuelve limits efectivos desde policy global, override completo o overlay parcial; permite cerrar recursos de validators; provee DSL `builder().entity(...).fields(...).paging(...).query(...).limits(...)`.
- Colabora con: `SearchField`, `SearchPaging`, `SearchQuery`, `SearchPolicy`, `RsqlOperator`, `SearchCompiler`.
- Superficie publica: API publica central para consumidores.
- Notas para futuras tareas: `limits(Consumer)` hace overlay sobre policy global, mientras `limits(SearchPolicy)` reemplaza completo; mantener esta diferencia clara en docs y tests.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/definition/SearchDefinitionFactory.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.definition`.
- Feature / area: factory Spring-friendly para definiciones.
- Rol: crea builders de `SearchDefinition` inicializados con una policy de aplicacion.
- Responsabilidades: conserva `SearchPolicy` no nula; expone `builder()` que llama a `SearchDefinition.builder(policy)`.
- Colabora con: `JpaRsqlSearchAutoConfiguration`, `SearchPolicy`, `SearchDefinition`.
- Superficie publica: API publica y bean auto-configurado.
- Notas para futuras tareas: util cuando la app quiere que las definiciones hereden path limits globales desde configuration properties en tiempo de build de definicion.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/definition/SearchField.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.definition`.
- Feature / area: selector publico y capacidades por campo.
- Rol: modela un campo expuesto por una definicion, incluyendo tipo, path compartido opcional, subtype opcional, filtering y sorting.
- Responsabilidades: valida selector/tipo; permite `path(...)`, `subtype(...)`, `filterable(...)`, `sortable(...)`, `searchable()`; valida path base si fue declarado; construye filtering/sorting disabled por defecto o habilitados con paths efectivos; cierra recursos de filtering.
- Colabora con: `SearchFiltering`, `SearchSorting`, `SearchPath`, `SearchPolicy.Paths`.
- Superficie publica: API publica de DSL.
- Notas para futuras tareas: `subtype` exige que el subtipo extienda la entidad root y habilita sorting por `CriteriaBuilder.treat`; si se toca herencia, revisar tambien `SearchSpecificationSorting`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/definition/SearchPath.java
- Tipo: util publico final con records publicos `Metadata` y `Topology`.
- Package: `io.github.ggomarighetti.jparsqlsearch.definition`.
- Feature / area: validacion y analisis de paths Java/JPA.
- Rol: valida paths dot-separated contra propiedades/getters/campos Java y deriva metadata de colecciones, joins y to-many.
- Responsabilidades: valida profundidad maxima; resuelve segmentos con BeanUtils y reflection; compara tipo terminal declarado vs resuelto; atraviesa arrays, maps e iterables usando generics; detecta relaciones por anotaciones JPA y entidades; construye topologia con `joinedPaths` y `toManyPaths`; preserva segmentos vacios para rechazar paths mal formados.
- Colabora con: `SearchField`, `SearchFiltering`, `SearchSorting`, `SearchProtectionContext`, `SearchPolicy.Paths`, anotaciones JPA.
- Superficie publica: API publica de soporte/validacion de paths.
- Notas para futuras tareas: esta validacion es Java/reflection, no reemplaza al validador JPA runtime; cuando se agregan tipos genericos raros, revisar sustitucion de `TypeVariable`, `WildcardType` y arrays genericos.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/RsqlFilterValidationException.java
- Tipo: excepcion publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: errores de filtro RSQL.
- Rol: reporta fallas de parseo, reglas semanticas o limites de filtros RSQL.
- Responsabilidades: define codigos `RSQL_PARSE_ERROR`, `RSQL_RULES_FORBIDDEN`, `RSQL_LIMIT_EXCEEDED`; conserva lista inmutable de `RsqlValidationError`; soporta causa opcional.
- Colabora con: `RsqlSearchGuard`, `RsqlRulesValidator`, `SearchRsqlEngine`, `RsqlValidationError`.
- Superficie publica: API publica de errores.
- Notas para futuras tareas: mantener mensajes seguros para exponer en APIs; detalles semanticamente ricos deben ir en `errors()`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/RsqlValidationError.java
- Tipo: record publico serializable.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: detalle de validacion RSQL.
- Rol: representa un error localizado dentro del AST RSQL y sus metadatos de campo/operador/argumento.
- Responsabilidades: define codigos para selector no permitido, filtering disabled, operador no permitido, aridad invalida, conversion fallida y violaciones de reglas; valida `code`, `astPath`, `message` y `argumentIndex`.
- Colabora con: `RsqlRulesValidator`, `FilterValidationError`, excepciones RSQL.
- Superficie publica: DTO publico serializable.
- Notas para futuras tareas: al agregar errores nuevos en validacion semantica, agregar constante aca y revisar tests de serializacion/error DTO.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/SearchDefinitionValidationException.java
- Tipo: excepcion publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: errores de definicion/configuracion.
- Rol: indica que la `SearchDefinition`, backend o conversion declarada es invalida.
- Responsabilidades: define codigos para path depth, path JPA no resuelto, configuracion RSQL invalida, operador no registrado/no ejecutable, mismatch de tipos y defaults no soportados; conserva codigo estable y causa opcional.
- Colabora con: `SearchPath`, `JpaSearchDefinitionValidator`, `SearchRsqlEngine`, `PerplexhubRsqlBackendAdapter`, `DefaultFilterOperators`.
- Superficie publica: API publica de errores.
- Notas para futuras tareas: usar esta excepcion para fallas de configuracion del servidor, no para input dinamico de usuario.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/SearchPageableValidationException.java
- Tipo: excepcion publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: errores de pageable/sort.
- Rol: reporta page, size o sort rechazados por reglas de definicion o limites de proteccion.
- Responsabilidades: define codigos `SORT_RULES_FORBIDDEN`, `PAGE_RULES_FORBIDDEN`, `SORT_LIMIT_EXCEEDED`, `PAGE_LIMIT_EXCEEDED`; conserva violaciones Bean Validation para page/size.
- Colabora con: `SearchPageableGuard`, `RuleViolation`, `SearchPaging`, `SearchSorting`.
- Superficie publica: API publica de errores.
- Notas para futuras tareas: mantener separada la semantica de "rules forbidden" (definicion) y "limit exceeded" (proteccion global/local).

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/SearchProtectionException.java
- Tipo: excepcion publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: limites de proteccion cross-request.
- Rol: indica que el request completo excedio una regla de seguridad configurada.
- Responsabilidades: usa codigo unico `SEARCH_PROTECTION_RULE_EXCEEDED`; expone `rule`, `actual` y `limit`; genera mensaje con nombre de regla.
- Colabora con: `SearchProtectionContext`.
- Superficie publica: API publica de errores.
- Notas para futuras tareas: los nombres de regla son parte practica del contrato de troubleshooting; cambiarlos puede romper asserts o documentacion de usuarios.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/SearchQueryValidationException.java
- Tipo: excepcion publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: errores de query textual.
- Rol: reporta query opcional no permitida o no convertible a specification.
- Responsabilidades: define `QUERY_RULES_FORBIDDEN`; conserva violaciones inmutables de `RuleViolation`; soporta causa opcional para fallos diferidos de JPA Criteria.
- Colabora con: `SearchQueryGuard`, `SearchQuery`, `RuleViolation`.
- Superficie publica: API publica de errores.
- Notas para futuras tareas: si se agregan mas codigos de query, revisar `SearchQueryGuard` y los DTOs de error esperados por integradores.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/exception/ValidationExceptionSupport.java
- Tipo: helper package-private.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: soporte comun de excepciones.
- Rol: centraliza validacion de codigos y copias inmutables de listas.
- Responsabilidades: rechaza codigos nulos/blancos; copia listas con `List.copyOf`.
- Colabora con: excepciones publicas del package.
- Superficie publica: interna.
- Notas para futuras tareas: mantenerlo minimalista para no mezclar logica de dominio de errores en un helper generico.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/filter/DefaultFilterOperators.java
- Tipo: util publico final.
- Package: `io.github.ggomarighetti.jparsqlsearch.filter`.
- Feature / area: perfiles de operadores por tipo.
- Rol: provee whitelists restrictivas por defecto para tipos Java/Spring comunes.
- Responsabilidades: asigna perfiles boolean, exact, ordered y text; soporta primitives via `ClassUtils`; excluye deliberadamente null checks de todos los perfiles; expone `forType` y `supports`.
- Colabora con: `SearchFiltering.Builder.withDefaults`, `RsqlOperators`.
- Superficie publica: API publica de conveniencia.
- Notas para futuras tareas: agregar un tipo default cambia comportamiento de `filterable()`; revisar tests y documentar que `IS_NULL`/`NOT_NULL` siguen siendo opt-in.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/filter/FilterOperator.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.filter`.
- Feature / area: validacion de argumentos por operador.
- Rol: define como convertir y validar los argumentos de un operador RSQL permitido.
- Responsabilidades: convierte strings al `argumentType` usando `ConversionService`; trata fallos de input como `CONVERSION_FAILED`; valida cada argumento y la lista completa con `HibernateRuleValidator`; expone DSL `each(...)` y `args(...)`; cierra validators.
- Colabora con: `SearchFiltering`, `HibernateRuleValidator`, `FilterValidationResult`, `FilterValidationError`, `ConversionService`.
- Superficie publica: API publica de DSL para reglas de operadores.
- Notas para futuras tareas: conversion de `String` es bypass directo; conversiones con `ConversionException` causadas por `IllegalArgumentException` se consideran input invalido, otras se propagan como configuracion/fallo inesperado.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/filter/FilterValidationError.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.filter`.
- Feature / area: detalle interno-publico de validacion de argumentos.
- Rol: captura una falla de conversion o una violacion Bean Validation para un operador.
- Responsabilidades: diferencia `CONVERSION_FAILED`, `ARGUMENT_RULE`, `ARGUMENTS_RULE`; valida campos requeridos segun categoria; ofrece factories y optionals para indice, targetType y violation.
- Colabora con: `FilterOperator`, `RsqlRulesValidator`, `RuleViolation`.
- Superficie publica: API publica de soporte.
- Notas para futuras tareas: mantener la distincion argumento individual vs lista completa porque se traduce distinto a `RsqlValidationError`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/filter/FilterValidationResult.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.filter`.
- Feature / area: resultado de validacion de operadores.
- Rol: agrupa errores de conversion/validacion de un operador.
- Responsabilidades: copia errores de forma inmutable; expone `accepted()` cuando no hay errores.
- Colabora con: `FilterOperator`, `SearchFiltering`.
- Superficie publica: API publica de soporte.
- Notas para futuras tareas: mantenerlo como value object simple; la traduccion a errores RSQL ocurre fuera.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/filter/SearchFiltering.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.filter`.
- Feature / area: politica de filtrado por campo.
- Rol: representa si un selector puede filtrarse, por que path, con que operadores y con que topologia.
- Responsabilidades: ofrece instancia disabled reutilizable; permite path override, defaults, allow y deny; resuelve defaults por tipo; exige al menos un operador; valida path y deriva `requiresDistinct`/topology; construye operadores con tipos explicitos o tipo de campo; valida argumentos de operadores permitidos.
- Colabora con: `SearchField`, `SearchPath`, `DefaultFilterOperators`, `FilterOperator`, `SearchPolicy.Paths`, `SearchDefinitionValidationException`.
- Superficie publica: API publica de DSL y runtime metadata.
- Notas para futuras tareas: `deny` borra operadores explicitos y defaults heredados; si `withDefaults` no soporta el tipo lanza `DEFAULT_OPERATORS_UNSUPPORTED_TYPE`, no un error generico.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/jpa/JpaSearchDefinitionValidator.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.jpa`.
- Feature / area: validacion runtime contra metamodelo JPA.
- Rol: valida que entidades y paths declarados existan realmente en el metamodelo JPA gestionado.
- Responsabilidades: valida entidad root y subtypes; recorre paths de filtering/sorting con `ManagedType` y `Attribute`; atraviesa colecciones via `PluralAttribute` cuando el path sigue despues de una coleccion; compara tipo terminal contra tipo declarado; lanza `JPA_PATH_UNRESOLVED`.
- Colabora con: `EntityManagerFactory`, `SearchDefinition`, `SearchField`, `SearchPath`, `SearchDefinitionValidator`.
- Superficie publica: API publica y bean auto-configurado cuando hay JPA.
- Notas para futuras tareas: complementa la validacion por reflection de `SearchPath`; no mover a build-time porque necesita el metamodelo real de la aplicacion.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/page/SearchPaging.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.page`.
- Feature / area: reglas de paging por definicion.
- Rol: declara si una definicion acepta paginacion y que reglas Bean Validation aplica a page/size.
- Responsabilidades: ofrece disabled reutilizable; valida page y size por separado o juntos; devuelve violaciones seguras; construye validators programaticos para `Integer`; cierra recursos.
- Colabora con: `SearchDefinition`, `SearchPageableGuard`, `HibernateRuleValidator`, `RuleViolation`.
- Superficie publica: API publica de DSL.
- Notas para futuras tareas: la policy global limita rangos duros; estas reglas son contrato especifico de la definicion y producen `PAGE_RULES_FORBIDDEN`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/policy/SearchPolicy.java
- Tipo: clase publica final con records/builders anidados.
- Package: `io.github.ggomarighetti.jparsqlsearch.policy`.
- Feature / area: limites globales y locales de proteccion.
- Rol: concentra defaults endurecidos y reglas cross-component para RSQL, filtro, paging, sorting, query y paths.
- Responsabilidades: expone `defaults()`, `builder()`, `toBuilder()` y overlays parciales; define grupos `Rsql`, `Filter`, `Filter.Like`, `Paging`, `Paging.Page`, `Paging.Slice`, `Sorting`, `Query`, `Paths`; valida positivos/no negativos y relaciones como max >= min; provee defaults como RSQL length 4096, AST 48 nodes, filter 24 comparisons, page max size 100, sort max orders 3, query max length 256, path depth 3.
- Colabora con: `JpaRsqlSearchProperties`, `SearchDefinition`, `SearchCompiler`, `SearchProtectionContext`, `SearchPath`.
- Superficie publica: API publica de configuracion programatica.
- Notas para futuras tareas: `Builder.buildOverlay()` captura solo setters invocados; este mecanismo sostiene `SearchDefinition.limits(Consumer)` y requiere que cada setter anidado registre override correctamente.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/query/SearchQuery.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.query`.
- Feature / area: query textual opcional por definicion.
- Rol: declara validacion y fabrica de `Specification` para un parametro de busqueda libre.
- Responsabilidades: ofrece disabled reutilizable; registra reglas Hibernate Validator sobre `String`; exige `specification(...)` al construir; informa si hay reglas explicitas; valida query y delega a `SearchQuerySpecification`; cierra validator.
- Colabora con: `SearchDefinition`, `SearchQueryGuard`, `HibernateRuleValidator`, `RuleViolation`, Spring Data `Specification`.
- Superficie publica: API publica de DSL.
- Notas para futuras tareas: si la policy exige validator (`query.requireValidator`), `hasRules()` debe reflejar solo reglas explicitamente declaradas, no la existencia de specification.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/query/SearchQuerySpecification.java
- Tipo: interface funcional publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.query`.
- Feature / area: extension point de query textual.
- Rol: convierte texto validado en una `Specification<T>`.
- Responsabilidades: define `toSpecification(String query)`.
- Colabora con: `SearchQuery`, aplicaciones consumidoras, Spring Data `Specification`.
- Superficie publica: SPI publica.
- Notas para futuras tareas: mantenerla minima para lambdas/metodos de referencia; errores lanzados dentro del predicate son envueltos por `SearchQueryGuard`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/sort/SearchSorting.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.sort`.
- Feature / area: capacidades de sort por selector.
- Rol: representa si un campo es sortable, path real, direcciones, ignore-case, null handling y topologia.
- Responsabilidades: ofrece disabled reutilizable; permite path override, direcciones permitidas, ignore-case y null-handling; valida path/tipo; prohibe sorting por paths collection-valued; exige CharSequence para ignore-case; default de direcciones es ASC y DESC, default null handling es NATIVE.
- Colabora con: `SearchField`, `SearchPath`, `SearchPageableGuard`, `SearchSpecificationSorting`, `SearchPolicy.Paths`.
- Superficie publica: API publica de DSL y metadata.
- Notas para futuras tareas: aunque el builder permita null handling, la policy global puede bloquearlo; distinguir aceptacion por definicion vs limite global.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/validation/HibernateRuleValidator.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.validation`.
- Feature / area: ejecucion de reglas Bean Validation programaticas.
- Rol: ejecuta constraints Hibernate Validator sin exponer el valor invalido en los resultados.
- Responsabilidades: retorna `none()` para reglas vacias; crea `ValidatorFactory` programatico con `HibernateValidator` y `ParameterMessageInterpolator`; valida valores; transforma violaciones a `RuleViolation`; ordena resultados deterministamente; cierra factories con `Cleaner` y `AutoCloseable`.
- Colabora con: `SearchPaging`, `SearchQuery`, `FilterOperator`, `RuleViolation`.
- Superficie publica: API publica de soporte.
- Notas para futuras tareas: cuidado con recursos si se crean definiciones dinamicas; por eso `SearchDefinition.close()` propaga cierres.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/validation/RuleViolation.java
- Tipo: record publico serializable.
- Package: `io.github.ggomarighetti.jparsqlsearch.validation`.
- Feature / area: DTO seguro de Bean Validation.
- Rol: expone path, mensaje, template y tipo de constraint sin incluir invalid value.
- Responsabilidades: valida campos no nulos; permite `withPath` y `prefixed` para contextualizar page/size/query/arguments.
- Colabora con: `HibernateRuleValidator`, excepciones de query/pageable, `FilterValidationError`.
- Superficie publica: DTO publico.
- Notas para futuras tareas: no agregar invalid value; el comentario de seguridad es intencional.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/validation/SearchDefinitionValidator.java
- Tipo: interface funcional publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.validation`.
- Feature / area: SPI de validacion de definiciones.
- Rol: permite enchufar validadores runtime de `SearchDefinition`.
- Responsabilidades: define `validate(SearchDefinition<?>)`.
- Colabora con: `RsqlSearchGuard`, `JpaSearchDefinitionValidator`, auto-configuracion Spring.
- Superficie publica: SPI publica.
- Notas para futuras tareas: los validators se ejecutan una vez por instancia de definicion en `RsqlSearchGuard`; si un validator depende de estado mutable externo, entender el cache.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/RsqlAst.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: representacion de AST RSQL parseado.
- Rol: envuelve el nodo nativo del parser y una vista normalizada de comparaciones en orden izquierda-a-derecha.
- Responsabilidades: recorre `ComparisonNode`/`LogicalNode`; traduce `ComparisonOperator` a `RsqlOperatorDescriptor`; crea lista inmutable de `RsqlComparison`.
- Colabora con: `SearchRsqlEngine`, `RsqlOperatorRegistry`, parser nstdio/jirutka.
- Superficie publica: API publica de bajo nivel para backend/custom integrations.
- Notas para futuras tareas: si se soportan nuevos tipos de nodo, actualizar el recorrido y los validators.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/RsqlComparison.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: comparacion RSQL normalizada.
- Rol: representa selector, operador logico y argumentos crudos de una comparacion.
- Responsabilidades: valida selector no blanco, operador no nulo y copia argumentos inmutable.
- Colabora con: `RsqlAst`, backends/consumidores de bajo nivel.
- Superficie publica: API publica de bajo nivel.
- Notas para futuras tareas: los argumentos permanecen como `String`; conversion ocurre despues con `ConversionService`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/RsqlCompilationRequest.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: request backend-neutral de compilacion.
- Rol: transporta todo lo que un backend necesita para producir una `Specification`.
- Responsabilidades: exige RSQL no blanco, AST, definition, conversion service y registry; incluye flag `distinct`.
- Colabora con: `RsqlBackendAdapter`, `RsqlSearchGuard`, `SearchRsqlEngine`.
- Superficie publica: API publica/SPI para backends.
- Notas para futuras tareas: mantenerlo backend-neutral; no filtrar detalles de Perplexhub aca.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/SearchRsqlEngine.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: motor RSQL configurable.
- Rol: coordina registry de operadores, parser, backend y conversion service.
- Responsabilidades: crea builder/defaults; parsea RSQL y transforma errores a `RSQL_PARSE_ERROR`; compila requests delegando al backend; valida definiciones contra operadores registrados, tipos de argumento y conversion desde String; delega validacion backend-specific.
- Colabora con: `SearchRsqlEngineBuilder`, `RsqlParserFactory`, `RsqlBackendAdapter`, `RsqlOperatorRegistry`, `SearchDefinition`.
- Superficie publica: API publica de bajo nivel.
- Notas para futuras tareas: callers directos deben aplicar policy/protecciones por su cuenta; el camino recomendado sigue siendo `SearchCompiler`.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/SearchRsqlEngineBuilder.java
- Tipo: builder publico final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: configuracion del motor RSQL.
- Rol: permite personalizar dialecto, parser, backend y conversion service.
- Responsabilidades: arranca con operadores default, parser default, backend Perplexhub y `ApplicationConversionService`; permite remover defaults, agregar descriptors, reemplazar parser/backend/conversion service; construye `RsqlOperatorRegistry`.
- Colabora con: `SearchRsqlEngine`, `DefaultRsqlOperatorDescriptors`, `DefaultRsqlParserFactory`, `PerplexhubRsqlBackendAdapter`.
- Superficie publica: API publica y target de `SearchRsqlEngineCustomizer`.
- Notas para futuras tareas: duplicates se rechazan recien al construir el registry; tests de custom dialect deben cubrir simbolos duplicados.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/SearchRsqlEngineCustomizer.java
- Tipo: interface funcional publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: extension Spring del motor.
- Rol: permite customizar el builder auto-configurado via beans ordenados.
- Responsabilidades: define `customize(SearchRsqlEngineBuilder)`.
- Colabora con: `JpaRsqlSearchRsqlAutoConfiguration`.
- Superficie publica: SPI publica.
- Notas para futuras tareas: mantener compatible con lambdas; no agregar metodos abstractos.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/RsqlBackendAdapter.java
- Tipo: interface publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend`.
- Feature / area: SPI de backend RSQL.
- Rol: traduce un request RSQL validado a una `Specification`.
- Responsabilidades: define `compile`; ofrece `validate` default no-op para compatibilidad de definiciones/backends.
- Colabora con: `SearchRsqlEngine`, `RsqlCompilationRequest`, `PerplexhubRsqlBackendAdapter`.
- Superficie publica: SPI publica.
- Notas para futuras tareas: backends alternativos deben validar que pueden ejecutar operadores custom antes del runtime de Criteria.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/RsqlJpaPredicateContext.java
- Tipo: record publico generico.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend`.
- Feature / area: contexto para operadores custom JPA.
- Rol: entrega estado Criteria y argumentos convertidos a una fabrica custom de predicates.
- Responsabilidades: conserva `CriteriaBuilder`, path, attribute, root, operador y argumentos inmutables; expone `argument(index)`.
- Colabora con: `RsqlJpaPredicateFactory`, `PerplexhubRsqlBackendAdapter`.
- Superficie publica: SPI publica.
- Notas para futuras tareas: `attribute` puede venir nulo segun backend/input; el record no lo exige actualmente, revisar antes de asumirlo.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/RsqlJpaPredicateFactory.java
- Tipo: interface funcional publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend`.
- Feature / area: operadores custom JPA.
- Rol: permite construir un `Predicate` para un operador RSQL custom.
- Responsabilidades: define `toPredicate(RsqlJpaPredicateContext<?, ?, ?, ?, ?>)`.
- Colabora con: `RsqlOperatorDescriptor`, `PerplexhubRsqlBackendAdapter`, JPA Criteria.
- Superficie publica: SPI publica.
- Notas para futuras tareas: mantener la firma generica amplia para no atar extensiones a un solo tipo de path/root.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/perplexhub/PerplexhubRsqlBackendAdapter.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub`.
- Feature / area: backend default RSQL-to-JPA.
- Rol: implementa compilacion RSQL con `RSQLJPAPredicateConverter` de Perplexhub.
- Responsabilidades: aplica `query.distinct(true)` cuando el guard lo pide; crea `PredicateContext` por ejecucion; soporta subtypes con `criteriaBuilder.treat`; traduce AND/OR/comparison recursivamente; registra custom predicates desde descriptors; valida que operadores no default tengan factory, argument type y type Comparable.
- Colabora con: Perplexhub `RSQLJPAPredicateConverter`, `RSQLCustomPredicate`, `RsqlCompilationRequest`, `SearchDefinition`, `RsqlOperatorDescriptor`.
- Superficie publica: backend publico default.
- Notas para futuras tareas: custom operators en Perplexhub requieren `Comparable` aunque el dominio conceptual no ordene; si se cambia backend, revisar esta restriccion.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/perplexhub/PerplexhubRsqlBackendOptions.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub`.
- Feature / area: opciones del backend Perplexhub.
- Rol: configura igualdad estricta y escape char de LIKE.
- Responsabilidades: default `strictEquality=true`, `likeEscapeCharacter=null`; ofrece builder y customizer.
- Colabora con: `PerplexhubRsqlBackendAdapter`, auto-config properties.
- Superficie publica: API publica de configuracion backend.
- Notas para futuras tareas: `strictEquality` protege contra semanticas wildcard inesperadas de igualdad; cambiar el default alteraria queries existentes.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/DefaultRsqlOperatorDescriptors.java
- Tipo: util publico final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: dialecto RSQL default.
- Rol: declara descriptors de todos los operadores built-in soportados por el backend default.
- Responsabilidades: lista operadores default; define simbolos/aliases y aridades para igualdad, comparaciones, IN/OUT, null checks, LIKE/ILIKE y BETWEEN; marca descriptors como `defaultJpaSupported`.
- Colabora con: `SearchRsqlEngineBuilder`, `RsqlOperators`, `RsqlOperatorDescriptor`.
- Superficie publica: API publica de soporte.
- Notas para futuras tareas: al agregar un operador built-in, actualizar aca, `RsqlOperators`, default profiles si aplica, backend/tests.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/RsqlOperator.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: identificador logico de operador.
- Rol: da un nombre estable independiente de los simbolos parser.
- Responsabilidades: valida/no normaliza a blanco, trim del nombre, factory `of`, `toString`.
- Colabora con: descriptors, filters, policy checks.
- Superficie publica: API publica.
- Notas para futuras tareas: la igualdad se basa en el nombre; nombres son contrato.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/RsqlOperatorArity.java
- Tipo: record publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: aridad de operadores.
- Rol: define rango inclusivo de argumentos aceptados.
- Responsabilidades: valida min/max; provee `exact`, `between`, `atLeast`, `accepts`; convierte a aridad del parser.
- Colabora con: `RsqlOperatorDescriptor`, `RsqlRulesValidator`, parser RSQL.
- Superficie publica: API publica.
- Notas para futuras tareas: `atLeast` usa `Integer.MAX_VALUE`; las policies de max argumentos siguen limitando requests reales.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/RsqlOperatorDescriptor.java
- Tipo: clase publica final con builder.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: metadata completa de operador.
- Rol: une operador logico, simbolos parser, aridad, tipo custom y predicate factory opcional.
- Responsabilidades: valida simbolos; expone symbol primary y aliases; crea `ComparisonOperator`; configura `argumentType`, `jpaPredicate`, `defaultJpaSupported`.
- Colabora con: `RsqlOperatorRegistry`, `SearchRsqlEngine`, `PerplexhubRsqlBackendAdapter`, `RsqlJpaPredicateFactory`.
- Superficie publica: API publica de extension.
- Notas para futuras tareas: `defaultJpaSupported` es package-private setter para built-ins; custom operators externos deben declarar `jpaPredicate` si usan backend Perplexhub.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/RsqlOperatorRegistry.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: registry inmutable de operadores.
- Rol: permite lookup por operador logico o simbolo parser y genera operadores nativos para el parser.
- Responsabilidades: rechaza duplicados de operador o simbolo; mantiene orden de registro; expone descriptors, parser operators, `descriptor`, `require`.
- Colabora con: `SearchRsqlEngine`, `DefaultRsqlParserFactory`, `RsqlAst`.
- Superficie publica: API publica de bajo nivel.
- Notas para futuras tareas: si se agregan aliases, asegurar que no colisionen con simbolos existentes como `==`, `=in=`, etc.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/operator/RsqlOperators.java
- Tipo: constantes publicas final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.operator`.
- Feature / area: operadores built-in logicos.
- Rol: centraliza identificadores para igualdad, comparaciones, listas, null checks, LIKE/case-insensitive y ranges.
- Responsabilidades: define `EQUAL`, `NOT_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `IN`, `NOT_IN`, `IS_NULL`, `NOT_NULL`, `LIKE`, `NOT_LIKE`, `IGNORE_CASE`, `IGNORE_CASE_LIKE`, `IGNORE_CASE_NOT_LIKE`, `BETWEEN`, `NOT_BETWEEN`.
- Colabora con: default descriptors, default filter operators, protection context.
- Superficie publica: API publica de constantes.
- Notas para futuras tareas: no reutilizar nombres para semanticas distintas; son parte del contrato externo.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/parser/DefaultRsqlParserFactory.java
- Tipo: clase publica final.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.parser`.
- Feature / area: parser default.
- Rol: crea `RSQLParser` configurado con operadores registrados del engine.
- Responsabilidades: implementa factory stateless; pasa `operators.parserOperators()` al parser.
- Colabora con: `SearchRsqlEngine`, nstdio/jirutka `RSQLParser`.
- Superficie publica: API publica de soporte.
- Notas para futuras tareas: crear parser por parse operation evita compartir estado accidental.

### src/main/java/io/github/ggomarighetti/jparsqlsearch/rsql/parser/RsqlParserFactory.java
- Tipo: interface funcional publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.parser`.
- Feature / area: SPI de parser.
- Rol: permite reemplazar la creacion del parser RSQL.
- Responsabilidades: define `create(RsqlOperatorRegistry)`.
- Colabora con: `SearchRsqlEngineBuilder`, `SearchRsqlEngine`.
- Superficie publica: SPI publica.
- Notas para futuras tareas: mantener salida como `RSQLParser` nativo porque el engine espera parsear a AST compatible.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchAutoConfigurationTest.java
- Tipo: test unitario de auto-configuracion.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: autoconfiguracion principal.
- Rol: verifica beans default, backoff y binding de limites globales en contexto Spring.
- Responsabilidades: asegura que se crea `SearchCompiler`; que al deshabilitar RSQL default no se crea compiler salvo engine provisto; que no se expone configuracion Postgres antigua; que `SearchDefinitionFactory` usa path limits globales; que properties de proteccion llegan a `SearchCompiler`; que no se requieren beans `SearchDefinition`.
- Colabora con: `ApplicationContextRunner`, `JpaRsqlSearchAutoConfiguration`, `JpaRsqlSearchRsqlAutoConfiguration`, `SearchDefinitionFactory`, `SearchCompiler`.
- Superficie publica: test de contrato Spring Boot.
- Notas para futuras tareas: si cambia el nombre de beans o la condicion `rsql.enabled`, actualizar estos tests primero.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchPropertiesTest.java
- Tipo: test unitario de binding.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: configuration properties.
- Rol: garantiza que cada propiedad `jpa.rsql.search.*` se bindea y se refleja en `SearchPolicy`.
- Responsabilidades: cubre rsql/perplexhub, filter, filter.like, paging/page/slice, sorting, query y paths; usa `Binder` con `MapConfigurationPropertySource`; compara propiedades y policy resultante.
- Colabora con: `JpaRsqlSearchProperties`, `SearchPolicy`, Spring Boot Binder.
- Superficie publica: test de contrato de configuracion externa.
- Notas para futuras tareas: obligatorio actualizar al agregar, quitar o renombrar cualquier property.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/autoconfigure/JpaRsqlSearchRsqlAutoConfigurationTest.java
- Tipo: test unitario de auto-configuracion RSQL.
- Package: `io.github.ggomarighetti.jparsqlsearch.autoconfigure`.
- Feature / area: wiring RSQL, converters, customizers y backends.
- Rol: protege el comportamiento de la auto-configuracion del motor RSQL.
- Responsabilidades: verifica que no se expongan guards internos; que el contrato de conversion sea `ConversionService`; que converter beans alimenten el engine; que customizers agreguen operadores; que un engine provisto reemplace default y no reciba customizers; que un backend provisto sea usado; que validators runtime entren al guard; que un `ConversionService` unico se use para validacion y ejecucion.
- Colabora con: `SearchRsqlEngineBuilder`, `SearchRsqlEngineCustomizer`, `RsqlBackendAdapter`, `SearchDefinitionValidator`, `ApplicationContextRunner`.
- Superficie publica: test de contrato Spring Boot/SPI.
- Notas para futuras tareas: estos tests son alarma temprana si se cambia como se elige `ConversionService` o se aplican customizers.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlPropertyTest.java
- Tipo: property/fuzz test.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: robustez de filtro RSQL.
- Rol: prueba que inputs RSQL arbitrarios no escapen con excepciones inesperadas.
- Responsabilidades: genera 2500 inputs pseudoaleatorios; verifica unknown selectors y operadores no permitidos; prueba limites exactos N/N+1; valida conversiones tipadas; envuelve fallos diferidos del backend.
- Colabora con: `RsqlSearchGuard`, `RsqlInputGenerator`, `SearchPropertyFixtures`, dominio `Product`.
- Superficie publica: test de robustez interna.
- Notas para futuras tareas: si aparece un nuevo tipo esperado de excepcion, actualizar `SearchPropertyFixtures.isExpectedRsqlThrowable`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlRegressionCorpusTest.java
- Tipo: test de corpus de regresion.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: hardening RSQL.
- Rol: ejecuta un corpus de entradas raras para evitar throwables no controlados.
- Responsabilidades: carga `/corpus/rsql-weird-corpus.txt`; decodifica tokens especiales como `<EMPTY>`, `<TAB>`, `<SNOWMAN>`; acepta solo excepciones esperadas.
- Colabora con: `RsqlSearchGuard`, `SearchPropertyFixtures`, recurso de corpus.
- Superficie publica: test de robustez.
- Notas para futuras tareas: agregar aqui inputs que hayan causado bugs de parser/preflight.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlSearchGuardTest.java
- Tipo: test unitario de guard RSQL.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: validacion/compilacion RSQL.
- Rol: cubre reglas de `RsqlSearchGuard`, `RsqlRulesValidator` y compatibilidad de engine/backend.
- Responsabilidades: verifica acceso a engine/policy; specs para campos permitidos; wrapping de fallos inmediatos y diferidos; distinct por paths collection-valued; operadores custom no ejecutables o con tipos incorrectos; conversiones y validators por argumento/lista; errores de parse/preflight; limites de AST/comparaciones/argumentos/OR; orden de aplicacion de protecciones antes de errores semanticos; helpers privados por reflection.
- Colabora con: `SearchRsqlEngine`, `RsqlBackendAdapter`, `RsqlOperatorDescriptor`, `RsqlRulesValidator`, `SearchProtectionContext`, `TestTypes`.
- Superficie publica: test profundo de infraestructura interna.
- Notas para futuras tareas: este archivo es la red principal para cambios de validacion RSQL; si se cambia prioridad de errores, varias aserciones de codigo/path deben moverse.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/RsqlSearchGuardTestAccess.java
- Tipo: helper de test publico.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: introspeccion de beans internos.
- Rol: permite a tests de autoconfiguracion detectar si se expuso un `RsqlSearchGuard`.
- Responsabilidades: metodo `isRsqlSearchGuard(Object)`.
- Colabora con: `JpaRsqlSearchRsqlAutoConfigurationTest`.
- Superficie publica: test support.
- Notas para futuras tareas: mantenerlo pequeno para no abrir acceso indirecto a mas internals.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchPageableGuardTest.java
- Tipo: test unitario de pageable/sort.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: validacion de `Pageable`.
- Rol: protege traduccion de aliases de sort, bounded unpaged y errores page/sort.
- Responsabilidades: valida traduccion selector->path; conserva ignoreCase/null handling cuando policy lo permite; convierte unpaged permitido en page acotada; rechaza paths duplicados, selectors desconocidos, campos sin sorting, direcciones prohibidas, paging no declarado, limites globales y violaciones Bean Validation de page/size.
- Colabora con: `SearchPageableGuard`, `SearchDefinition`, `SearchPolicy`, `SearchPageableValidationException`.
- Superficie publica: test de comportamiento interno y contrato observable de `CompiledSearch.pageable`.
- Notas para futuras tareas: cualquier cambio en defaults de unpaged size o sort safety debe actualizar expectativas aca.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchPageablePropertyTest.java
- Tipo: property/fuzz test.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: robustez de pageable/sort.
- Rol: genera pageables/sorts aleatorios y asegura que solo fallen con excepciones esperadas.
- Responsabilidades: prueba 2500 pageables; valida que sorts aceptados traduzcan propiedades a paths sin duplicados y preserven direction/ignoreCase/nullHandling.
- Colabora con: `PageableInputGenerator`, `SearchPropertyFixtures`, `SearchPageableGuard`.
- Superficie publica: test de robustez.
- Notas para futuras tareas: mantener sincronizado `isPotentiallyAcceptable` con las reglas de `SearchPropertyFixtures.pageableDefinition`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchProtectionTest.java
- Tipo: test unitario de protecciones cross-component.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: `SearchProtectionContext` y defaults de `SearchPolicy`.
- Rol: verifica limites de filtro, LIKE, to-many, sort, page/slice, query y combinaciones entre componentes.
- Responsabilidades: cubre max in/not-in/between/negated/or/join roots; wildcards escapados; reglas LIKE; deshabilitar to-many filtering/sorting; distinct requerido; branch combinations inversas; slice vs page count; query requireValidator; relation sorting; bounded unpaged + query; defaults endurecidos.
- Colabora con: `SearchProtectionContext`, `SearchCompiler`, `SearchQueryGuard`, `SearchPageableGuard`, `SearchPolicy`.
- Superficie publica: test de seguridad funcional.
- Notas para futuras tareas: al agregar un limite nuevo, este es el lugar natural para cubrir interacciones con otros componentes.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchQueryGuardTest.java
- Tipo: test unitario de query textual.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: query opcional.
- Rol: protege validacion, policy y wrapping de errores de `SearchQueryGuard`.
- Responsabilidades: query blank -> unrestricted; rechaza query sin definicion, policy disabled o requireValidator sin reglas; valida reglas Hibernate; preserva string original; rechaza specification null; envuelve fallos inmediatos/diferidos y preserva excepciones propias.
- Colabora con: `SearchQueryGuard`, `SearchDefinition`, `SearchPolicy`, `SearchQueryValidationException`.
- Superficie publica: test de contrato query.
- Notas para futuras tareas: si se decide trim de query, este test fallara porque hoy se preserva input original.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/compile/SearchSpecificationSortingTest.java
- Tipo: test unitario con proxies Criteria.
- Package: `io.github.ggomarighetti.jparsqlsearch.compile`.
- Feature / area: sorting Criteria por subtype/null handling.
- Rol: verifica que `SearchSpecificationSorting` detecte, remueva y aplique ordenamientos dentro de Criteria.
- Responsabilidades: detecta sort que necesita `treat`; remueve sort del pageable; aplica orders solo a content queries, no count; usa `lower` para ignore-case; usa `selectCase` para null handling; valida que null handling nativo produzca un solo order.
- Colabora con: `SearchSpecificationSorting`, JPA Criteria proxies, `SearchDefinition`.
- Superficie publica: test de infraestructura interna.
- Notas para futuras tareas: si se cambia implementacion de null ordering, revisar cantidad y orden de llamadas `asc/desc`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/exception/ExceptionSerializationTest.java
- Tipo: test unitario de serializacion/validacion de excepciones.
- Package: `io.github.ggomarighetti.jparsqlsearch.exception`.
- Feature / area: contrato de errores publicos.
- Rol: asegura que excepciones y detalles serializables sobrevivan round-trip Java serialization.
- Responsabilidades: rechaza codigos blancos y listas nulas; serializa/deserializa `RsqlFilterValidationException`, `SearchPageableValidationException`, `SearchQueryValidationException`; compara code, message y details.
- Colabora con: excepciones publicas, `RsqlValidationError`, `RuleViolation`, `ExceptionAssertions`.
- Superficie publica: test de contrato API.
- Notas para futuras tareas: si se agregan campos a excepciones serializables, agregar asserts de round-trip.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/property/PageableInputGenerator.java
- Tipo: generador de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.property`.
- Feature / area: property-based tests de pageable/sort.
- Rol: produce `Pageable` y `Sort` pseudoaleatorios reproducibles.
- Responsabilidades: elige propiedades validas e invalidas, direcciones, null handling, ignoreCase, unpaged ocasional, page/size dentro y fuera de limites.
- Colabora con: `SearchPageablePropertyTest`.
- Superficie publica: test support.
- Notas para futuras tareas: ajustar `PROPERTIES` cuando cambien aliases de `SearchPropertyFixtures`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/property/RsqlInputGenerator.java
- Tipo: generador de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.property`.
- Feature / area: property-based tests de RSQL.
- Rol: produce strings RSQL variados, validos, invalidos y aleatorios.
- Responsabilidades: mezcla caracteres ASCII, separadores RSQL, quotes, backslash, whitespace, acentos y snowman; genera comparaciones, compuestos, grupos, selectors desconocidos y operadores prohibidos.
- Colabora con: `RsqlPropertyTest`.
- Superficie publica: test support.
- Notas para futuras tareas: agregar casos nuevos de parser/preflight a `VALUES` o `CHARS` si aparecen regresiones.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/property/SearchPropertyFixtures.java
- Tipo: fixture de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.property`.
- Feature / area: definiciones compartidas para property tests.
- Rol: define contratos de busqueda reproducibles para fuzzing RSQL y pageable.
- Responsabilidades: declara codigos de excepcion esperados; mapa de paths sorting; crea `rsqlDefinition` con campos `sku`, `name`, `amount`, `stock`, `status`, `releaseDate`, `reviewRating`; crea `pageableDefinition` con aliases sortable y reglas; clasifica throwables esperados.
- Colabora con: `RsqlPropertyTest`, `SearchPageablePropertyTest`, dominio `Product`.
- Superficie publica: test support.
- Notas para futuras tareas: mantener estos fixtures alineados con los generators para evitar falsos positivos de fuzzing.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/rsql/RsqlAstTest.java
- Tipo: test unitario de AST.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql`.
- Feature / area: AST RSQL normalizado.
- Rol: verifica la vista `comparisons()` de `RsqlAst`.
- Responsabilidades: rechaza comparaciones con operadores no registrados; ignora nodos no soportados al construir vista de comparaciones; preserva orden izquierda-a-derecha.
- Colabora con: `RsqlAst`, `SearchRsqlEngine`, `RsqlOperatorRegistry`.
- Superficie publica: test de API de bajo nivel.
- Notas para futuras tareas: si se decide que nodos no soportados deban fallar durante normalizacion, ajustar este contrato.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/rsql/backend/perplexhub/PerplexhubRsqlBackendAdapterTest.java
- Tipo: test unitario de backend con proxies Criteria.
- Package: `io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub`.
- Feature / area: backend Perplexhub.
- Rol: cubre casos defensivos del adapter default fuera de integracion real con DB.
- Responsabilidades: falla si un selector validado ya no existe en definition; falla con AST node no soportado; no toca `query.distinct` cuando request no lo pide; activa distinct solo cuando `request.distinct=true`.
- Colabora con: `PerplexhubRsqlBackendAdapter`, `RsqlCompilationRequest`, `RsqlAst`, JPA Criteria proxies.
- Superficie publica: test de infraestructura backend.
- Notas para futuras tareas: estos tests no verifican SQL real; para planes/queries reales mirar integration Postgres.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/DefaultFilterOperatorsTest.java
- Tipo: test unitario.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: perfiles default de operadores.
- Rol: protege el set de operadores por tipo y la semantica de defaults/deny/allow.
- Responsabilidades: verifica perfiles restrictivos para tipos comunes; excluye null checks; prueba `filterable()` y `withDefaults`; permite agregar/denegar/restaurar operadores; rechaza defaults sin perfil; asegura inmutabilidad y `supports`.
- Colabora con: `DefaultFilterOperators`, `SearchDefinition`, `SearchFiltering`.
- Superficie publica: test de contrato de DSL.
- Notas para futuras tareas: cualquier cambio en perfiles default debe actualizar estas expectativas y README.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/DocumentationPolicyExamplesTest.java
- Tipo: test de ejemplos de documentacion.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: ejemplos de policy.
- Rol: asegura que snippets de policy usados en docs/README sigan compilando.
- Responsabilidades: construye definiciones con overrides de rsql, filter, paging, sorting, query y paths.
- Colabora con: `SearchDefinition`, `TestTypes`.
- Superficie publica: test de documentacion.
- Notas para futuras tareas: actualizarlo junto con ejemplos del README para evitar drift.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/ExceptionAssertions.java
- Tipo: helper de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: assertions compartidas.
- Rol: alias generico para `Assertions.assertThrows`.
- Responsabilidades: metodo `thrownBy(Class<T>, Executable)`.
- Colabora con: tests que quieren asserts mas compactos.
- Superficie publica: test support.
- Notas para futuras tareas: no agregar logica compleja; ayuda a legibilidad.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/JpaSearchDefinitionValidatorTest.java
- Tipo: test unitario con mocks/proxies de metamodelo.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: validador JPA.
- Rol: cubre resolucion de entidad, atributos, colecciones y subtypes contra metamodelo JPA.
- Responsabilidades: rechaza entidad no managed, type mismatch, segmentos vacios, collection attribute no plural, nested type missing; resuelve plural attributes por element type; permite terminal plural sin traversal; rechaza subtype fuera de jerarquia.
- Colabora con: `JpaSearchDefinitionValidator`, JPA metamodel proxies, `SearchDefinition`.
- Superficie publica: test de validador runtime.
- Notas para futuras tareas: si se cambia traversal de plural attributes, revisar cuidadosamente los casos terminal vs intermedio.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/PublicApiSurfaceTest.java
- Tipo: test de superficie publica.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: encapsulacion/API.
- Rol: asegura que internals sigan package-private y que SPIs RSQL clave sigan publicos.
- Responsabilidades: verifica clases internas de `compile` y `autoconfigure`; confirma que no exista `InternalApi`; valida public methods de `SearchRsqlEngine`, builder y policy overlay.
- Colabora con: reflection Java, `SearchRsqlEngine`, `SearchPolicy`.
- Superficie publica: test de contrato binario/API.
- Notas para futuras tareas: antes de hacer publica una clase interna, decidir si se quiere comprometer compatibilidad.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/RsqlEngineCoverageTest.java
- Tipo: test unitario de engine/dialecto.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchRsqlEngine`, builder y backend validation.
- Rol: cubre configuracion custom del motor y compatibilidad de operadores.
- Responsabilidades: builder con operadores bulk/parser/backend/conversion; builder sin defaults; AST normalizado; rechazo de operadores no registrados/type mismatch/no converter; custom backend permite no Comparable; String no requiere converter; opciones Perplexhub y requisitos de custom predicates Comparable.
- Colabora con: `SearchRsqlEngine`, `SearchRsqlEngineBuilder`, `RsqlOperatorDescriptor`, `PerplexhubRsqlBackendAdapter`.
- Superficie publica: test de API/SPI RSQL.
- Notas para futuras tareas: si se flexibiliza Perplexhub para tipos no Comparable, actualizar este contrato.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SearchCompilerTest.java
- Tipo: test unitario de fachada.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchCompiler`.
- Rol: protege la combinacion de filtro, query, specs obligatorias, pageable y validadores.
- Responsabilidades: constructores con policy; combina RSQL + query + specs con AND; expone solo entradas publicas esperadas; aplica specs aun sin filter/query; rechaza specs nulas; devuelve `CompiledSearch`; soporta empties; hereda policy; valida definicion sin RSQL; cachea validacion exitosa por instancia; no cachea fallas; cubre concurrencia de validacion.
- Colabora con: `SearchCompiler`, `SearchRsqlEngine`, `SearchDefinitionValidator`, `CompiledSearch`.
- Superficie publica: test de API principal.
- Notas para futuras tareas: cualquier cambio de orden/combinacion de specifications puede romper consumidores.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SearchDefinitionTest.java
- Tipo: test unitario amplio de DSL.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchDefinition`, `SearchField`, `SearchPath`, limits.
- Rol: cubre el contrato declarativo completo de definiciones.
- Responsabilidades: valida tipos/path/subtypes; filtering/sorting overrides; searchable; direcciones; materializacion de limits y overlays; explicit policy vs global; duplicados; estados disabled por defecto; query inline/reusable; lifecycle/close; topologia de joins/to-many; inmutabilidad; paths profundos; colecciones; errores de sorting collection-valued e ignore-case no texto.
- Colabora con: `SearchDefinition`, `SearchField`, `SearchFiltering`, `SearchSorting`, `SearchPath`, `SearchPolicy`.
- Superficie publica: test principal de DSL.
- Notas para futuras tareas: este es el archivo a revisar cuando se cambie la experiencia de builder.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SearchFilteringTest.java
- Tipo: test unitario de filtering por campo.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchFiltering`.
- Rol: cubre whitelists de operadores, defaults y conversion/validacion de argumentos.
- Responsabilidades: acepta operadores permitidos y rechaza faltantes; rechaza duplicados; reporta default profile unsupported; `deny` sobre defaults; conversion failures explicitos; conversion exceptions causadas por input; distinct requerido por terminal collection filtering.
- Colabora con: `SearchFiltering`, `FilterOperator`, `ConversionService`, `SearchPath`.
- Superficie publica: test de DSL/runtime metadata.
- Notas para futuras tareas: mantener claro que conversion failures de input no son errores de configuracion.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SearchPathTest.java
- Tipo: test unitario amplio de reflection/path.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchPath`.
- Rol: cubre resolucion de propiedades, generics, colecciones y topologia JPA.
- Responsabilidades: defaults de depth; getters/setters/campos heredados; arrays/maps/iterables; generics con bounds y supertypes concretos; terminal collection metadata; rechazos de paths sin element type; segmentos blancos/missing/too deep; annotations JPA para joined/to-many; defensividad de topology; helpers privados de generics por reflection.
- Colabora con: `SearchPath`, clases internas fixture con generics/anotaciones JPA.
- Superficie publica: test de soporte critico de paths.
- Notas para futuras tareas: cambios en resolucion generica suelen ser delicados; correr este test completo al tocar `SearchPath`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SearchPolicyTest.java
- Tipo: test unitario de policy.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: `SearchPolicy`.
- Rol: protege defaults, builders anidados y validaciones numericas.
- Responsabilidades: compara builders standalone con grupos default; verifica setters de nested builders; rechaza limites invalidos y relaciones max/min imposibles.
- Colabora con: `SearchPolicy`, `ExceptionAssertions`.
- Superficie publica: test de configuracion programatica.
- Notas para futuras tareas: al cambiar defaults, este test fuerza actualizacion consciente.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SmallApiCoverageTest.java
- Tipo: test unitario de value objects/APIs pequenas.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: cobertura de APIs pequeñas.
- Rol: cubre comportamientos chicos que no merecen archivos dedicados.
- Responsabilidades: disabled paging/query; reglas de paging; builder de query; sorting acceptance; optional details de `FilterValidationError`; prefix/withPath de `RuleViolation`; validaciones de `RsqlValidationError`; arity; descriptor/registry de operadores.
- Colabora con: `SearchPaging`, `SearchQuery`, `SearchSorting`, `FilterValidationError`, `RuleViolation`, operadores RSQL.
- Superficie publica: test de APIs publicas menores.
- Notas para futuras tareas: buen lugar para agregar asserts de nuevos value objects simples.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/SpringConfigurationMetadataIT.java
- Tipo: integration test de artefacto jar.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: metadata Spring Boot publicada.
- Rol: verifica que el jar construido contiene `spring-configuration-metadata.json` completo y descrito.
- Responsabilidades: busca jar en `target`; abre ZIP; parsea metadata con Jackson; exige propiedades con descripcion no vacia; revisa propiedades clave y default values.
- Colabora con: Maven build, Spring configuration processor, `JpaRsqlSearchProperties`.
- Superficie publica: test de publicacion/metadata.
- Notas para futuras tareas: requiere que el jar exista antes del integration test; falla si no se ejecuto fase de package.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/unit/TestTypes.java
- Tipo: fixture de tipos Java.
- Package: `io.github.ggomarighetti.jparsqlsearch.unit`.
- Feature / area: modelos simples para unit tests.
- Rol: provee clases POJO sin JPA para validar paths, tipos y subtypes.
- Responsabilidades: define `Product`, `PerishableProduct`, `Review`, `Person`, `Owner`, `Customer`, `Region`, `Country`, enum `Status`; expone getters para price/email/name/createdAt/person/owner/customer/tags/reviews/rawReviews/status.
- Colabora con: tests de definition, filtering, path, pageable, query, compiler.
- Superficie publica: test fixture.
- Notas para futuras tareas: no son entidades JPA; para integracion real usar fixtures bajo `integration/bench` o `integration/inheritance`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/dao/ProductRepository.java
- Tipo: repositorio Spring Data de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.dao`.
- Feature / area: integracion JPA producto.
- Rol: repositorio real usado por ITs para ejecutar `Specification<Product>`.
- Responsabilidades: extiende `JpaRepository<Product, Long>` y `JpaSpecificationExecutor<Product>`.
- Colabora con: `ProductSearchPostgresIT`, `ProductSearchPostgresPlanIT`, entidad `Product`.
- Superficie publica: test fixture.
- Notas para futuras tareas: mantener `JpaSpecificationExecutor` porque es la superficie que consume `CompiledSearch.specification()`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/dao/ProductSeeder.java
- Tipo: seeder de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.dao`.
- Feature / area: datos de integracion de catalogo.
- Rol: carga un dataset pequeno y deterministico para tests funcionales Postgres.
- Responsabilidades: persiste categorias, suppliers y productos; agrega reviews; usa UUID estables; retorna `Catalog` con entidades clave para asserts.
- Colabora con: `TestEntityManager`, entidades bench, `ProductSearchPostgresIT`.
- Superficie publica: test fixture.
- Notas para futuras tareas: si cambian nombres/SKUs esperados por ITs, actualizar asserts de contenido.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/dao/ProductSpecifications.java
- Tipo: util de specifications de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.dao`.
- Feature / area: specs obligatorias/query textual.
- Rol: provee specs de aplicacion para query libre y published-only.
- Responsabilidades: `matchesTerm` busca term en name/sku/description lower-case; `publishedProducts` filtra `Status.PUBLISHED`.
- Colabora con: `ProductSearchFixtures`, tests Postgres.
- Superficie publica: test fixture.
- Notas para futuras tareas: estos specs representan predicates obligatorios de aplicacion combinados por `SearchCompiler`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Address.java
- Tipo: embeddable JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, supplier address.
- Rol: modela ciudad y pais embebidos en `Supplier`.
- Responsabilidades: fields `city`, `countryCode`; builder; equals/hashCode/toString.
- Colabora con: `Supplier`, paths `supplier.address.countryCode`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: usado para probar nested embedded path atravesando relation + embeddable.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Audit.java
- Tipo: embeddable JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, auditoria embebida.
- Rol: modela metadata de creacion en `Product`.
- Responsabilidades: fields `createdBy`, `createdAt`; builder; equals/hashCode/toString.
- Colabora con: `Product`, paths `audit.createdBy`, `audit.createdAt`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: cubre conversion de `Instant` y embeddables directos.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Category.java
- Tipo: entidad JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, categoria.
- Rol: categoria many-to-one de producto.
- Responsabilidades: table `product_categories`, fields `id`, `code`, `name`; builder; equality por code/name.
- Colabora con: `Product`, paths `category.code`, relation sorting/filtering.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: indexes y plan tests dependen de `category_id`/`category.code`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Dimensions.java
- Tipo: embeddable JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, dimensiones.
- Rol: datos numericos embebidos para filtros BigDecimal.
- Responsabilidades: fields `weightKg`, `widthCm`, `heightCm`; builder; equality/toString.
- Colabora con: `Product`, path `dimensions.weightKg`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: precision/scale simulan columnas numericas reales.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Product.java
- Tipo: entidad JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench principal.
- Rol: entidad central para probar filtros, sorts, query textual, joins, embeddables y to-many.
- Responsabilidades: table `catalog_products`; fields UUID, sku, name, description, stock, price, booleans, releaseDate, status; relations category/supplier/reviews; embeddables audit/dimensions; `addReview`; transient `getDisplayName`; builder.
- Colabora con: `Category`, `Supplier`, `Review`, `ProductRepository`, `ProductSearchFixtures`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: `getDisplayName` es Java property no persistente y se usa para validar rechazo del metamodelo JPA.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Review.java
- Tipo: entidad JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, one-to-many.
- Rol: representa reviews de producto para probar filtros por paths to-many.
- Responsabilidades: table `product_reviews`; fields rating/title/verified/createdAt; many-to-one a `Product`.
- Colabora con: `Product`, path `reviews.rating`, distinct/count protections.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: cambios en cardinalidad afectan tests de distinct y planes to-many.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Status.java
- Tipo: enum de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench.
- Rol: estados de producto.
- Responsabilidades: valores `DRAFT`, `PUBLISHED`, `DISCONTINUED`.
- Colabora con: `Product`, `ProductSpecifications.publishedProducts`.
- Superficie publica: test fixture.
- Notas para futuras tareas: asserts de published-only dependen de estos valores.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/bench/domain/Supplier.java
- Tipo: entidad JPA de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.bench.domain`.
- Feature / area: dominio bench, proveedor.
- Rol: supplier many-to-one de producto con embeddable address.
- Responsabilidades: table `suppliers`; fields UUID/name/preferred/address; builder; equality.
- Colabora con: `Product`, `Address`, paths `supplier.name`, `supplier.preferred`, `supplier.address.countryCode`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: plan tests crean indexes sobre supplier name/country.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/inheritance/dao/PersonRepository.java
- Tipo: repositorio Spring Data de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.inheritance.dao`.
- Feature / area: integracion de herencia JPA.
- Rol: ejecuta specifications contra la jerarquia `Person`.
- Responsabilidades: extiende `JpaRepository<Person, Long>` y `JpaSpecificationExecutor<Person>`.
- Colabora con: `PersonSubtypeSearchPostgresIT`.
- Superficie publica: test fixture.
- Notas para futuras tareas: necesario para validar `CriteriaBuilder.treat` end-to-end.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/inheritance/domain/LegalPerson.java
- Tipo: entidad JPA subtipo.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain`.
- Feature / area: herencia SINGLE_TABLE.
- Rol: subtipo legal con campo especifico.
- Responsabilidades: `@DiscriminatorValue("LEGAL")`, field `registrationNumber`.
- Colabora con: `Person`, `PersonSubtypeSearchPostgresIT`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: hoy no se filtra por `registrationNumber`, pero sostiene rama no-natural en OR/sort.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/inheritance/domain/NaturalPerson.java
- Tipo: entidad JPA subtipo.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain`.
- Feature / area: herencia SINGLE_TABLE.
- Rol: subtipo natural con `birthDate`.
- Responsabilidades: `@DiscriminatorValue("NATURAL")`, field `birthDate`.
- Colabora con: `Person`, `SearchField.subtype`, tests de `treat`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: es el caso principal de campo solo de subtipo.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/inheritance/domain/Person.java
- Tipo: entidad JPA abstracta.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain`.
- Feature / area: herencia JPA.
- Rol: root de jerarquia SINGLE_TABLE para probar filtros/sorts por subtype.
- Responsabilidades: table `search_people`, discriminator `person_type`, fields `id`, `name`.
- Colabora con: `NaturalPerson`, `LegalPerson`, `PersonRepository`.
- Superficie publica: test fixture JPA.
- Notas para futuras tareas: cambios de estrategia de herencia pueden alterar SQL generado y tests de `treat`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/JpaRsqlSearchTestAutoConfigurationImportSelector.java
- Tipo: import selector de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: bootstrapping de IT.
- Rol: importa auto-configuraciones package-private desde tests de aplicacion.
- Responsabilidades: devuelve nombres de `JpaRsqlSearchRsqlAutoConfiguration` y `JpaRsqlSearchAutoConfiguration`.
- Colabora con: `ProductSearchTestApplication`, `PersonSubtypeSearchTestApplication`.
- Superficie publica: test infrastructure.
- Notas para futuras tareas: actualizar si se renombra o divide auto-configuracion.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/PersonSubtypeSearchPostgresIT.java
- Tipo: integration test Postgres/JPA.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: subtypes JPA y `CriteriaBuilder.treat`.
- Rol: prueba campos declarados con `subtype` contra Postgres real.
- Responsabilidades: carga `NaturalPerson` y `LegalPerson`; valida/ejecuta filtro por `birthDate`; combina campo heredado y subtipo; prueba OR con rama subtype + root; ordena por campo subtype y verifica que el pageable compilado queda unsorted porque el order se aplica en Criteria.
- Colabora con: `PersonRepository`, `SearchCompiler`, `JpaSearchDefinitionValidator`, `PostgresTestEnvironment`.
- Superficie publica: integration test funcional.
- Notas para futuras tareas: si falla sorting por subtype, revisar `SearchSpecificationSorting`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/PersonSubtypeSearchTestApplication.java
- Tipo: Spring Boot test application.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: bootstrapping de IT herencia.
- Rol: configura repositories/entities de `Person` y auto-config de la libreria.
- Responsabilidades: `@SpringBootConfiguration`, `@EnableAutoConfiguration`, `@EnableJpaRepositories`, `@EntityScan`, `@Import`.
- Colabora con: `PersonSubtypeSearchPostgresIT`.
- Superficie publica: test infrastructure.
- Notas para futuras tareas: mantener basePackageClasses apuntando a repo/entity correctos para evitar scans amplios.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/ProductSearchFixtures.java
- Tipo: fixture de definicion de busqueda.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: contrato standard de busqueda de productos.
- Rol: define `SearchDefinition<Product>` completa usada por ITs funcionales y de plan.
- Responsabilidades: configura limits; declara aliases para id/publicId/sku/name/category/supplier/active/price/stock/release/audit/dimensions/reviews; query textual con `SizeDef`; paging page/size con Min/Max.
- Colabora con: `ProductSearchPostgresIT`, `ProductSearchPostgresPlanIT`, `ProductSpecifications`.
- Superficie publica: test fixture.
- Notas para futuras tareas: al agregar un campo de producto, este fixture es el contrato de busqueda end-to-end.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/ProductSearchPostgresIT.java
- Tipo: integration test Postgres/JPA funcional.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: ejecucion real de busquedas de productos.
- Rol: valida que `SearchCompiler` + Perplexhub + Spring Data JPA funcionen contra Postgres real.
- Responsabilidades: prueba search standard con filter/query/sort/spec obligatoria; many-to-one; embeddables/nested embeddables; one-to-many opt-in; published-only spec; conversiones reales UUID/BigDecimal/Integer/Boolean/LocalDate/Instant; custom operator con conversion service; igualdad literal de wildcards; rechazo de inputs inseguros antes del repositorio; validacion JPA de paths.
- Colabora con: `ProductRepository`, `ProductSeeder`, `ProductSearchFixtures`, `SearchRsqlEngineCustomizer`, `PostgresTestEnvironment`.
- Superficie publica: integration test principal.
- Notas para futuras tareas: si cambia semantica de igualdad/LIKE o conversion service, este IT da senales reales.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/ProductSearchPostgresPlanIT.java
- Tipo: integration/performance plan test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: planes SQL Postgres.
- Rol: captura queries generadas y valida que planes Postgres sean razonables/parseables sin spills.
- Responsabilidades: seed masivo; crea indexes/analyze; captura content/count; ejecuta EXPLAIN JSON; valida uso de indices, no sequential scan en casos selectivos, no external sort/temp blocks, estimaciones razonables, trigram indexes, costos relativos de OR/to-many/relation sort.
- Colabora con: `PostgresQueryRecorder`, `PostgresExplain`, `PostgresPlan`, `ProductPlanSeeder`, `ProductSearchFixtures`.
- Superficie publica: integration test de rendimiento/plan.
- Notas para futuras tareas: genera JSON en `target/a03-plans`; cambios de version Postgres/estadisticas pueden requerir ajustar umbrales.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/ProductSearchTestApplication.java
- Tipo: Spring Boot test application.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: bootstrapping de IT producto.
- Rol: configura repositories/entities de producto y auto-config de la libreria.
- Responsabilidades: habilita auto-config, JPA repositories, entity scan e import selector.
- Colabora con: `ProductSearchPostgresIT`, `ProductSearchPostgresPlanIT`.
- Superficie publica: test infrastructure.
- Notas para futuras tareas: evitar scans demasiado amplios para mantener tests aislados.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/SearchDefinitionRuntimeValidationPostgresIT.java
- Tipo: integration test de validacion runtime.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration`.
- Feature / area: timing de validacion de definiciones.
- Rol: asegura que definiciones invalidas no rompan startup, pero fallen al compilar busquedas.
- Responsabilidades: usa `ApplicationContextRunner` con Postgres; confirma que no hay startup validator; valida path JPA al compilar con y sin filtro RSQL; usa `displayName` transient como path invalido.
- Colabora con: `SearchCompiler`, `JpaSearchDefinitionValidator`, auto-configs, `Product`.
- Superficie publica: integration test de lifecycle.
- Notas para futuras tareas: importante para no convertir errores de definicion en fallos de arranque indeseados.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresExplain.java
- Tipo: util de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: EXPLAIN JSON Postgres.
- Rol: ejecuta `EXPLAIN`/`EXPLAIN ANALYZE` sobre queries capturadas y persiste planes.
- Responsabilidades: reproduce JDBC bindings capturados; desactiva recording durante EXPLAIN; parsea JSON; escribe archivos en `target/a03-plans`; retorna `PostgresPlan`.
- Colabora con: `PostgresQueryRecorder`, `DataSource`, Jackson, `ProductSearchPostgresPlanIT`.
- Superficie publica: test support.
- Notas para futuras tareas: si cambian parametros capturados por datasource-proxy, revisar replay de `ParameterSetOperation`.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresPlan.java
- Tipo: value/helper de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: analisis de planes Postgres.
- Rol: abstrae inspeccion de JSON EXPLAIN.
- Responsabilidades: colecciona nodos recursivos; expone node types, uso de indices, seq scan, external sort, total cost, shared hit blocks, execution time, temp blocks y ratio estimado/actual.
- Colabora con: `PostgresExplain`, tests de plan.
- Superficie publica: test support.
- Notas para futuras tareas: campos dependen del formato JSON de PostgreSQL; version upgrades pueden cambiar nombres o metricas.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresQueryRecorder.java
- Tipo: listener datasource-proxy de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: captura SQL.
- Rol: captura queries SELECT de aplicacion dentro de una sesion thread-local.
- Responsabilidades: `capture` devuelve resultado + queries; `withoutRecording` pausa captura; filtra selects de pg_catalog; copia parametros; clasifica `CONTENT` vs `COUNT`; provee `single(kind)`.
- Colabora con: `PostgresRecordingConfiguration`, `PostgresExplain`, datasource-proxy.
- Superficie publica: test support.
- Notas para futuras tareas: nested capture esta prohibido por diseño; si aparecen queries extra, ajustar filtro/clasificacion.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresRecordingConfiguration.java
- Tipo: `@TestConfiguration`.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: captura SQL en ITs.
- Rol: envuelve el bean `dataSource` con proxy recorder.
- Responsabilidades: registra `PostgresQueryRecorder`; usa `BeanPostProcessor` para reemplazar `dataSource` por `ProxyDataSourceBuilder` con listener.
- Colabora con: `ProductSearchPostgresPlanIT`.
- Superficie publica: test infrastructure.
- Notas para futuras tareas: depende del bean name `dataSource`; si Spring cambia naming, no grabara queries.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresSchemaInitializer.java
- Tipo: util de schema test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: setup Postgres para planes.
- Rol: crea extension e indices usados por plan tests.
- Responsabilidades: `create extension pg_trgm`; crea indices FK, search composites, supplier country/name, price/stock/release/created, trigram sobre lower(name/sku/description); ejecuta `analyze`.
- Colabora con: `ProductSearchPostgresPlanIT`, `JdbcTemplate`.
- Superficie publica: test support.
- Notas para futuras tareas: nombres de indices se assertan indirectamente en plan tests; cambiar nombres exige actualizar expectativas.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/PostgresTestEnvironment.java
- Tipo: singleton Testcontainers.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: ambiente Postgres de IT.
- Rol: levanta contenedor `postgres:18-alpine` compartido.
- Responsabilidades: configura DB/user/password; startup timeout 120s; registra dynamic properties o array de properties para context runner.
- Colabora con: todos los ITs Postgres.
- Superficie publica: test infrastructure.
- Notas para futuras tareas: requiere Docker disponible; version de Postgres afecta planes.

### src/test/java/io/github/ggomarighetti/jparsqlsearch/integration/postgres/ProductPlanSeeder.java
- Tipo: seeder masivo de test.
- Package: `io.github.ggomarighetti.jparsqlsearch.integration.postgres`.
- Feature / area: dataset para planes Postgres.
- Rol: carga dataset deterministico grande para evaluar planes/costos.
- Responsabilidades: trunca tablas; inserta 50 categorias, 200 suppliers, 20.000 productos y reviews; genera SKUs/UUIDs/precios/fechas/statuses deterministas; batch size 1000.
- Colabora con: `ProductSearchPostgresPlanIT`, `JdbcTemplate`.
- Superficie publica: test support.
- Notas para futuras tareas: distribuciones de category/supplier/status estan elegidas para selectividad de planes; cambios pueden alterar costos esperados.

### src/test/resources/corpus/rsql-weird-corpus.txt
- Tipo: recurso de corpus.
- Package: ruta logica `src/test/resources/corpus`.
- Feature / area: regresion RSQL.
- Rol: lista entradas raras para probar que el parser/guard no escapen con errores inesperados.
- Responsabilidades: incluye placeholders decodificados por `RsqlRegressionCorpusTest`, filtros vacios/blancos, quotes, parentesis, listas, operadores custom, selectors desconocidos, conversiones invalidas, unicode y escapes.
- Colabora con: `RsqlRegressionCorpusTest`.
- Superficie publica: test resource.
- Notas para futuras tareas: agregar aqui cualquier input que haya causado bug real o throwable inesperado.

### src/test/resources/junit-platform.properties
- Tipo: recurso de configuracion JUnit.
- Package: ruta logica `src/test/resources`.
- Feature / area: ejecucion de tests.
- Rol: deshabilita ejecucion paralela de JUnit Jupiter.
- Responsabilidades: define `junit.jupiter.execution.parallel.enabled=false`.
- Colabora con: suite completa de tests, especialmente Testcontainers/Postgres y recursos compartidos.
- Superficie publica: test resource.
- Notas para futuras tareas: habilitar paralelo puede romper tests con contenedor/estado compartido si no se audita primero.

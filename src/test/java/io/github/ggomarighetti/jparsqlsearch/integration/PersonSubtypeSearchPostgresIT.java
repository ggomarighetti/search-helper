package io.github.ggomarighetti.jparsqlsearch.integration;

import io.github.ggomarighetti.jparsqlsearch.compile.CompiledSearch;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.integration.inheritance.dao.PersonRepository;
import io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain.LegalPerson;
import io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain.NaturalPerson;
import io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain.Person;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresTestEnvironment;
import io.github.ggomarighetti.jparsqlsearch.jpa.JpaSearchDefinitionValidator;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        showSql = false,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersonSubtypeSearchTestApplication.class)
class PersonSubtypeSearchPostgresIT {
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        PostgresTestEnvironment.register(registry);
    }

    @Autowired
    private PersonRepository people;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SearchCompiler compiler;

    @BeforeEach
    void seedPeople() {
        entityManager.persist(new NaturalPerson("Ana", LocalDate.of(1985, Month.JUNE, 15)));
        entityManager.persist(new NaturalPerson("Bruno", LocalDate.of(2001, Month.FEBRUARY, 3)));
        entityManager.persist(new LegalPerson("Acme", "REG-001"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void validatesAndExecutesSubtypeOnlyFields() {
        SearchDefinition<Person> definition = personSearch();

        jpaDefinitionValidator().validate(definition);
        CompiledSearch<Person> compiled = compiler.compile(
                "birthDate=ge=1990-01-01",
                null,
                PageRequest.of(0, 10),
                definition);

        assertThat(people.findAll(compiled.specification(), compiled.pageable()))
                .extracting(Person::getName)
                .containsExactly("Bruno");
    }

    @Test
    void combinesInheritedAndSubtypeFields() {
        CompiledSearch<Person> compiled = compiler.compile(
                "name==Ana;birthDate=lt=1990-01-01",
                null,
                PageRequest.of(0, 10),
                personSearch());

        assertThat(people.findAll(compiled.specification(), compiled.pageable()))
                .extracting(Person::getName)
                .containsExactly("Ana");
    }

    @Test
    void treatsOnlyTheSubtypeBranchOfAnOrExpression() {
        CompiledSearch<Person> compiled = compiler.compile(
                "birthDate==1985-06-15,name==Acme",
                null,
                PageRequest.of(0, 10),
                personSearch());

        assertThat(people.findAll(compiled.specification(), compiled.pageable()))
                .extracting(Person::getName)
                .containsExactlyInAnyOrder("Ana", "Acme");
    }

    @Test
    void sortsBySubtypeOnlyFieldsThroughTheCompiler() {
        SearchDefinition<Person> definition = SearchDefinition.builder()
                .entity(Person.class)
                .limits(limits -> limits.sorting(sorting -> sorting.allowNullHandling(true)))
                .fields(fields -> fields.add("birthDate", LocalDate.class)
                        .subtype(NaturalPerson.class)
                        .sortable(sort -> sort.allowNullHandling(Sort.NullHandling.NULLS_LAST)))
                .paging()
                .build();
        CompiledSearch<Person> compiled = compiler.compile(
                null,
                null,
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("birthDate").nullsLast())),
                definition);
        Page<Person> page = people.findAll(compiled.specification(), compiled.pageable());

        assertThat(compiled.pageable().getSort().isUnsorted()).isTrue();
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page)
                .extracting(Person::getName)
                .containsExactly("Bruno", "Ana");
    }

    private static SearchDefinition<Person> personSearch() {
        return SearchDefinition.builder()
                .entity(Person.class)
                .fields(fields -> {
                    fields.add("name", String.class).filterable();
                    fields.add("birthDate", LocalDate.class)
                            .subtype(NaturalPerson.class)
                            .filterable();
                })
                .paging()
                .build();
    }

    private JpaSearchDefinitionValidator jpaDefinitionValidator() {
        return new JpaSearchDefinitionValidator(
                entityManager.getEntityManager().getEntityManagerFactory());
    }
}

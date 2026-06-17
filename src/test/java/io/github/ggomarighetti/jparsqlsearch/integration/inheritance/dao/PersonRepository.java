package io.github.ggomarighetti.jparsqlsearch.integration.inheritance.dao;

import io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonRepository
        extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {
}

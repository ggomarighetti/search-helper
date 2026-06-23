package io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("NATURAL")
public class NaturalPerson extends Person {
    @Column(name = "birth_date")
    private LocalDate birthDate;

    protected NaturalPerson() {
    }

    public NaturalPerson(String name, LocalDate birthDate) {
        super(name);
        this.birthDate = birthDate;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }
}

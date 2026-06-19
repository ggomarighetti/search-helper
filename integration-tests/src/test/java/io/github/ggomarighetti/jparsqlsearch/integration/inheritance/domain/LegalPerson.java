package io.github.ggomarighetti.jparsqlsearch.integration.inheritance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("LEGAL")
public class LegalPerson extends Person {
    @Column(name = "registration_number")
    private String registrationNumber;

    protected LegalPerson() {
    }

    public LegalPerson(String name, String registrationNumber) {
        super(name);
        this.registrationNumber = registrationNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }
}

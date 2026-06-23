package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class Address {
    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String countryCode;

    protected Address() {
    }

    Address(String city, String countryCode) {
        this.city = city;
        this.countryCode = countryCode;
    }

    public static AddressBuilder builder() {
        return new AddressBuilder();
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Address address)) {
            return false;
        }
        return Objects.equals(city, address.city)
                && Objects.equals(countryCode, address.countryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, countryCode);
    }

    @Override
    public String toString() {
        return "Address{city='%s', countryCode='%s'}".formatted(city, countryCode);
    }

    public static final class AddressBuilder {
        private String city;
        private String countryCode;

        private AddressBuilder() {
        }

        public AddressBuilder city(String city) {
            this.city = city;
            return this;
        }

        public AddressBuilder countryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Address build() {
            return new Address(city, countryCode);
        }
    }
}

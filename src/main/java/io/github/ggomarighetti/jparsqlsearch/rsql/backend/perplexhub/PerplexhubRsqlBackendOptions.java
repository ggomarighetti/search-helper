package io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub;

import java.util.function.Consumer;
import java.util.Objects;

/** Immutable options for the built-in Perplexhub JPA backend. */
public final class PerplexhubRsqlBackendOptions {
    private final boolean strictEquality;
    private final Character likeEscapeCharacter;

    private PerplexhubRsqlBackendOptions(boolean strictEquality, Character likeEscapeCharacter) {
        this.strictEquality = strictEquality;
        this.likeEscapeCharacter = likeEscapeCharacter;
    }

    /**
     * Returns default backend options.
     *
     * @return strict, literal-equality default options
     */
    public static PerplexhubRsqlBackendOptions defaults() {
        return new PerplexhubRsqlBackendOptions(true, null);
    }

    /**
     * Creates an options builder.
     *
     * @return new options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reports the configured equality semantics.
     *
     * @return whether string equality remains literal rather than wildcard-based
     */
    public boolean strictEquality() {
        return strictEquality;
    }

    /**
     * Returns the optional LIKE escape character.
     *
     * @return configured LIKE escape character, or {@code null}
     */
    public Character likeEscapeCharacter() {
        return likeEscapeCharacter;
    }

    /** Builder for immutable Perplexhub backend options. */
    public static final class Builder {
        private boolean strictEquality = true;
        private Character likeEscapeCharacter;

        private Builder() {
        }

        /**
         * Configures literal string equality.
         *
         * @param strictEquality {@code true} for literal equality
         * @return this builder
         */
        public Builder strictEquality(boolean strictEquality) {
            this.strictEquality = strictEquality;
            return this;
        }

        /**
         * Configures the optional LIKE escape character.
         *
         * @param likeEscapeCharacter escape character, or {@code null}
         * @return this builder
         */
        public Builder likeEscapeCharacter(Character likeEscapeCharacter) {
            this.likeEscapeCharacter = likeEscapeCharacter;
            return this;
        }

        /**
         * Applies a reusable builder customizer.
         *
         * @param customizer options customizer
         * @return this builder
         */
        public Builder customize(Consumer<Builder> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null").accept(this);
            return this;
        }

        /**
         * Builds immutable options.
         *
         * @return immutable backend options
         */
        public PerplexhubRsqlBackendOptions build() {
            return new PerplexhubRsqlBackendOptions(strictEquality, likeEscapeCharacter);
        }
    }
}

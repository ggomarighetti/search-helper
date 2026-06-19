package io.github.ggomarighetti.jparsqlsearch.definition.validation;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;

/** Extension point for runtime validation of completed search definitions. */
@FunctionalInterface
public interface SearchDefinitionValidator {
    /**
     * Validates a definition or throws a configuration exception.
     *
     * @param definition immutable definition to validate
     */
    void validate(SearchDefinition<?> definition);
}

package io.github.ggomarighetti.jparsqlsearch.path;

import java.util.Objects;

final class PathSegments {
    private PathSegments() {
    }

    static String[] split(String path) {
        return Objects.requireNonNull(path, "path must not be null").split("\\.", -1);
    }
}

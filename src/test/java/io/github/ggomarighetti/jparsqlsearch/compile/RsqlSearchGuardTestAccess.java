package io.github.ggomarighetti.jparsqlsearch.compile;

public final class RsqlSearchGuardTestAccess {
    private RsqlSearchGuardTestAccess() {
    }

    public static boolean isRsqlSearchGuard(Object bean) {
        return bean instanceof RsqlSearchGuard;
    }
}

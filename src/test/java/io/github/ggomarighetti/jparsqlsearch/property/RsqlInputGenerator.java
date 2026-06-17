package io.github.ggomarighetti.jparsqlsearch.property;

import java.util.Random;

public final class RsqlInputGenerator {
    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyz"
                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "0123456789"
                    + "._-:/@"
                    + "();,=!~<>"
                    + "'\"\\\\"
                    + " \t\n\r"
                    + "\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1"
                    + "\u2603";

    private static final String[] SELECTORS = {
            "sku",
            "name",
            "amount",
            "stock",
            "status",
            "releaseDate",
            "reviewRating",
            "passwordHash",
            "SKU",
            "price",
            "supplier.name",
            "",
            " "
    };

    private static final String[] OPERATORS = {
            "==",
            "!=",
            "=in=",
            "=out=",
            ">",
            ">=",
            "<",
            "<=",
            "=like=",
            "=ilike=",
            "=custom="
    };

    private static final String[] VALUES = {
            "SKU-001",
            "Name",
            "10.50",
            "100",
            "-1",
            "PUBLISHED",
            "DELETED",
            "2026-06-12",
            "not-a-date",
            "'quoted value'",
            "\"double quoted\"",
            "",
            "(",
            ")",
            "\\\\",
            "\u2603"
    };

    private final Random random;

    public RsqlInputGenerator(long seed) {
        this.random = new Random(seed);
    }

    public String nextInput() {
        int mode = random.nextInt(10);
        if (mode < 3) {
            return randomCharacters(random.nextInt(350));
        }
        if (mode < 7) {
            return comparison();
        }
        if (mode < 9) {
            return compound();
        }
        return grouped();
    }

    public String comparisonWithUnknownSelector() {
        String[] selectors = {"passwordHash", "SKU", "price", "supplier.name", "category.name", "sku.id"};
        return selectors[random.nextInt(selectors.length)] + "==" + value();
    }

    public String comparisonWithDisallowedOperator() {
        String[] selectors = {"sku", "name", "amount", "stock"};
        String[] operators = {"!=", "=out=", "=ilike=", "=custom="};
        return selectors[random.nextInt(selectors.length)] + operators[random.nextInt(operators.length)] + value();
    }

    private String compound() {
        int comparisons = 2 + random.nextInt(4);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < comparisons; i++) {
            if (i > 0) {
                builder.append(random.nextBoolean() ? ';' : ',');
            }
            builder.append(comparison());
        }
        return builder.toString();
    }

    private String grouped() {
        String input = comparison();
        int groups = random.nextInt(5);
        StringBuilder builder = new StringBuilder(input.length() + groups * 2);
        for (int i = 0; i < groups; i++) {
            builder.append('(');
        }
        builder.append(input);
        for (int i = 0; i < groups; i++) {
            builder.append(')');
        }
        return builder.toString();
    }

    private String comparison() {
        String selector = SELECTORS[random.nextInt(SELECTORS.length)];
        String operator = OPERATORS[random.nextInt(OPERATORS.length)];
        if ("=in=".equals(operator) || "=out=".equals(operator)) {
            return selector + operator + list();
        }
        return selector + operator + value();
    }

    private String list() {
        int arguments = random.nextInt(7);
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < arguments; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(value());
        }
        return builder.append(')').toString();
    }

    private String value() {
        if (random.nextInt(5) == 0) {
            return randomCharacters(random.nextInt(40));
        }
        return VALUES[random.nextInt(VALUES.length)];
    }

    private String randomCharacters(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
}

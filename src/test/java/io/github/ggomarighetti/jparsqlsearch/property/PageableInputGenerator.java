package io.github.ggomarighetti.jparsqlsearch.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableInputGenerator {
    private static final String[] PROPERTIES = {
            "sku",
            "SKU",
            "name",
            "Name",
            "amount",
            "legacyAmount",
            "releaseDate",
            "supplierName",
            "passwordHash",
            "price",
            "supplier.name"
    };

    private final Random random;

    public PageableInputGenerator(long seed) {
        this.random = new Random(seed);
    }

    public Pageable nextPageable() {
        Sort sort = nextSort();
        if (random.nextInt(20) == 0) {
            return Pageable.unpaged(sort);
        }
        int page = random.nextInt(151);
        int size = 1 + random.nextInt(150);
        return PageRequest.of(page, size, sort);
    }

    public Sort nextSort() {
        int orders = random.nextInt(6);
        if (orders == 0) {
            return Sort.unsorted();
        }
        List<Sort.Order> result = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            Sort.Order order = new Sort.Order(
                    random.nextBoolean() ? Sort.Direction.ASC : Sort.Direction.DESC,
                    PROPERTIES[random.nextInt(PROPERTIES.length)],
                    nullHandling());
            if (random.nextBoolean()) {
                order = order.ignoreCase();
            }
            result.add(order);
        }
        return Sort.by(result);
    }

    private Sort.NullHandling nullHandling() {
        Sort.NullHandling[] values = Sort.NullHandling.values();
        return values[random.nextInt(values.length)];
    }
}

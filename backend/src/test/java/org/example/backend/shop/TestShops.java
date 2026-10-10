package org.example.backend.shop;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * A reference to shop #1, the shop the V6 migration creates. Tests use it as
 * the owner of the fixtures they build, so they don't each have to load it.
 * Only its id is set, which is all a foreign key needs.
 */
public final class TestShops {

    public static final Shop ONE = reference(1L);

    private TestShops() {
    }

    public static Shop reference(Long id) {
        Shop shop = new Shop("Test shop " + id);
        ReflectionTestUtils.setField(shop, "id", id);
        return shop;
    }
}

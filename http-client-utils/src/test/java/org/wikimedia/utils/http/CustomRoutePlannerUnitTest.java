package org.wikimedia.utils.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomRoutePlannerUnitTest {
    @Test
    void test() {
        assertThat(new CustomRoutePlanner()).isInstanceOf(CustomRoutePlanner.class);
    }
}

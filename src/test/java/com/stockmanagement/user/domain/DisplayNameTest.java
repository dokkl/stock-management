package com.stockmanagement.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    void blankBecomesDefault() {
        assertThat(DisplayName.of(null).value()).isEqualTo("카카오 사용자");
        assertThat(DisplayName.of("   ").value()).isEqualTo("카카오 사용자");
    }

    @Test
    void trimsAndCutsToFortyCharacters() {
        assertThat(DisplayName.of("  홍길동 ").value()).isEqualTo("홍길동");
        assertThat(DisplayName.of("가".repeat(50)).value()).hasSize(40);
        assertThat(DisplayName.of("😀".repeat(45))
                        .value()
                        .codePointCount(
                                0, DisplayName.of("😀".repeat(45)).value().length()))
                .isEqualTo(40);
    }

    @Test
    void constructorValidates() {
        assertThatThrownBy(() -> new DisplayName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DisplayName("가".repeat(41))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DisplayName("홍길동")).hasToString("홍길동");
    }
}

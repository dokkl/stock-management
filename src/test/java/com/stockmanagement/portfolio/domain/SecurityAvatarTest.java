package com.stockmanagement.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SecurityAvatarTest {

    @Test
    void initialIsFirstLetterOfName() {
        assertThat(SecurityAvatar.of("005930", "삼성전자").initial()).isEqualTo("삼");
        assertThat(SecurityAvatar.of("AAPL", "apple").initial()).isEqualTo("A");
    }

    @Test
    void initialSkipsLeadingSpacesAndKeepsSupplementaryCharacters() {
        assertThat(SecurityAvatar.of("X", "  nvidia").initial()).isEqualTo("N");
        assertThat(SecurityAvatar.of("X", "𝔸bc").initial()).isEqualTo("𝔸");
    }

    @Test
    void blankOrMissingNameFallsBackToSymbol() {
        assertThat(SecurityAvatar.of("tsla", "  ").initial()).isEqualTo("T");
        assertThat(SecurityAvatar.of("MSFT", null).initial()).isEqualTo("M");
    }

    @Test
    void toneIsStablePerSymbolAndWithinPalette() {
        assertThat(SecurityAvatar.of("005930", "삼성전자").tone())
                .isEqualTo(SecurityAvatar.of("005930", "다른 이름").tone());
        assertThat(IntStream.range(0, 200)
                        .map(i -> SecurityAvatar.of("S" + i, "x").tone())
                        .distinct()
                        .sorted()
                        .toArray())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }
}

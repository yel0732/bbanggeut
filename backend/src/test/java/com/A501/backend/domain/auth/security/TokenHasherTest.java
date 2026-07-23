package com.A501.backend.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void generateRawToken_producesUniqueValuesEachCall() {
        String first = TokenHasher.generateRawToken();
        String second = TokenHasher.generateRawToken();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hash_isDeterministicForSameInput() {
        String rawToken = TokenHasher.generateRawToken();

        assertThat(TokenHasher.hash(rawToken)).isEqualTo(TokenHasher.hash(rawToken));
    }

    @Test
    void hash_producesSixtyFourCharacterHexString() {
        String hash = TokenHasher.hash("any-raw-token-value");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void hash_differsForDifferentInput() {
        String hashA = TokenHasher.hash("token-a");
        String hashB = TokenHasher.hash("token-b");

        assertThat(hashA).isNotEqualTo(hashB);
    }
}

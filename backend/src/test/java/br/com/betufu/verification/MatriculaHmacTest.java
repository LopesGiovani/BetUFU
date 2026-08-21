package br.com.betufu.verification;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.betufu.verification.internal.MatriculaHmac;

class MatriculaHmacTest {

    private static final String TEST_KEY_V1_BASE64 =
            Base64.getEncoder().encodeToString("test-only-hmac-key-0000000000000001".getBytes(StandardCharsets.UTF_8));
    private static final String TEST_KEY_V2_BASE64 =
            Base64.getEncoder().encodeToString("test-only-hmac-key-0000000000000002".getBytes(StandardCharsets.UTF_8));

    private final MatriculaHmac hmacService = new MatriculaHmac(
            "v1",
            Map.of(
                    "v1", Base64.getDecoder().decode(TEST_KEY_V1_BASE64),
                    "v2", Base64.getDecoder().decode(TEST_KEY_V2_BASE64)));

    @Test
    @DisplayName("Produz digest de exatamente 32 octetos (256 bits) para entrada normalizada")
    void producesDigestOfExactly32Bytes() {
        byte[] digest = hmacService.calculate("12345ABC", "v1");

        assertThat(digest).isNotNull();
        assertThat(digest).hasSize(32);
    }

    @Test
    @DisplayName("Cálculo é determinístico byte a byte sob a mesma chave e versão")
    void calculationIsDeterministicUnderSameKeyAndVersion() {
        byte[] digest1 = hmacService.calculate("12345ABC", "v1");
        byte[] digest2 = hmacService.calculate("12345ABC", "v1");

        assertThat(digest1).isEqualTo(digest2);
        assertThat(MessageDigest.isEqual(digest1, digest2)).isTrue();
    }

    @Test
    @DisplayName("Usa a versão corrente padrão quando a versão não é especificada na chamada")
    void usesCurrentVersionByDefault() {
        byte[] defaultDigest = hmacService.calculateCurrent("12345ABC");
        byte[] v1Digest = hmacService.calculate("12345ABC", "v1");

        assertThat(defaultDigest).isEqualTo(v1Digest);
    }

    @Test
    @DisplayName("A mesma entrada produz digests diferentes sob versões de chave distintas (D-35)")
    void producesDifferentDigestsUnderDifferentKeyVersionsForSameInput() {
        byte[] digestV1 = hmacService.calculate("12345ABC", "v1");
        byte[] digestV2 = hmacService.calculate("12345ABC", "v2");

        assertThat(digestV1).isNotEqualTo(digestV2);
        assertThat(MessageDigest.isEqual(digestV1, digestV2)).isFalse();
    }

    @Test
    @DisplayName("Entradas diferentes produzem digests distintos sob a mesma versão de chave")
    void producesDifferentDigestsForDifferentInputsUnderSameVersion() {
        byte[] digestA = hmacService.calculate("12345ABC", "v1");
        byte[] digestB = hmacService.calculate("12345XYZ", "v1");

        assertThat(digestA).isNotEqualTo(digestB);
    }

    @Test
    @DisplayName("Comparação em tempo constante identifica igualdades e diferenças sem early return")
    void constantTimeComparisonIdentifiesMatchesAndMismatches() {
        byte[] digestA = hmacService.calculate("12345ABC", "v1");
        byte[] digestB = hmacService.calculate("12345ABC", "v1");
        byte[] digestC = hmacService.calculate("12345XYZ", "v1");
        byte[] shortArray = new byte[16];

        assertThat(MatriculaHmac.isEqual(digestA, digestB)).isTrue();
        assertThat(MatriculaHmac.isEqual(digestA, digestC)).isFalse();
        assertThat(MatriculaHmac.isEqual(digestA, shortArray)).isFalse();
        assertThat(MatriculaHmac.isEqual(digestA, null)).isFalse();
        assertThat(MatriculaHmac.isEqual(null, digestB)).isFalse();
        assertThat(MatriculaHmac.isEqual(null, null)).isFalse();
    }

    @Test
    @DisplayName("Cálculo concorrente não corrompe estado interno nem compartilha instâncias mutáveis")
    void concurrentCalculationsAreThreadSafe() throws Exception {
        int threadCount = 10;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        byte[] expectedV1 = hmacService.calculate("CONCURRENT123", "v1");
        byte[] expectedV2 = hmacService.calculate("CONCURRENT123", "v2");

        try {
            for (int i = 0; i < threadCount; i++) {
                final boolean useV1 = (i % 2 == 0);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String version = useV1 ? "v1" : "v2";
                        byte[] result = hmacService.calculate("CONCURRENT123", version);
                        byte[] expected = useV1 ? expectedV1 : expectedV2;
                        assertThat(result).isEqualTo(expected);
                    }
                    return null;
                }));
            }

            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("Recusa versão desconhecida com exceção")
    void rejectsUnknownKeyVersion() {
        assertThatThrownBy(() -> hmacService.calculate("12345ABC", "v999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v999");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "\t\n",
        "1",
        "A1",
        "1234",
        "ABCD",
        "12345abc",
        "12345-abc",
        "abcde",
        "12345-ABC",
        "123 45ABC",
        "12345.ABC",
        "12345/ABC",
        " 12345ABC ",
        "123456789012345678901",
        "ABCDEFGHIJKLMNOPQRSTU",
        "12345_ABC",
        "12345@ABC",
        "12345#ABC",
        "12345!ABC",
        "12345$ABC"
    })
    @DisplayName("calculate recusa entradas que não estejam normalizadas e válidas sob ^[A-Z0-9]{5,20}$")
    void calculateRejectsInputsNotAlreadyNormalizedAndValid(String invalidInput) {
        assertThatThrownBy(() -> hmacService.calculate(invalidInput, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "\t\n",
        "1",
        "A1",
        "1234",
        "ABCD",
        "12345abc",
        "12345-abc",
        "abcde",
        "12345-ABC",
        "123 45ABC",
        "12345.ABC",
        "12345/ABC",
        " 12345ABC ",
        "123456789012345678901",
        "ABCDEFGHIJKLMNOPQRSTU",
        "12345_ABC",
        "12345@ABC",
        "12345#ABC",
        "12345!ABC",
        "12345$ABC"
    })
    @DisplayName("calculateCurrent recusa entradas que não estejam normalizadas e válidas sob ^[A-Z0-9]{5,20}$")
    void calculateCurrentRejectsInputsNotAlreadyNormalizedAndValid(String invalidInput) {
        assertThatThrownBy(() -> hmacService.calculateCurrent(invalidInput))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

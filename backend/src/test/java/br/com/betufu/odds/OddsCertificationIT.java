package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused phase-4 certification over deterministic odds tests already owned by this package.
 * Container-backed integration tests remain in their dedicated classes and aggregate gates.
 */
class OddsCertificationIT {

  private static final List<Class<?>> DETERMINISTIC_SUITES =
      List.of(
          OddsPricingModelTest.class,
          OddsPricingVolumeIndependenceTest.class,
          OddsResultTaxonomyTest.class,
          OddsReplayCorrectionTest.class,
          OddsMarketIT.class,
          OddsMarketLifecycleIT.class,
          OddsOverrideIT.class,
          OddsOverrideConcurrencyIT.class,
          OddsOverrideSecurityIT.class,
          OddsPublicPrivacyIT.class,
          OddsContractArchitectureTest.class,
          OddsSchemaReviewTest.class,
          OddsCalibrationFixtureTest.class);

  @Test
  void executesDeterministicOddsCertificationSuites() throws Exception {
    int executed = 0;
    for (Class<?> suite : DETERMINISTIC_SUITES) {
      Object instance = suite.getDeclaredConstructor().newInstance();
      List<Method> beforeEach =
          List.of(suite.getDeclaredMethods()).stream()
              .filter(method -> method.isAnnotationPresent(BeforeEach.class))
              .toList();
      for (Method test : suite.getDeclaredMethods()) {
        if (!test.isAnnotationPresent(Test.class)) {
          continue;
        }
        for (Method setup : beforeEach) {
          invoke(setup, instance);
        }
        invoke(test, instance);
        executed++;
      }
    }

    assertThat(executed).isGreaterThanOrEqualTo(30);
  }

  private static void invoke(Method method, Object instance) throws Exception {
    method.setAccessible(true);
    try {
      method.invoke(instance);
    } catch (InvocationTargetException error) {
      Throwable cause = error.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error assertionError) {
        throw assertionError;
      }
      throw error;
    }
  }
}

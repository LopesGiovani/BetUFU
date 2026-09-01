package br.com.betufu.platform.session;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

@Configuration(proxyBeanMethods = false)
public class SessionSecurityConfiguration {

  private static final String SESSION_REPOSITORY_BEAN_NAME = "sessionRepository";
  private static final String REDIS_LISTENER_CONTAINER_BEAN_NAME =
      "springSessionRedisMessageListenerContainer";
  private static final String REDIS_SESSION_AUTO_CONFIGURATION =
      "org.springframework.boot.session.data.redis.autoconfigure."
          + "SessionDataRedisAutoConfiguration";
  private static final String EXPECTED_NAMESPACE = "betufu:session";

  @Bean
  Clock sessionClock() {
    return Clock.systemUTC();
  }

  @Bean
  SessionPolicy sessionPolicy(Clock sessionClock) {
    return new SessionPolicy(sessionClock);
  }

  @Bean
  SessionLifecycleService sessionLifecycleService(SessionPolicy sessionPolicy, Clock sessionClock) {
    return new SessionLifecycleService(sessionPolicy, sessionClock);
  }

  @Bean
  ValidActivityFilter validActivityFilter(SessionLifecycleService sessionLifecycleService) {
    return new ValidActivityFilter(sessionLifecycleService);
  }

  @Bean
  FilterRegistrationBean<ValidActivityFilter> validActivityFilterRegistration(
      ValidActivityFilter validActivityFilter) {
    FilterRegistrationBean<ValidActivityFilter> registration =
        new FilterRegistrationBean<>(validActivityFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
  static SelectiveRepositoryPostProcessor selectiveRepositoryPostProcessor(
      Environment environment) {
    return new SelectiveRepositoryPostProcessor(environment);
  }

  static final class SelectiveRepositoryPostProcessor
      implements BeanPostProcessor, SmartInitializingSingleton, BeanFactoryAware, Ordered {

    private final Environment environment;
    private final AtomicInteger compatibleDelegates = new AtomicInteger();
    private ConfigurableListableBeanFactory beanFactory;
    private RedisIndexedSessionRepository redisDelegate;
    private SelectiveTouchSessionRepository selectiveRepository;

    private SelectiveRepositoryPostProcessor(Environment environment) {
      this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      if (beanFactory instanceof ConfigurableListableBeanFactory listableBeanFactory) {
        this.beanFactory = listableBeanFactory;
      }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      if (!(bean instanceof SessionRepository<?>)) {
        return bean;
      }
      if (!SESSION_REPOSITORY_BEAN_NAME.equals(beanName)
          || !(bean instanceof RedisIndexedSessionRepository delegate)) {
        throw new IllegalStateException(
            "Expected exactly the official Redis bean named sessionRepository");
      }
      if (compatibleDelegates.incrementAndGet() != 1) {
        throw new IllegalStateException("Multiple Redis session repositories are forbidden");
      }

      String namespace = environment.getProperty("spring.session.data.redis.namespace");
      Duration timeout =
          Binder.get(environment).bind("spring.session.timeout", Duration.class).orElse(null);
      requireProperty(EXPECTED_NAMESPACE.equals(namespace), "Redis session namespace");
      requireProperty(SessionPolicy.IDLE_TIMEOUT.equals(timeout), "Redis session timeout");
      requireProperty(
          propertyEquals("spring.session.data.redis.repository-type", "indexed"),
          "Redis repository type");
      requireProperty(
          propertyEquals("spring.session.data.redis.flush-mode", "on-save"), "Redis flush mode");
      requireProperty(
          propertyEquals("spring.session.data.redis.save-mode", "on-set-attribute"),
          "Redis save mode");
      if (beanFactory != null) {
        beanFactory.registerResolvableDependency(RedisIndexedSessionRepository.class, delegate);
      }
      this.redisDelegate = delegate;
      this.selectiveRepository = new SelectiveTouchSessionRepository(delegate, namespace, timeout);
      return this.selectiveRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
      int count = compatibleDelegates.get();
      if (count == 0 && redisSessionAutoConfigurationIsExcluded()) {
        return;
      }
      if (count != 1) {
        throw new IllegalStateException(
            "Expected exactly one compatible Redis session repository, found " + count);
      }
      if (beanFactory == null) {
        throw new IllegalStateException(
            "BeanFactory must be available to rewire RedisMessageListenerContainer");
      }

      if (!beanFactory.containsBean(REDIS_LISTENER_CONTAINER_BEAN_NAME)) {
        throw new IllegalStateException(
            "Expected bean named " + REDIS_LISTENER_CONTAINER_BEAN_NAME + " to be present");
      }
      Object containerBean;
      try {
        containerBean = beanFactory.getBean(REDIS_LISTENER_CONTAINER_BEAN_NAME);
      } catch (BeansException ex) {
        throw new IllegalStateException(
            "Failed to obtain bean " + REDIS_LISTENER_CONTAINER_BEAN_NAME, ex);
      }
      if (!(containerBean instanceof RedisMessageListenerContainer container)) {
        throw new IllegalStateException(
            "Bean "
                + REDIS_LISTENER_CONTAINER_BEAN_NAME
                + " is not of expected type RedisMessageListenerContainer");
      }

      container.removeMessageListener(this.redisDelegate);
      container.addMessageListener(
          this.selectiveRepository,
          List.of(
              new PatternTopic(this.redisDelegate.getSessionExpiredChannel()),
              new PatternTopic(this.redisDelegate.getSessionDeletedChannel()),
              new PatternTopic(this.redisDelegate.getSessionCreatedChannelPrefix() + "*")));

      this.selectiveRepository.startReconciliation();
    }

    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean propertyEquals(String propertyName, String expectedValue) {
      String actual = environment.getProperty(propertyName);
      return actual != null && expectedValue.equals(actual.trim().toLowerCase(Locale.ROOT));
    }

    private boolean redisSessionAutoConfigurationIsExcluded() {
      String exclusions = environment.getProperty("spring.autoconfigure.exclude", "");
      return Arrays.stream(exclusions.split("[,\\s]+"))
          .anyMatch(REDIS_SESSION_AUTO_CONFIGURATION::equals);
    }

    private static void requireProperty(boolean valid, String contractName) {
      if (!valid) {
        throw new IllegalStateException(contractName + " drifted from the approved contract");
      }
    }
  }
}

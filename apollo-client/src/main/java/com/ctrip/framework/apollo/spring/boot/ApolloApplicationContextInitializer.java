package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.google.common.base.Splitter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ResourceUtils;

/**
 * Inject the Apollo config in Spring Boot bootstrap phase
 *
 * <p>Configuration example:</p>
 * <pre class="code">
 *   # will inject 'application' namespace in bootstrap phase
 *   apollo.bootstrap.enabled = true
 * </pre>
 *
 * or
 *
 * <pre class="code">
 *   apollo.bootstrap.enabled = true
 *   # will inject 'application' and 'FX.apollo' namespaces in bootstrap phase
 *   apollo.bootstrap.namespaces = application,FX.apollo
 * </pre>
 */
public class ApolloApplicationContextInitializer implements
    ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static final Logger logger = LoggerFactory.getLogger(ApolloApplicationContextInitializer.class);
  private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

  private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector
      .getInstance(ConfigPropertySourceFactory.class);

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment environment = context.getEnvironment();
    String enabled = environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, "false");
    if (!Boolean.valueOf(enabled)) {
      logger.debug("Apollo bootstrap config is not enabled for context {}, see property: ${{}}", context, PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED);
      return;
    }
    logger.debug("Apollo bootstrap config is enabled for context {}", context);

    if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
      //already initialized
      return;
    }

    String namespaces = environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_NAMESPACES, ConfigConsts.NAMESPACE_APPLICATION);
    logger.debug("Apollo bootstrap namespaces: {}", namespaces);
    List<String> namespaceList = NAMESPACE_SPLITTER.splitToList(namespaces);

    CompositePropertySource composite = new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
    for (String namespace : namespaceList) {
      Config config = ConfigService.getConfig(namespace);

      composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
    }

    environment.getPropertySources().addFirst(composite);
    reinitializeLoggingSystem(environment);
  }

    /**
     * Learning from {@see org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration} which is in spring-cloud.
     * @param environment
     */
  private void reinitializeLoggingSystem(ConfigurableEnvironment environment) {
    String logConfig = environment.resolvePlaceholders("${logging.config:}");
    LogFile logFile = LogFile.get(environment);
    LoggingSystem system = LoggingSystem.get(LoggingSystem.class.getClassLoader());
    try {
      ResourceUtils.getURL(logConfig).openStream().close();
      // Three step initialization that accounts for the clean up of the logging
      // context before initialization. Spring Boot doesn't initialize a logging
      // system that hasn't had this sequence applied (since 1.4.1).
      system.cleanUp();
      system.beforeInitialize();
      system.initialize(new LoggingInitializationContext(environment),logConfig, logFile);
    } catch (Exception ex) {
       logger.warn("Logging config file location '" + logConfig
                + "' cannot be opened and will be ignored");
    }
  }
}

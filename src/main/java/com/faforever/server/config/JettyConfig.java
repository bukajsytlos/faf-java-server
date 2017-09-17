package com.faforever.server.config;

//import org.eclipse.jetty.util.thread.QueuedThreadPool;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JettyConfig {

  private final ServerProperties fafServerProperties;

  public JettyConfig(ServerProperties fafServerProperties) {
    this.fafServerProperties = fafServerProperties;
  }

/*  @Inject
  public void configureJettyServletWebServerFactory(JettyEmbeddedServletContainerFactory factory) {
    factory.addServerCustomizers(server -> {
      final QueuedThreadPool threadPool = server.getBean(QueuedThreadPool.class);
      threadPool.setMinThreads(fafServerProperties.getJetty().getMinThreads());
      threadPool.setMaxThreads(fafServerProperties.getJetty().getMaxThreads());
      threadPool.setIdleTimeout(fafServerProperties.getJetty().getIdleTimeoutMillis());
    });
  }*/
}

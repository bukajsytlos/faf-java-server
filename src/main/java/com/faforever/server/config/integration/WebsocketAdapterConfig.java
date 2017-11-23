package com.faforever.server.config.integration;

import com.faforever.server.client.ClientConnection;
import com.faforever.server.client.ClientConnectionService;
import com.faforever.server.config.ServerProperties;
import com.faforever.server.integration.ChannelNames;
import com.faforever.server.integration.MessageHeaders;
import com.faforever.server.integration.Protocol;
import com.faforever.server.integration.legacy.transformer.LegacyRequestTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.dsl.HeaderEnricherSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.support.Consumer;
import org.springframework.integration.dsl.support.Transformers;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.websocket.IntegrationWebSocketContainer;
import org.springframework.integration.websocket.ServerWebSocketContainer;
import org.springframework.integration.websocket.inbound.WebSocketInboundChannelAdapter;
import org.springframework.integration.websocket.outbound.WebSocketOutboundMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.web.socket.server.jetty.JettyRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class WebsocketAdapterConfig {

  private final ServerProperties serverProperties;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ClientConnectionService clientConnectionService;

  @Inject
  public WebsocketAdapterConfig(ServerProperties serverProperties, ApplicationEventPublisher applicationEventPublisher, ClientConnectionService clientConnectionService) {
    this.serverProperties = serverProperties;
    this.applicationEventPublisher = applicationEventPublisher;
    this.clientConnectionService = clientConnectionService;
  }

  @Bean
  public IntegrationWebSocketContainer serverWebSocketContainer(ServletContext servletContext) {
    return new ServerWebSocketContainer("/ws-endpoint")
      .setAllowedOrigins("*")
      .setHandshakeHandler(handshakeHandler(servletContext))
      .withSockJs();
  }

  @Bean
  public DefaultHandshakeHandler handshakeHandler(ServletContext servletContext) {

    WebSocketPolicy policy = new WebSocketPolicy(WebSocketBehavior.SERVER);
    policy.setInputBufferSize(8192);
    policy.setIdleTimeout(600000);

    final DefaultHandshakeHandler defaultHandshakeHandler = new DefaultHandshakeHandler(
      new JettyRequestUpgradeStrategy(new WebSocketServerFactory(servletContext, policy)));
    return defaultHandshakeHandler;
  }

  /**
   * WebSocket inbound adapter that accepts connections and messages from clients.
   */
  @Bean
  public WebSocketInboundChannelAdapter webSocketInboundChannelAdapter(IntegrationWebSocketContainer serverWebSocketContainer) {
    WebSocketInboundChannelAdapter webSocketInboundChannelAdapter = new WebSocketInboundChannelAdapter(serverWebSocketContainer); // TODO: 8.5.2017 msgpack protocol registry
    webSocketInboundChannelAdapter.setOutputChannel(new DirectChannel());
    webSocketInboundChannelAdapter.setErrorChannelName(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
    return webSocketInboundChannelAdapter;
  }

  /**
   * TCP inbound adapter that accepts connections and messages from clients.
   */
  @Bean
  public WebSocketOutboundMessageHandler webSocketOutboundMessageHandler(IntegrationWebSocketContainer serverWebSocketContainer) {
    WebSocketOutboundMessageHandler webSocketOutboundMessageHandler = new WebSocketOutboundMessageHandler(serverWebSocketContainer); // TODO: 8.5.2017 msgpack protocol registry
    return webSocketOutboundMessageHandler;
  }

  /**
   * Integration flow that reads from the TCP inbound gateway and transforms legacy messages into internal messages.
   */
  @Bean
  public IntegrationFlow webSocketAdapterInboundFlow(WebSocketInboundChannelAdapter webSocketInboundChannelAdapter, ObjectMapper objectMapper) {
    return IntegrationFlows
      .from(webSocketInboundChannelAdapter)
//      .transform(legacyByteArrayToStringTransformer()) // TODO: 8.5.2017 msgpack transformation
      .transform(Transformers.fromJson(HashMap.class))
      .transform(new LegacyRequestTransformer(objectMapper))
      .enrichHeaders(sessionIdEnricher())
      .channel(ChannelNames.CLIENT_INBOUND)
      .get();
  }

  /**
   * Integration flow that converts an internal message into the legacy message format and sends it back to the original
   * client.
   */
  @Bean
  public IntegrationFlow webSocketAdapterOutboundFlow(WebSocketOutboundMessageHandler webSocketOutboundMessageHandler) {
    return IntegrationFlows
      .from(ChannelNames.WEB_SOCKET_OUTBOUND)
//      .transform(new LegacyResponseTransformer()) // TODO: 8.5.2017 msgpack transformation
      .split(broadcastSplitter())
      .enrichHeaders(sessionIdEnricher())
      .handle(webSocketOutboundMessageHandler)
      .get();
  }

  /**
   * Splits messages into per-connection messages if the "broadcast" header is set.
   */
  private AbstractMessageSplitter broadcastSplitter() {
    return new AbstractMessageSplitter() {
      @Override
      protected Object splitMessage(Message<?> message) {
        if (message.getHeaders().containsKey(MessageHeaders.BROADCAST)) {
          return clientConnectionService.getConnections().stream()
//            .filter(clientConnection -> clientConnection.getProtocol() == Protocol.LEGACY_UTF_16)
            .map(clientConnection -> MessageBuilder.fromMessage(message)
              .setHeader(MessageHeaders.CLIENT_CONNECTION, clientConnection)
            )
            .collect(Collectors.toList());
        }
        return message;
      }
    };
  }

  /**
   * Extracts the connection ID from the {@link ClientConnection} header and sets it as {@link IpHeaders#CONNECTION_ID}.
   */
  private Consumer<HeaderEnricherSpec> sessionIdEnricher() {
    return headerEnricherSpec -> headerEnricherSpec.headerFunction(IntegrationMessageHeaderAccessor.CORRELATION_ID,
      message -> {
        final String connectionId = (String) message.getHeaders().get(MessageHeaders.WS_SESSION_ID);
        final Optional<ClientConnection> clientConnection = clientConnectionService.getClientConnection(connectionId);
        if (!clientConnection.isPresent()) {
          try {
            clientConnectionService.createClientConnection(connectionId, Protocol.WEB_SOCKET, InetAddress.getByName((String) message.getHeaders().get(IpHeaders.IP_ADDRESS)));
          } catch (UnknownHostException e) {
            e.printStackTrace();
          }
        }
        return connectionId;
      });
  }
}

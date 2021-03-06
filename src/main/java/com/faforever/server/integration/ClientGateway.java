package com.faforever.server.integration;

import com.faforever.server.client.ClientConnection;
import com.faforever.server.response.ServerResponse;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Header;

import static com.faforever.server.integration.MessageHeaders.CLIENT_CONNECTION;

/**
 * A Spring Integration gateway to send messages to the client.
 */
@MessagingGateway
public interface ClientGateway {

  /**
   * Sends the specified message to the specified client connection.
   */
  @Gateway(requestChannel = ChannelNames.CLIENT_OUTBOUND)
  void send(ServerResponse serverResponse, @Header(CLIENT_CONNECTION) ClientConnection clientConnection);

  /**
   * Sends the specified message to all clients.
   */
  @Gateway(requestChannel = ChannelNames.CLIENT_OUTBOUND_BROADCAST)
  void broadcast(ServerResponse response);
}

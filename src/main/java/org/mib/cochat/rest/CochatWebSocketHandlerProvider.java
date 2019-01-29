package org.mib.cochat.rest;

import com.networknt.handler.HandlerProvider;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.service.ChatterService;
import org.mib.cochat.service.RoomService;
import org.mib.cochat.service.ServiceFactory;
import org.xnio.ChannelListener;

import java.io.IOException;

import static org.mib.cochat.rest.ContextInjectionHandler.chainedBlocking;
import static org.mib.common.validator.Validator.validateIntPositive;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
public class CochatWebSocketHandlerProvider implements HandlerProvider {

    private final RoomService roomService;
    private final ChatterService chatterService;
    private final ChannelListener<WebSocketChannel> listener;

    CochatWebSocketHandlerProvider() {
        ServiceFactory sf = ServiceFactory.getInstance();
        this.roomService = sf.getRoomService();
        this.chatterService = sf.getChatterService();
        this.listener = new AbstractReceiveListener() {
            @Override
            protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
                super.onClose(webSocketChannel, channel);
                String token = extractToken(webSocketChannel.getUrl());
                Chatter chatter = chatterService.getChatter(webSocketChannel);
                log.info("web socket channel for chatter {} disconnected from room {}", chatter.getName(), token);
                chatterService.unregisterWebSocketChannel(webSocketChannel);
                roomService.unregisterWebSocketChatter(token, webSocketChannel);
                roomService.publishNotification(token, chatter.getName() + " has left the room");
            }

            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                super.onFullTextMessage(channel, message);
                String token = extractToken(channel.getUrl());
                Chatter chatter = chatterService.getChatter(channel);
                log.debug("received message for room {} from web socket chatter {}", token, chatter.getName());
                CochatScope.setChatter(chatter);
                roomService.publishMessage(token, message.getData());
            }
        };
    }

    @Override
    public HttpHandler getHandler() {
        log.info("registering web socket handler...");
        return Handlers.path().addPrefixPath("/ws", chainedBlocking(chatterService, Handlers.websocket((exchange, channel) -> {
            String token = extractToken(exchange.getRequestURI());
            validateStringNotBlank(token, "room token");
            String chatterName = CochatScope.getChatter().getName();
            log.info("web socket channel connected to room {} for chatter {}", token, chatterName);
            roomService.publishNotification(token, chatterName + " has joined the room");
            roomService.registerWebSocketChatter(token, channel);
            chatterService.registerWebSocketChannel(channel);

            channel.getReceiveSetter().set(listener);
            channel.resumeReceives();
        })));
    }

    private String extractToken(String url) {
        validateStringNotBlank(url, "web socket url");
        int start = url.lastIndexOf("/") + 1;
        validateIntPositive(start, "token start index");
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (!isAlphaNumeric(c)) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isAlphaNumeric(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}

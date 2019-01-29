package org.mib.cochat.service;

import com.google.common.collect.Maps;
import io.undertow.websockets.core.WebSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.repo.Repository;
import org.mib.rest.exception.ForbiddenException;

import java.util.Map;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
public class ChatterService {

    private final Repository<String, Chatter> repository;
    private final Map<WebSocketChannel, Chatter> channelChatterMap;

    public ChatterService(final Repository<String, Chatter> repository, final boolean isWebSocketEnabled) {
        validateObjectNotNull(repository, "chatter repo");
        this.repository = repository;
        this.channelChatterMap = isWebSocketEnabled ? Maps.newConcurrentMap() : null;
    }

    public Chatter createChatter(String name) {
        validateStringNotBlank(name, "chatter name");
        Chatter chatter = new Chatter(name);
        if (!repository.store(chatter.getToken(), chatter)) {
            chatter = new Chatter(name);
        }
        log.info("created chatter {} with token {}", name, chatter.getToken());
        return chatter;
    }

    public Chatter getChatter(String token) {
        validateStringNotBlank(token, "chatter token");
        log.debug("retrieving chatter with token {}...", token);
        return repository.retrieve(token);
    }

    public Chatter getChatter(WebSocketChannel channel) {
        if (channelChatterMap == null) {
            throw new IllegalStateException("web socket not enabled");
        }
        validateObjectNotNull(channel, "websocket channel");
        log.debug("retrieving chatter for web socket channel {}...", channel.getSourceAddress());
        return channelChatterMap.get(channel);
    }

    public void registerWebSocketChannel(WebSocketChannel channel) {
        if (channelChatterMap == null) {
            throw new IllegalStateException("web socket not enabled");
        }
        validateObjectNotNull(channel, "web socket channel");
        Chatter chatter = CochatScope.getChatter();
        log.debug("registering web socket channel {} to chatter {}...", channel.getSourceAddress(), chatter);
        channelChatterMap.put(channel, chatter);
    }

    public void unregisterWebSocketChannel(WebSocketChannel channel) {
        if (channelChatterMap == null) {
            throw new IllegalStateException("web socket not enabled");
        }
        validateObjectNotNull(channel, "web socket channel");
        log.debug("unregistering web socket channel {}...", channel.getSourceAddress());
        channelChatterMap.remove(channel);
    }

    public void deleteChatter(String token) {
        validateStringNotBlank(token, "chatter token");
        log.info("deleting chatter with token {}...", token);
        Chatter chatter = repository.retrieve(token);
        if (chatter == null) {
            log.warn("chatter with token {} does not exist, ignoring...", token);
            return;
        }
        if (!chatter.equals(CochatScope.getChatter())) {
            log.error("permission denied to delete chatter with token {}", token);
            throw new ForbiddenException("permission denied for deleting chatter " + token);
        }
        if (repository.delete(token)) {
            log.info("deleted chatter with token {}", token);
        } else {
            log.error("unable to delete chatter with token {}", token);
            throw new RuntimeException("failed to delete chatter with token " + token);
        }
    }
}

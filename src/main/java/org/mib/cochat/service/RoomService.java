package org.mib.cochat.service;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.message.Information;
import org.mib.cochat.message.Message;
import org.mib.cochat.message.Notification;
import org.mib.cochat.repo.Repository;
import org.mib.cochat.room.Room;
import org.mib.rest.exception.ForbiddenException;
import org.mib.rest.exception.ResourceNotFoundException;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import static org.mib.common.ser.Serdes.serializeAsJsonString;
import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
@SuppressWarnings("deprecation")
public class RoomService {

    private final Repository<String, Room> repository;
    private final MessageService messageService;
    private final Map<String, Set<WebSocketChannel>> webSocketChannelsMap;
    private final WebSocketCallback<Void> webSocketCallback;

    public RoomService(final Repository<String, Room> repository, final MessageService messageService, final boolean isWebSocketEnabled) {
        validateObjectNotNull(repository, "room repository");
        validateObjectNotNull(messageService, "message service");
        this.repository = repository;
        this.messageService = messageService;
        this.webSocketChannelsMap = isWebSocketEnabled ? Maps.newConcurrentMap() : null;
        this.webSocketCallback = isWebSocketEnabled ? new WebSocketCallback<Void>() {
            @Override
            public void complete(WebSocketChannel channel, Void context) {
                log.debug("message delivered to peer {}", channel.getSourceAddress());
            }

            @Override
            public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
                log.error("failed to deliver message to peer {}", channel.getSourceAddress(), throwable);
            }
        } : null;
    }

    public Room createRoom(String name) {
        validateStringNotBlank(name, "room name");
        Room room = new Room(name);
        while (!repository.store(room.getToken(), room)) {
            log.warn("room {} occupied, re-generating...");
            room = new Room(name);
        }
        log.info("created room {} with name {}", room.getToken(), name);
        return room;
    }

    public Room getRoom(String token) {
        validateStringNotBlank(token, "room token");
        log.debug("retrieving room {}...", token);
        return repository.retrieve(token);
    }

    public Room getExistingRoom(String token) {
        Room room = getRoom(token);
        if (room == null) {
            throw new ResourceNotFoundException("no room found for " + token);
        }
        return room;
    }

    public void deleteRoom(String token) {
        validateStringNotBlank(token, "room token");
        log.info("deleting room {}...", token);
        Room room = repository.retrieve(token);
        if (room == null) {
            log.warn("room {} does not exist, ignoring...", token);
            return;
        }
        if (!room.getCreator().equals(CochatScope.getChatter())) {
            log.error("permission denied to delete room {}, only creator is allowed", token);
            throw new ForbiddenException("permission denied to delete room " + token);
        }
        if (webSocketChannelsMap != null) {
            Set<WebSocketChannel> webSocketChannels = webSocketChannelsMap.remove(token);
            if (webSocketChannels != null && !webSocketChannels.isEmpty()) {
                webSocketChannels.forEach(IOUtils::closeQuietly);
            }
        }
        room.getMessages().forEach(message -> messageService.deleteMessage(message.getToken()));
        if (repository.delete(token)) {
            log.info("deleted room {} with name {}", token, room.getName());
        } else {
            log.error("unable to delete room {} with name {}", token, room.getName());
            throw new RuntimeException("failed to delete room " + token);
        }
    }

    public Message publishMessage(String token, String text) throws IOException {
        Room room = getExistingRoom(token);
        Message message = messageService.createMessage(text);
        publish(room, message);
        return message;
    }

    public Message publishMessage(String token, String name, File tmpFile, String mimeType) throws IOException {
        Room room = getExistingRoom(token);
        Message message = messageService.createMessage(name, tmpFile, mimeType);
        publish(room, message);
        return message;
    }

    public Notification publishNotification(String token, String content) {
        Room room = getExistingRoom(token);
        Notification notification = new Notification(content);
        publish(room, notification);
        return notification;
    }

    public void registerWebSocketChatter(String token, WebSocketChannel channel) {
        if (webSocketChannelsMap == null) {
            throw new IllegalStateException("web socket not enabled");
        }
        validateObjectNotNull(channel, "web socket channel");
        // ensure room exists
        getExistingRoom(token);
        log.debug("registering web socket channel {} for room {}...", channel.getSourceAddress(), token);
        Set<WebSocketChannel> channels = webSocketChannelsMap.computeIfAbsent(token, t -> Sets.newConcurrentHashSet());
        channels.add(channel);
    }

    public void unregisterWebSocketChatter(String token, WebSocketChannel channel) {
        if (webSocketChannelsMap == null) {
            throw new IllegalStateException("web socket not enabled");
        }
        validateObjectNotNull(channel, "web socket channel");
        // ensure room exists
        getExistingRoom(token);
        log.debug("unregistering web socket channel {} for room {}...", channel.getSourceAddress(), token);
        Set<WebSocketChannel> channels = webSocketChannelsMap.get(token);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) webSocketChannelsMap.remove(token);
        }
        IOUtils.closeQuietly(channel);
    }

    public void purgeRoom(String token) {
        validateStringNotBlank(token, "room token");
        log.info("purging room {}...", token);
        Room room = repository.retrieve(token);
        if (room == null) {
            log.warn("room {} does not exist, ignoring...", token);
            return;
        }
        if (!room.getCreator().equals(CochatScope.getChatter())) {
            log.error("permission denied to purge room {}, only creator is allowed", token);
            throw new ForbiddenException("permission denied to purge room " + token);
        }
        room.getMessages().forEach(message -> messageService.deleteMessage(message.getToken()));
        synchronized (room.getMessages()) {
            room.getMessages().clear();
        }
        log.info("purged room {}", token);
    }

    private void publish(Room room, Information info) {
        validateObjectNotNull(room, "room");
        validateObjectNotNull(info, "info");
        info.setRoom(room);
        if (info instanceof Message) {
            synchronized (room.getMessages()) {
                Message msg = (Message) info;
                room.getMessages().add(msg);
                room.getMessages().sort(Comparator.comparingLong(Message::getTimestamp));
            }
        }
        if (webSocketChannelsMap != null) {
            Set<WebSocketChannel> channels = webSocketChannelsMap.computeIfAbsent(room.getToken(), t -> Sets.newConcurrentHashSet());
            if (channels.isEmpty()) return;
            String content = serializeAsJsonString(info);
            channels.forEach(channel -> WebSockets.sendText(content, channel, webSocketCallback));
        }
    }
}

package org.mib.cochat.rest;

import com.networknt.handler.HandlerProvider;
import com.networknt.health.HealthGetHandler;
import com.networknt.metrics.prometheus.PrometheusGetHandler;
import io.undertow.Handlers;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.message.Image;
import org.mib.cochat.message.Message;
import org.mib.cochat.message.RawFile;
import org.mib.cochat.room.Room;
import org.mib.cochat.service.ChatterService;
import org.mib.cochat.service.FileService;
import org.mib.cochat.service.MessageService;
import org.mib.cochat.service.RoomService;
import org.mib.cochat.service.ServiceFactory;
import org.mib.common.config.ConfigProvider;
import org.mib.rest.exception.BadRequestException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import static org.mib.cochat.rest.ContextInjectionHandler.blocking;
import static org.mib.cochat.rest.ContextInjectionHandler.chainedBlocking;
import static org.mib.common.ser.Serdes.deserializeFromJson;
import static org.mib.common.ser.Serdes.serializeAsJsonString;
import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
@SuppressWarnings("unused")
public class CochatAPIHandlerProvider implements HandlerProvider {

    static final String TOKEN_FIELD_NAME = "token";
    private static final String TIMESTAMP_PARAM_NAME = "_timestamp";
    private static final String DOWNLOAD_HEADER_PREFIX = "attachment; filename=";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final HttpString CORS_HEADER = HttpString.tryFromString("Access-Control-Allow-Origin");
    private static final String ALL = "*";
    private static final String TOKEN_COOKIE_FORMAT = TOKEN_FIELD_NAME + "=%s; HttpOnly; Path=/";
    private static final Object EMPTY_OBJECT = new Object();

    private static final String ASSETS_DIR_PATH = ConfigProvider.get("assets_dir_path");

    private final RoomService roomService;
    private final MessageService messageService;
    private final ChatterService chatterService;
    private final FileService fileService;

    public CochatAPIHandlerProvider() {
        ServiceFactory sf = ServiceFactory.getInstance();
        this.fileService = sf.getFileService();
        this.messageService = sf.getMessageService();
        this.roomService = sf.getRoomService();
        this.chatterService = sf.getChatterService();
    }

    @Override
    public HttpHandler getHandler() {
        log.info("registering routing handler...");
        return Handlers.routing()
                .add("OPTIONS", "/api/*", corsHandler())
                .get("/api/description", describeHandler())
                .get("/api/sessions", chatterRetrieveHandler())
                .post("/api/sessions", chatterCreateHandler())
                .post("/api/rooms", roomCreateHandler())
                .get("/api/rooms/{" + TOKEN_FIELD_NAME + "}", roomRetrieveHandler())
                .delete("/api/rooms/{" + TOKEN_FIELD_NAME + "}", roomDeleteHandler())
                .post("/api/rooms/{" + TOKEN_FIELD_NAME + "}/messages", messagePublishHandler())
                .post("/api/rooms/{" + TOKEN_FIELD_NAME + "}/files", filePublishHandler())
                .get("/api/rooms/{" + TOKEN_FIELD_NAME + "}/messages", messageRetrieveHandler())
                .delete("/api/messages/{" + TOKEN_FIELD_NAME + "}", messageDeleteHandler())
                .get("/api/files/{" + TOKEN_FIELD_NAME + "}", fileRetrieveHandler())
                .get("/api/health", new HealthGetHandler())
                .get("/api/metrics", new PrometheusGetHandler())
                .get("/", assetHandler(ASSETS_DIR_PATH + "/index.html"))
                .get("/session", assetHandler(ASSETS_DIR_PATH + "/chatter.html"))
                .get("/{" + TOKEN_FIELD_NAME + "}", assetHandler(ASSETS_DIR_PATH + "/room.html"))
                .get("/assets/{" + TOKEN_FIELD_NAME + "}", assetHandler());
    }

    private HttpHandler corsHandler() {
        return exchange -> {
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseHeaders().add(CORS_HEADER, ALL);
            exchange.endExchange();
        };
    }

    private HttpHandler describeHandler() {
        final String response = serializeAsJsonString(ConfigProvider.getBoolean("web_socket_enabled") ?
                new Description(true, ConfigProvider.getInt("web_socket_port")) : new Description()
        );
        return exchange -> {
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(response);
        };
    }

    private HttpHandler chatterRetrieveHandler() {
        return blocking(exchange -> {
            Cookie chatterToken = exchange.getRequestCookies().get(TOKEN_FIELD_NAME);
            if (chatterToken != null && StringUtils.isNotBlank(chatterToken.getValue())) {
                Chatter chatter = chatterService.getChatter(chatterToken.getValue());
                if (chatter != null) {
                    sendJson(exchange, chatter);
                    return;
                }
            }
            sendJson(exchange, EMPTY_OBJECT);
        });
    }

    private HttpHandler chatterCreateHandler() {
        return blocking(exchange -> {
            Cookie chatterToken = exchange.getRequestCookies().get(TOKEN_FIELD_NAME);
            Chatter chatter = null;
            if (chatterToken != null && StringUtils.isNotBlank(chatterToken.getValue())) {
                chatter = chatterService.getChatter(chatterToken.getValue());
            }
            if (chatter == null) {
                String name = deserializeFromJson(IOUtils.toByteArray(exchange.getInputStream()), CreationRequest.class).name;
                chatter = chatterService.createChatter(name);
                exchange.getResponseHeaders().add(Headers.SET_COOKIE, String.format(TOKEN_COOKIE_FORMAT, chatter.getToken()));
            }
            sendJson(exchange, chatter);
        });
    }

    private HttpHandler roomCreateHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String name = deserializeFromJson(IOUtils.toByteArray(exchange.getInputStream()), CreationRequest.class).name;
            Room room = roomService.createRoom(name);
            sendJson(exchange, room);
        });
    }

    private HttpHandler roomRetrieveHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String token = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            Room room = roomService.getExistingRoom(token);
            sendJson(exchange, room);
        });
    }

    private HttpHandler roomDeleteHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String token = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            roomService.deleteRoom(token);
            exchange.setStatusCode(StatusCodes.OK);
            exchange.endExchange();
        });
    }

    private HttpHandler messagePublishHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String roomToken = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            String content = IOUtils.toString(exchange.getInputStream(), exchange.getRequestCharset());
            Message message = roomService.publishMessage(roomToken, content);
            sendJson(exchange, message);
        });
    }

    private HttpHandler filePublishHandler() {
        return chainedBlocking(chatterService, new EagerFormParsingHandler(exchange -> {
            String roomToken = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            FormData attachment = exchange.getAttachment(FormDataParser.FORM_DATA);
            if (attachment == null) {
                throw new BadRequestException("no form received");
            }
            FormData.FormValue fileValue = attachment.getFirst("file");
            if (fileValue == null) {
                throw new BadRequestException("no file uploaded");
            }
            if (!fileValue.isFile()) {
                throw new BadRequestException("not a file");
            }
            String mimeType = fileValue.getHeaders().getFirst(Headers.CONTENT_TYPE);
            String filePath = fileValue.getPath().toAbsolutePath().toString();
            Message message = roomService.publishMessage(roomToken, fileValue.getFileName(), new File(filePath), mimeType);
            sendJson(exchange, message);
        }));
    }

    private HttpHandler messageRetrieveHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String roomToken = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            Deque<String> timestamps = exchange.getQueryParameters().get(TIMESTAMP_PARAM_NAME);
            long timestamp = timestamps == null ? 0 : Long.parseLong(timestamps.getFirst());
            Room room = roomService.getExistingRoom(roomToken);
            List<Message> messages = room.getMessages().stream().filter(msg -> msg.getTimestamp() >= timestamp).collect(Collectors.toList());
            sendJson(exchange, messages);
        });
    }

    private HttpHandler messageDeleteHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String token = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            messageService.deleteMessage(token);
            exchange.setStatusCode(StatusCodes.OK);
            exchange.endExchange();
        });
    }

    private HttpHandler fileRetrieveHandler() {
        return chainedBlocking(chatterService, exchange -> {
            String token = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            Message message = messageService.getExistingMessage(token);
            if (!(message instanceof RawFile)) {
                throw new BadRequestException("message " + token + " not file");
            }
            RawFile file = (RawFile) message;
            if (!(message instanceof Image)) {
                exchange.getResponseHeaders().add(Headers.CONTENT_DISPOSITION, DOWNLOAD_HEADER_PREFIX + file.getName());
            }
            sendFile(exchange, fileService.getFilePath(file));
        });
    }

    private HttpHandler assetHandler(String resourcePath) {
        validateStringNotBlank(resourcePath, "resource path");
        return blocking(exchange -> sendFile(exchange, resourcePath));
    }

    private HttpHandler assetHandler() {
        return blocking(exchange -> {
            String resourceName = exchange.getQueryParameters().get(TOKEN_FIELD_NAME).getFirst();
            String path = ASSETS_DIR_PATH + File.separator + resourceName;
            sendFile(exchange, path);
        });
    }

    private void sendJson(HttpServerExchange exchange, Object object) {
        validateObjectNotNull(object, "json object");
        HeaderMap headers = exchange.getResponseHeaders();
        headers.add(CORS_HEADER, ALL);
        headers.add(Headers.CONTENT_TYPE, JSON_CONTENT_TYPE);
        exchange.getResponseSender().send(serializeAsJsonString(object), StandardCharsets.UTF_8);
    }

    private void sendFile(HttpServerExchange exchange, String path) throws IOException {
        log.debug("sending file {} to {}...", path, exchange.getSourceAddress());
        File file = new File(path);
        exchange.setStatusCode(StatusCodes.OK);
        HeaderMap headers = exchange.getResponseHeaders();
        headers.add(CORS_HEADER, ALL);
        headers.add(Headers.CONTENT_TYPE, Files.probeContentType(file.toPath()));
        headers.add(Headers.CONTENT_LENGTH, file.length());
        try (FileInputStream is = new FileInputStream(file)) {
            exchange.getResponseSender().transferFrom(is.getChannel(), IoCallback.END_EXCHANGE);
        }
    }

    @Data
    private static class CreationRequest {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Description {
        private boolean webSocketEnabled;
        private int webSocketPort;
    }
}

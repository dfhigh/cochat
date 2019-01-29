package org.mib.cochat.rest;

import com.google.common.collect.ImmutableList;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.StatusCodes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.service.ChatterService;
import org.mib.rest.exception.BadRequestException;
import org.mib.rest.exception.ForbiddenException;
import org.mib.rest.exception.ResourceNotFoundException;
import org.mib.rest.exception.UnauthorizedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.mib.cochat.rest.CochatAPIHandlerProvider.TOKEN_FIELD_NAME;
import static org.mib.common.validator.Validator.validateCollectionNotEmptyContainsNoNull;
import static org.mib.common.validator.Validator.validateObjectNotNull;

@Slf4j
class ContextInjectionHandler implements HttpHandler {

    private final ChatterService chatterService;
    private final List<HttpHandler> nextHandlers;

    private ContextInjectionHandler(final ChatterService chatterService, final HttpHandler... handlers) {
        validateObjectNotNull(chatterService, "chatter service");
        if (handlers == null || handlers.length == 0) {
            throw new IllegalArgumentException("next handler array can't be null or empty");
        }
        Arrays.stream(handlers).forEach(handler -> validateObjectNotNull(handler, "handler"));
        this.chatterService = chatterService;
        this.nextHandlers = ImmutableList.copyOf(handlers);
    }

    private ContextInjectionHandler(final ChatterService chatterService, final Collection<HttpHandler> handlers) {
        validateObjectNotNull(chatterService, "chatter service");
        validateCollectionNotEmptyContainsNoNull(handlers, "handler collection");
        this.chatterService = chatterService;
        this.nextHandlers = ImmutableList.copyOf(handlers);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        try {
            if (nextHandlers.isEmpty()) {
                log.warn("no next handler specified");
                throw new IllegalStateException("next handler not specified");
            }
            Chatter chatter = null;
            Cookie chatterToken = exchange.getRequestCookies().get(TOKEN_FIELD_NAME);
            if (chatterToken != null && StringUtils.isNotBlank(chatterToken.getValue())) {
                chatter = chatterService.getChatter(chatterToken.getValue());
            }
            if (chatter == null) throw new UnauthorizedException("empty or invalid session");
            CochatScope.setChatter(chatter);
            for (HttpHandler handler : nextHandlers) {
                handler.handleRequest(exchange);
            }
        } catch (BadRequestException e) {
            log.error("bad input parameters for request {}", exchange.getRequestURI(), e);
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.setReasonPhrase(e.getMessage());
        } catch (UnauthorizedException e) {
            log.error("unauthorized access for request {}", exchange.getRequestURI(), e);
            exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
            exchange.setReasonPhrase(e.getMessage());
        } catch (ForbiddenException e) {
            log.error("forbidden operation for request {}", exchange.getRequestURI(), e);
            exchange.setStatusCode(StatusCodes.FORBIDDEN);
            exchange.setReasonPhrase(e.getMessage());
        } catch (ResourceNotFoundException e) {
            log.error("resource not found for request {}", exchange.getRequestURI(), e);
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.setReasonPhrase(e.getMessage());
        } catch (Exception e) {
            log.error("failed to handle request {}", exchange.getRequestURI(), e);
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            exchange.setReasonPhrase(e.getMessage());
        } finally {
            CochatScope.clear();
        }
    }

    static HttpHandler chained(final ChatterService chatterService, final HttpHandler... handlers) {
        return new ContextInjectionHandler(chatterService, handlers);
    }

    static HttpHandler chained(final ChatterService chatterService, final Collection<HttpHandler> handlers) {
        return new ContextInjectionHandler(chatterService, handlers);
    }

    static HttpHandler blocking(final HttpHandler handler) {
        validateObjectNotNull(handler, "handler");
        return new BlockingHandler(handler);
    }

    static HttpHandler chainedBlocking(final ChatterService chatterService, final HttpHandler... handlers) {
        return new BlockingHandler(chained(chatterService, handlers));
    }

    static HttpHandler chainedBlocking(final ChatterService chatterService, final Collection<HttpHandler> handlers) {
        return new BlockingHandler(chained(chatterService, handlers));
    }
}

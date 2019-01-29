package org.mib.cochat.service;

import lombok.extern.slf4j.Slf4j;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.message.Message;
import org.mib.cochat.message.RawFile;
import org.mib.cochat.message.Text;
import org.mib.cochat.repo.Repository;
import org.mib.rest.exception.ForbiddenException;
import org.mib.rest.exception.ResourceNotFoundException;

import java.io.File;
import java.io.IOException;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
public class MessageService {

    private final Repository<String, Message> repository;
    private final FileService fileService;

    public MessageService(final Repository<String, Message> repository, final FileService fileService) {
        validateObjectNotNull(repository, "message repository");
        validateObjectNotNull(fileService, "file service");
        this.repository = repository;
        this.fileService = fileService;
    }

    public Message createMessage(String content) throws IOException {
        return storeMessage(new Text(content));
    }

    public Message createMessage(String filename, File tmpFile, String mimeType) throws IOException {
        return storeMessage(fileService.createFile(filename, tmpFile, mimeType));
    }

    public Message getMessage(String token) {
        validateStringNotBlank(token, "message token");
        log.debug("retrieving message {}...", token);
        return repository.retrieve(token);
    }

    public Message getExistingMessage(String token) {
        Message message = getMessage(token);
        if (message == null) {
            throw new ResourceNotFoundException("no message found for " + token);
        }
        return message;
    }

    public void deleteMessage(String token) {
        validateStringNotBlank(token, "message token");
        log.info("deleting message {}...", token);
        Message message = repository.retrieve(token);
        if (message == null) {
            log.warn("message {} does not exist, ignoring...", token);
            return;
        }
        Chatter chatter = CochatScope.getChatter();
        if (!(message.getAuthor().equals(chatter) || (message.getRoom() != null && chatter.equals(message.getRoom().getCreator())))) {
            log.error("permission denied to delete message {}", token);
            throw new ForbiddenException("permission denied to delete message " + token);
        }
        if (message instanceof RawFile) {
            fileService.deleteFile((RawFile) message);
        }
        if (repository.delete(token)) {
            log.info("deleted message {}", token);
        } else {
            log.error("unable to delete message {}", token);
            throw new RuntimeException("failed to delete message " + token);
        }
    }

    private Message storeMessage(Message message) throws IOException {
        while (!repository.store(message.getToken(), message)) {
            log.warn("message token {} already occupied, re-generating...", message.getToken());
            String originalToken = message.getToken();
            message.refreshToken();
            if (message instanceof RawFile) {
                RawFile file = (RawFile) message;
                fileService.refreshLocationForFile(file, originalToken);
            }
        }
        log.info("created message with token {}", message.getToken());
        return message;
    }
}

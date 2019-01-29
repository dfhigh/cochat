package org.mib.cochat.room;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;
import org.mib.cochat.message.Message;

import java.util.List;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Getter
public class Room {

    private static final int ROOM_TOKEN_LEN = 6;

    private final String token;
    private final Chatter creator;
    private final String name;
    private @JsonIgnore final List<Message> messages;

    public Room(final String name) {
        this(CochatScope.getChatter(), name);
    }

    public Room(final Chatter creator, final String name) {
        validateObjectNotNull(creator, "room creator");
        validateStringNotBlank(name, "room name");
        this.token = RandomStringUtils.randomAlphanumeric(ROOM_TOKEN_LEN);
        this.creator = creator;
        this.name = name;
        this.messages = Lists.newArrayList();
    }

    public Room(final Chatter creator, final String token, final String name) {
        validateObjectNotNull(creator, "room creator");
        validateStringNotBlank(name, "room name");
        validateStringNotBlank(token, "room token");
        this.token = token;
        this.creator = creator;
        this.name = name;
        this.messages = Lists.newArrayList();
    }
}

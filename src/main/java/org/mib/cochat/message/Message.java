package org.mib.cochat.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.RandomStringUtils;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.context.CochatScope;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class Message extends Information {

    private static final int MESSAGE_TOKEN_LEN = 16;

    protected volatile String token;
    private final Chatter author;

    public Message() {
        this(CochatScope.getChatter(), System.currentTimeMillis());
    }

    public Message(final Chatter author, final long timestamp) {
        super(timestamp);
        validateObjectNotNull(author, "author");
        this.token = RandomStringUtils.randomAlphanumeric(MESSAGE_TOKEN_LEN);
        this.author = author;
    }

    Message(final Chatter author, final String token, final long timestamp) {
        super(timestamp);
        validateObjectNotNull(author, "author");
        validateStringNotBlank(token, "token");
        this.token = token;
        this.author = author;
    }

    public void refreshToken() {
        this.token = RandomStringUtils.randomAlphanumeric(MESSAGE_TOKEN_LEN);
    }
}

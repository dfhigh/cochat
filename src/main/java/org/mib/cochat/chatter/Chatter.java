package org.mib.cochat.chatter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.RandomStringUtils;
import org.mib.common.hash.FNVHash;

import static org.mib.common.validator.Validator.validateStringNotBlank;

@Getter
@ToString
@EqualsAndHashCode
public class Chatter {

    private static final int TOKEN_LEN = 32;

    private final String token;
    private final String name;
    private final long identity;

    public Chatter(final String name) {
        validateStringNotBlank(name, "chatter name");
        this.name = name;
        this.token = RandomStringUtils.randomAlphanumeric(TOKEN_LEN);
        this.identity = FNVHash.hash64(token);
    }

    public Chatter(final String token, final String name) {
        validateStringNotBlank(name, "chatter name");
        validateStringNotBlank(token, "chatter token");
        this.token = token;
        this.name = name;
        this.identity = FNVHash.hash64(token);
    }
}

package org.mib.cochat.message;

import lombok.Getter;
import org.mib.cochat.chatter.Chatter;

import static org.mib.common.validator.Validator.validateStringNotBlank;

public class RawFile extends Message {

    private @Getter final String name;

    public RawFile(final String name) {
        super();
        validateStringNotBlank(name, "file name");
        this.name = name;
    }

    public RawFile(final Chatter author, final String token, final long timestamp, final String name) {
        super(author, token, timestamp);
        validateStringNotBlank(name, "file name");
        this.name = name;
    }
}

package org.mib.cochat.message;

import lombok.Getter;
import org.mib.cochat.chatter.Chatter;

import static org.mib.common.validator.Validator.validateStringNotBlank;

public class Text extends Message {

    private @Getter final String text;

    public Text(final Chatter author, final String token, final long timestamp, final String text) {
        super(author, token, timestamp);
        validateStringNotBlank(text, "text message");
        this.text = text;
    }

    public Text(final String text) {
        super();
        validateStringNotBlank(text, "text message");
        this.text = text;
    }
}

package org.mib.cochat.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static org.mib.common.validator.Validator.validateStringNotBlank;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Notification extends Information {

    private final String content;

    public Notification(final String content) {
        this(content, System.currentTimeMillis());
    }

    public Notification(final String content, final long timestamp) {
        super(timestamp);
        validateStringNotBlank(content, "notification content");
        this.content = content;
    }
}

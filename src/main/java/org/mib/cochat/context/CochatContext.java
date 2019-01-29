package org.mib.cochat.context;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.mib.cochat.chatter.Chatter;

import static org.mib.common.validator.Validator.validateObjectNotNull;

@Getter
@ToString
@EqualsAndHashCode
public class CochatContext {

    private final Chatter chatter;

    public CochatContext(final Chatter chatter) {
        validateObjectNotNull(chatter, "chatter");
        this.chatter = chatter;
    }
}

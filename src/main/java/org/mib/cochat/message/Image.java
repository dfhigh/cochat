package org.mib.cochat.message;

import lombok.Getter;
import org.mib.cochat.chatter.Chatter;

import static org.mib.common.validator.Validator.validateIntPositive;

@Getter
public class Image extends RawFile {

    private final int height;
    private final int width;

    public Image(final String name, final int height, final int width) {
        super(name);
        validateIntPositive(height, "image height");
        validateIntPositive(width, "image width");
        this.height = height;
        this.width = width;
    }

    public Image(final Chatter author, String token, final long timestamp, final String name, final int height, final int width) {
        super(author, token, timestamp, name);
        validateIntPositive(height, "image height");
        validateIntPositive(width, "image width");
        this.height = height;
        this.width = width;
    }
}

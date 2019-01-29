package org.mib.cochat.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.mib.cochat.room.Room;

import static org.mib.common.validator.Validator.validateLongPositive;
import static org.mib.common.validator.Validator.validateObjectNotNull;

@Getter
@ToString
@EqualsAndHashCode
public abstract class Information {

    private @JsonIgnore volatile Room room;
    private final long timestamp;

    Information() {
        this(System.currentTimeMillis());
    }

    Information(final long timestamp) {
        validateLongPositive(timestamp, "timestamp");
        this.timestamp = timestamp;
    }

    @JsonIgnore
    public void setRoom(Room room) {
        validateObjectNotNull(room, "room");
        if (this.room == null) {
            this.room = room;
        } else {
            throw new IllegalStateException("room already set");
        }
    }

    public String getType() {
        return getClass().getSimpleName();
    }
}

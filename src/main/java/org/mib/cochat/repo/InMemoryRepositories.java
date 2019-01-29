package org.mib.cochat.repo;

import lombok.Getter;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.message.Message;
import org.mib.cochat.room.Room;

@Getter
public class InMemoryRepositories implements Repositories {

    private final Repository<String, Chatter> chatterRepository;
    private final Repository<String, Room> roomRepository;
    private final Repository<String, Message> messageRepository;

    public InMemoryRepositories() {
        this.chatterRepository = new InMemoryRepository<>();
        this.roomRepository = new InMemoryRepository<>();
        this.messageRepository = new InMemoryRepository<>();
    }
}

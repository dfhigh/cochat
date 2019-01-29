package org.mib.cochat.repo;

import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.message.Message;
import org.mib.cochat.room.Room;

public interface Repositories {

    Repository<String, Chatter> getChatterRepository();

    Repository<String, Room> getRoomRepository();

    Repository<String, Message> getMessageRepository();
}

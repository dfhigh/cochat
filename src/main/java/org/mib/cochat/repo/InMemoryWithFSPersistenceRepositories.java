package org.mib.cochat.repo;

import lombok.Getter;
import org.mib.cochat.chatter.Chatter;
import org.mib.cochat.message.Image;
import org.mib.cochat.message.Message;
import org.mib.cochat.message.RawFile;
import org.mib.cochat.message.Text;
import org.mib.cochat.room.Room;

import java.util.Comparator;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Getter
public class InMemoryWithFSPersistenceRepositories implements Repositories {

    private final Repository<String, Chatter> chatterRepository;
    private final Repository<String, Room> roomRepository;
    private final Repository<String, Message> messageRepository;

    public InMemoryWithFSPersistenceRepositories(final String chatterRepoPath, final PersistStrategy chatterRepoPersistStrategy,
                                                 final String roomRepoPath, final PersistStrategy roomRepoPersistStrategy,
                                                 final String messageRepoPath, final PersistStrategy messageRepoPersistStrategy) {
        validateStringNotBlank(chatterRepoPath, "chatter repository path");
        validateObjectNotNull(chatterRepoPersistStrategy, "chatter repo persist strategy");
        validateStringNotBlank(roomRepoPath, "room repository path");
        validateObjectNotNull(roomRepoPersistStrategy, "room repo persist strategy");
        validateStringNotBlank(messageRepoPath, "message repository path");
        validateObjectNotNull(messageRepoPersistStrategy, "message repo persist strategy");
        this.chatterRepository = new InMemoryWithFSPersistenceRepository<String, Chatter>(chatterRepoPath, chatterRepoPersistStrategy) {

            @Override
            protected String serKey(String key) {
                return key;
            }

            @Override
            protected String serValue(Chatter value) {
                return value.getToken() + '\t' + value.getName().replaceAll("\t", "\\t");
            }

            @Override
            protected String fromSerKey(String serKey) {
                return serKey;
            }

            @Override
            protected Chatter fromSerValue(String serValue) {
                validateStringNotBlank(serValue, "serialized chatter value");
                String[] fields = serValue.split("\t");
                if (fields.length != 2) throw new IllegalArgumentException("invalid serialized chatter value " + serValue);
                return new Chatter(fields[0], fields[1].replaceAll("\\t", "\t"));
            }
        };
        this.roomRepository = new InMemoryWithFSPersistenceRepository<String, Room>(roomRepoPath, roomRepoPersistStrategy) {

            @Override
            protected String serKey(String key) {
                return key;
            }

            @Override
            protected String serValue(Room value) {
                return value.getToken() + '\t' + value.getName().replaceAll("\t", "\\t") + '\t' + value.getCreator().getToken();
            }

            @Override
            protected String fromSerKey(String serKey) {
                return serKey;
            }

            @Override
            protected Room fromSerValue(String serValue) {
                validateStringNotBlank(serValue, "serialized room value");
                String[] fields = serValue.split("\t");
                if (fields.length != 3) throw new IllegalArgumentException("invalid serialized room value " + serValue);
                return new Room(chatterRepository.retrieve(fields[2]), fields[0], fields[1].replaceAll("\\t", "\t"));
            }
        };
        this.messageRepository = new InMemoryWithFSPersistenceRepository<String, Message>(messageRepoPath, messageRepoPersistStrategy) {

            @Override
            protected String serKey(String key) {
                return key;
            }

            @Override
            protected String serValue(Message value) {
                if (value instanceof Text) {
                    Text text = (Text) value;
                    return "1\t" + text.getToken() + '\t' + text.getRoom().getToken() + '\t' + text.getAuthor().getToken() +
                            '\t' + text.getTimestamp() + '\t' + text.getText().replaceAll("\t", "\\t");
                } else if (value instanceof Image) {
                    Image image = (Image) value;
                    return "2\t" + image.getToken() + '\t' + image.getRoom().getToken() + '\t' + image.getAuthor().getToken() +
                            '\t' + image.getTimestamp() + '\t' + image.getName().replaceAll("\t", "\\t") +
                            '\t' + image.getHeight() + '\t' + image.getWidth();
                } else if (value instanceof RawFile) {
                    RawFile file = (RawFile) value;
                    return "3\t" + file.getToken() + '\t' + file.getRoom().getToken() + '\t' + file.getAuthor().getToken() +
                            '\t' + file.getTimestamp() + '\t' + file.getName().replaceAll("\t", "\\t");
                }
                throw new IllegalArgumentException("unknown message type " + value.getClass().getSimpleName());
            }

            @Override
            protected String fromSerKey(String serKey) {
                return serKey;
            }

            @Override
            protected Message fromSerValue(String serValue) {
                validateStringNotBlank(serValue, "serialized message value");
                String[] fields = serValue.split("\t");
                if (fields.length < 5) throw new IllegalArgumentException("invalid serialized message " + serValue);
                int type = Integer.parseInt(fields[0]);
                Message message;
                switch (type) {
                    case 1:
                        if (fields.length < 6) throw new IllegalArgumentException("invalid serialized message " + serValue);
                        message = new Text(chatterRepository.retrieve(fields[3]), fields[1], Long.parseLong(fields[4]),
                                fields[5].replaceAll("\\t", "\t"));
                        break;
                    case 2:
                        if (fields.length < 8) throw new IllegalArgumentException("invalid serialized image " + serValue);
                        message = new Image(chatterRepository.retrieve(fields[3]), fields[1], Long.parseLong(fields[4]),
                                fields[5].replaceAll("\\t", "\t"), Integer.parseInt(fields[6]),
                                Integer.parseInt(fields[7]));
                        break;
                    case 3:
                        if (fields.length < 6) throw new IllegalArgumentException("invalid serialized file " + serValue);
                        message = new RawFile(chatterRepository.retrieve(fields[3]), fields[1], Long.parseLong(fields[4]),
                                fields[5].replaceAll("\\t", "\t"));
                        break;
                    default:
                        throw new IllegalArgumentException("unknown message id " + type);
                }
                Room room = roomRepository.retrieve(fields[2]);
                room.getMessages().add(message);
                room.getMessages().sort(Comparator.comparing(Message::getTimestamp));
                message.setRoom(room);
                return message;
            }
        };
    }

}

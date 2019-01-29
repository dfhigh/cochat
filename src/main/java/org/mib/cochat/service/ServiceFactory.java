package org.mib.cochat.service;

import lombok.Getter;
import org.mib.cochat.repo.InMemoryRepositories;
import org.mib.cochat.repo.InMemoryRepository;
import org.mib.cochat.repo.InMemoryWithFSPersistenceRepositories;
import org.mib.cochat.repo.PersistStrategy;
import org.mib.cochat.repo.Repositories;
import org.mib.common.config.ConfigProvider;

@Getter
public class ServiceFactory {

    private static volatile ServiceFactory INSTANCE = null;

    private final RoomService roomService;
    private final MessageService messageService;
    private final ChatterService chatterService;
    private final FileService fileService;

    private ServiceFactory() {
        final boolean isWebSocketEnabled = ConfigProvider.getBoolean("web_socket_enabled");
        Repositories repositories;
        if (ConfigProvider.getBoolean("persist_enabled")) {
            String persistDir = ConfigProvider.get("persist_dir");
            PersistStrategy strategy = new PersistStrategy(
                    ConfigProvider.getBoolean("compress_enabled"),
                    ConfigProvider.getInt("max_edits_per_persist"),
                    ConfigProvider.getInt("persist_interval_seconds")
            );
            repositories = new InMemoryWithFSPersistenceRepositories(
                    persistDir + "/chatter.cochat", strategy,
                    persistDir + "/room.cochat", strategy,
                    persistDir + "/message.cochat", strategy
            );
        } else {
            repositories = new InMemoryRepositories();
        }
        this.fileService = new FileService(ConfigProvider.get("file_store_path"));
        this.messageService = new MessageService(repositories.getMessageRepository(), fileService);
        this.roomService = new RoomService(repositories.getRoomRepository(), messageService, isWebSocketEnabled);
        this.chatterService = new ChatterService(repositories.getChatterRepository(), isWebSocketEnabled);
    }

    public static ServiceFactory getInstance() {
        if (INSTANCE == null) {
            synchronized (ServiceFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ServiceFactory();
                }
            }
        }
        return INSTANCE;
    }
}

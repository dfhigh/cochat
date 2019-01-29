package org.mib.cochat.rest;

import com.networknt.server.Server;
import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;
import org.mib.common.config.ConfigProvider;

@Slf4j
public class CochatApp {

    public static void main(String[] args) {
        // API web server
        Server.main(args);

        // web socket server
        if (ConfigProvider.getBoolean("web_socket_enabled")) {
            int port = ConfigProvider.getInt("web_socket_port");
            String addr = ConfigProvider.get("web_socket_addr");
            Undertow ws = Undertow.builder()
                    .addHttpListener(port, addr, new CochatWebSocketHandlerProvider().getHandler())
                    .build();
            ws.start();
            log.info("started web socket server on {}:{}", addr, port);

            Runtime.getRuntime().addShutdownHook(new Thread(ws::stop));
        }
    }
}

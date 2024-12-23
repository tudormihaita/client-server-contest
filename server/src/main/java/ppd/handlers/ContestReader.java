package ppd.handlers;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

@AllArgsConstructor
public class ContestReader implements Runnable {
    private final Socket clientSocket;

    private static final Logger log = LogManager.getLogger(ContestReader.class);

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            // TODO: implement request handling logic

        } catch (IOException e) {
            log.error(e);
        }
    }
}

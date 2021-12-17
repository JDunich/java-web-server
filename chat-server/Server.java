import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Server {

    private static Socket client;

    public static void main( String[] args ) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Server <port number>");
            System.exit(1);
        }
        //create server socket given port number
        int portNumber = Integer.parseInt(args[0]);
        System.out.println("Listening to port number " + portNumber + "...");
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                try (Socket tempClient = serverSocket.accept()) {
                    client = tempClient;
                    handleClient(client);
                }
            }
        }
    }

    private static void handleClient(Socket client) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
        StringBuilder payloadBuilder = new StringBuilder();
        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while(!(line= br.readLine()).isBlank())
            requestBuilder.append(line).append("\r\n");

        String[] inputHeaders = requestBuilder.toString().split("\r\n");
        String[] methodPath = inputHeaders[0].split(" ");
        String method = methodPath[0];
        String path = methodPath[1];
        String version = inputHeaders[2];
        String host = inputHeaders[1].split(" ")[1];
        String referer = inputHeaders[11];
        systemFormat(method, path, version, host, referer);

        while(br.ready())
            payloadBuilder.append((char) br.read());

        String payload = payloadBuilder.toString();
        System.out.println(payload);
        if(payload.isEmpty())
            loginHTTP();
        else
            requestHTTP(method, payload, path);
    }

    private static void sendResponse(String status, String contentType, byte[] content) throws IOException {
        OutputStream clientStream = client.getOutputStream();
        clientStream.write(("HTTP/1.1 200 OK" + status + "\r\n").getBytes());
        clientStream.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientStream.write("\r\n".getBytes());
        clientStream.write(content);
        clientStream.flush();
        client.close();
    }

    private static void loginHTTP() throws IOException {
        System.out.println("Retrieving login screen...");
        Path loginHTML = Paths.get("login/login.html");
        sendResponse("200 OK", guessContentType(loginHTML), Files.readAllBytes(loginHTML));
    }

    private static void requestHTTP(String method, String payload, String Path){

    }

    private static void systemFormat(String method, String path, String version, String host, String referer){
        System.out.println("Method: " + method);
        System.out.println("Path: " + path);
        System.out.println(version);
        System.out.println(host);
        System.out.println(referer);
    }

    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }
}


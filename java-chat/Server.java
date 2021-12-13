import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class Server {

    private static final ArrayList<String> cookies = new ArrayList<>();
    private static final ArrayList<String> messages = new ArrayList<>();
    private static String userData;
    private static String temp;
    private static String chat;

    public static void main( String[] args ) throws Exception {
        if (args.length != 1)
        {
            System.err.println("Usage: java Server <port number>");
            System.exit(1);
        }
        //create server socket given port number
        int portNumber = Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                }
            }
        }
    }
    private static void handleClient(Socket client) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
        StringBuilder requestBuilder = new StringBuilder();
        while(br.ready()){
            char c = (char)br.read();
            requestBuilder.append(c);
        }

        String request = requestBuilder.toString();
        System.out.printf("The request is: %s \n", request);
        String[] requestsLines = request.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];
        String host = requestsLines[1].split(" ")[1]; // build the response here
        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) {
            String header = requestsLines[h];
            headers.add(header);
        }
        String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s", client.toString(), method, path, version, host, headers.toString());
        System.out.println(accessLog);

        if(method.equals("GET")){
            if(path.contains("login")){
                path += "login.html";
            }else if(path.contains("chat")){
                path += "chat.html";
                chat = temp;
            }
        }else if(method.equals("POST")){
            if(path.contains("login")){
                path += "login.html";
                String rawLogin = headers.get(7).replace("username=", "").replace("&password=", " ");
                String[] userPass = rawLogin.split(" ");
                String user = userPass[0];
                String pass = userPass[1];
                System.out.println("User Login information: username-> " + user + " password-> " + pass);
                if(!validLogin(user, pass))
                    System.out.println("INVALID CREDENTIALS!");
                else
                    System.out.println("Start Chatting");
                if(!cookies.contains(user)){
                    cookies.add(user);
                    String userCookie = user + "_" + ((int) (Math.random() * 100000));
                    appendCookie(userCookie, user);
                    userData = userCookie;
                    headers.add(userCookie);
                }
            }else if(path.contains("chat")) {
                path += "chat.html";
                if (!validCookie(headers.get(4).replace("Cookie: cookie_id=", ""))) {
                    System.out.println("INVALID COOKIE!");
                } else {
                    System.out.println("Reading HTML Chat...");
                    temp = postHTML(headers.get(4).replace("Cookie: cookie_id=", ""), headers.get(8).replace("message=", ""));
                }
            }
        }
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            byte[] content = Files.readAllBytes(filePath);
            String contentType = guessContentType(filePath);
            if(method.equals("GET") && path.contains("chat"))
                content = chat.getBytes();
            sendResponse(client, "200 OK", contentType, content);
        } else {
            // 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent);
        }
    }
    private static void sendResponse(Socket client, String status, String contentType, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 200 OK" + status + "\r\n").getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        if(!(userData == null))
            clientOutput.write(("Set-Cookie: " + userData + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.write("\r\n\r\n".getBytes());

        clientOutput.flush();
        client.close();
    }
    private static Path getFilePath(String path) {
        if ("/".equals(path)) {
            path = "/index.html";
        }
        return Paths.get("./", path);
    }
    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }

    private static boolean validLogin(String user, String pass) throws IOException {
        System.out.println(user + " " + pass + "---------");
        String file ="login/credentials.txt";

        BufferedReader reader = new BufferedReader(new FileReader(file));
        while(reader.ready()){
            String test = reader.readLine();
            String[] userPass = test.split(",");
            if(userPass[0].equals(user) && userPass[1].equals(pass))
                return true;
        }
        return false;
    }

    private static boolean validCookie(String cookie) throws IOException {
        String file = "allCookies.txt";
        BufferedReader reader = new BufferedReader(new FileReader(file));
        while(reader.ready()){
            String line = reader.readLine();
            if(line.contains(cookie))
                return true;
        }
        return false;
    }

    private static void appendCookie(String cookie, String user) throws IOException {
        String file ="allCookies.txt";
        FileWriter writer = new FileWriter(file, true);
        writer.write(user + "," + cookie + "\n");
        writer.close();
    }

    private static String postHTML(String cookie, String message) throws IOException {
        String user = findUser(cookie);
        messages.add(user + ":" + message);
        File file = new File("chat/chat.html");
        StringBuilder builder = new StringBuilder();
        Scanner reader = new Scanner(file);
        reader.useDelimiter("\\A");
        while (reader.hasNextLine()){
            String line = reader.nextLine();
            builder.append(line).append("\n");
            if(line.contains("chat-window")){
                for(String string : messages)
                    builder.append("<p>").append(string).append("</p>").append("\n");
            }
        }
        /*
        FileWriter writer = new FileWriter(file, true);
        writer.write(String.valueOf(builder));
        writer.close();
         */
        String fileString ="allMessages.txt";
        FileWriter writer = new FileWriter(fileString, true);
        writer.write(user + ":" + message +  "\n");
        writer.close();
        return String.valueOf(builder);
    }

    private static String findUser(String cookie) throws IOException {
        String file = "allCookies.txt";
        String user = "";
        BufferedReader reader = new BufferedReader((new FileReader(file)));
        while(reader.ready()){
            String line = reader.readLine();
            if(line.contains(cookie))
                user = line.replace("," + cookie, "");
        }
        return user;
    }


}

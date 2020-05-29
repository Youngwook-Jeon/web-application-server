package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = bufferedReader.readLine();
            String[] tokens = line.split(" ");
            String url = tokens[1];

            log.debug("request line : {}", line);
            int length = 0;
            boolean logined = false;
            while (!"".equals(line)) {
                if (line.startsWith("Content-Length")) {
                    length = Integer.parseInt(line.split(":")[1].trim());
                }
                if (line.startsWith("Cookie")) {
                    String token = line.split(":")[1];
                    String st = HttpRequestUtils.parseCookies(token.trim()).get("logined");
                    if (st != null) logined = Boolean.parseBoolean(st);
                }
                line = bufferedReader.readLine();
            }

            if (url.startsWith("/user/create")) {
                String data = IOUtils.readData(bufferedReader, length);
                Map<String, String> params = HttpRequestUtils.parseQueryString(data);
                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                DataBase.addUser(user);
                log.debug("User : {}", user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
            } else if (url.startsWith("/user/login")) {
                String data = IOUtils.readData(bufferedReader, length);
                Map<String, String> params = HttpRequestUtils.parseQueryString(data);
                User user = DataBase.findUserById(params.get("userId"));
                if (user == null) {
                    response(out, "/user/login_failed.html");
                    return;
                } else if (user.getPassword().equals(params.get("password"))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    responseLoginSuccessHeader(dos);
                } else {
                    response(out, "/user/login_failed.html");
                    return;
                }
            } else if (url.startsWith("/user/list")) {
                if (logined == false) {
                    response(out, "/user/login.html");
                    return;
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("<table>");
                for (User user : DataBase.findAll()) {
                    stringBuilder.append("<tr>");
                    stringBuilder.append("<td>" + user.getName() + "</td>");
                    stringBuilder.append("<td>" + user.getUserId() + "</td>");
                    stringBuilder.append("<td>" + user.getEmail() + "</td>");
                    stringBuilder.append("</tr>");
                }
                stringBuilder.append("</table>");
                byte[] body = stringBuilder.toString().getBytes();
                DataOutputStream dos = new DataOutputStream(out);
                response200Header(dos, body.length);
                responseBody(dos, body);
            } else if (url.startsWith("/css")) {
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                response200CssHeader(dos, body.length);
                responseBody(dos, body);
            } else {
                response(out, url);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response(OutputStream out, String url) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void responseLoginSuccessHeader(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Set-Cookie: logined=true \r\n");
            dos.writeBytes("Location: /index.html \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + url + " \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css \r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}

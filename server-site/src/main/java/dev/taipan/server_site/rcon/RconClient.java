package dev.taipan.server_site.rcon;

import dev.taipan.server_site.config.RconProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Минимальный клиент Source RCON (протокол Minecraft RCON) поверх TCP.
 * Без внешних зависимостей. Каждый вызов {@link #execute} открывает короткое соединение,
 * аутентифицируется и отправляет одну команду — этого достаточно для разовой выдачи VIP.
 */
@Component
public class RconClient {

    private static final int TYPE_AUTH = 3;
    private static final int TYPE_COMMAND = 2;
    private static final int AUTH_FAILED_ID = -1;

    private final RconProperties props;

    public RconClient(RconProperties props) {
        this.props = props;
    }

    /**
     * Выполняет команду на сервере. Бросает {@link RconException} при любой ошибке
     * (сеть, неверный пароль) — вызывающий код решает, ретраить или нет.
     */
    public String execute(String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(props.getHost(), props.getPort()), props.getConnectTimeoutMs());
            socket.setSoTimeout(props.getReadTimeoutMs());

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 1) Аутентификация.
            int authId = 1;
            sendPacket(out, authId, TYPE_AUTH, props.getPassword());
            Packet authResp = readPacket(in);
            if (authResp.id() == AUTH_FAILED_ID) {
                throw new RconException("RCON auth failed (неверный пароль)");
            }

            // 2) Команда.
            int cmdId = 2;
            sendPacket(out, cmdId, TYPE_COMMAND, command);
            Packet cmdResp = readPacket(in);
            return cmdResp.body();
        } catch (RconException e) {
            throw e;
        } catch (IOException e) {
            throw new RconException("RCON I/O error: " + e.getMessage(), e);
        }
    }

    private static void sendPacket(OutputStream out, int id, int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + bodyBytes.length + 2; // id + type + body + 2 null bytes

        byte[] packet = new byte[4 + length];
        int i = 0;
        i = writeLeInt(packet, i, length);
        i = writeLeInt(packet, i, id);
        i = writeLeInt(packet, i, type);
        System.arraycopy(bodyBytes, 0, packet, i, bodyBytes.length);
        // последние два байта остаются нулевыми

        out.write(packet);
        out.flush();
    }

    private static Packet readPacket(InputStream in) throws IOException {
        int length = readLeInt(in);
        if (length < 10) {
            throw new RconException("RCON: некорректная длина пакета: " + length);
        }
        byte[] payload = readN(in, length);

        int id = leInt(payload, 0);
        int type = leInt(payload, 4);
        // тело: от 8 до length-2 (исключая два завершающих нуля)
        int bodyLen = Math.max(0, length - 10);
        String body = new String(payload, 8, bodyLen, StandardCharsets.UTF_8);
        return new Packet(id, type, body);
    }

    private static int writeLeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
        return offset + 4;
    }

    private static int leInt(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static int readLeInt(InputStream in) throws IOException {
        byte[] b = readN(in, 4);
        return leInt(b, 0);
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) {
                throw new RconException("RCON: соединение закрыто до получения " + n + " байт");
            }
            read += r;
        }
        return buf;
    }

    private record Packet(int id, int type, String body) {}
}

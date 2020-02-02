package org.openhab.binding.kefls50wireless.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.core.audio.AudioFormat;
import org.eclipse.smarthome.core.audio.AudioHTTPServer;
import org.eclipse.smarthome.core.audio.AudioStream;
import org.eclipse.smarthome.core.audio.FixedLengthAudioStream;
import org.eclipse.smarthome.core.audio.UnsupportedAudioFormatException;
import org.eclipse.smarthome.core.audio.UnsupportedAudioStreamException;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KefLS50Client {

    private final Logger logger = LoggerFactory.getLogger(KefLS50Client.class);

    private static final int KEF_PORT = 50001;
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final int LINGER_TIMEOUT = 5000;
    private InetSocketAddress addr = null;
    private float volBackup = 0.0f;
    private long lastConnect = System.currentTimeMillis();

    private static String DLNA_SETUP_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"";
    private static String DLNA_SETUP = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "      <CurrentURI>%s</CurrentURI>\n"
            + "      <CurrentURIMetaData></CurrentURIMetaData>\n" + "    </u:SetAVTransportURI>\n" + "  </s:Body>\n"
            + "</s:Envelope>\n";

    private static String DLNA_PLAY_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"";
    private static String DLNA_PLAY = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "      <Speed>1</Speed>\n" + "    </u:Play>\n" + "  </s:Body>\n"
            + "</s:Envelope>\n";

    private static String DLNA_PAUSE_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"";
    private static String DLNA_PAUSE = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:Pause xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:Pause>\n" + "  </s:Body>\n" + "</s:Envelope>\n";

    private static String DLNA_STOP_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"";
    private static String DLNA_STOP = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:Stop xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:Stop>\n" + "  </s:Body>\n" + "</s:Envelope>\n";

    private static String DLNA_NEXT_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#Next\"";
    private static String DLNA_NEXT = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:Next xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:Next>\n" + "  </s:Body>\n" + "</s:Envelope>\n";

    private static String DLNA_PREVIOUS_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#Previous\"";
    private static String DLNA_PREVIOUS = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:Previous xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:Previous>\n" + "  </s:Body>\n" + "</s:Envelope>\n";

    private static String DLNA_GETMEDIA_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#GetMediaInfo\"";
    private static String DLNA_GETMEDIA = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:GetMediaInfo xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:GetMediaInfo>\n" + "  </s:Body>\n" + "</s:Envelope>\n";

    private static String DLNA_GETTRANSPORT_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"";
    private static String DLNA_GETTRANSPORT = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "  <s:Body>\n" + "    <u:GetTransportInfo xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
            + "      <InstanceID>0</InstanceID>\n" + "    </u:GetTransportInfo>\n" + "  </s:Body>\n"
            + "</s:Envelope>\n";

    public enum Input {
        UNKNOWN,
        NETWORK,
        BLUETOOTH,
        BLUETOOTH_NC, // Not connected bluetooth
        AUX,
        OPTICAL,
        USB
    }

    public enum TransportState {
        UNKNOWN,
        PLAYING,
        STOPPED,
        TRANSITIONING,
        RECORDING,
        PAUSED_RECORDING,
        PAUSED_PLAYBACK,
        NO_MEDIA_PRESENT
    }

    public KefLS50Client(String host) {

        addr = new InetSocketAddress(host, KEF_PORT);
    }

    public boolean isAvailable() {

        try {
            writeRaw(new byte[0]);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public float getVol() throws IOException {

        float vol = 0.0f;
        String data = byteArrayToHexString(writeReadRaw(hexStringToByteArray("472580")));
        if (data != null && data.startsWith("522581")) {
            int kefVol = Integer.parseInt(data.substring(6, 8), 16);
            if (kefVol <= 127 && kefVol >= 0) {
                vol = (float) kefVol / 127 * 100;
            }
        }
        return vol;
    }

    public void setVol(float vol) throws IOException {

        float clampedVol = vol;
        if (clampedVol < 0) {
            clampedVol = 0;
        }
        if (clampedVol > 100) {
            clampedVol = 100;
        }

        int kefVol = (int) (clampedVol / 100 * 127 + .5);

        writeRaw(hexStringToByteArray("532581" + String.format("%02X", kefVol)));
    }

    public boolean isMuted() throws IOException {

        boolean muted = false;
        String data = byteArrayToHexString(writeReadRaw(hexStringToByteArray("472580")));
        if (data != null && data.startsWith("522581")) {
            int kefVol = Integer.parseInt(data.substring(6, 8), 16);
            if (kefVol > 127 || kefVol < 0) {
                muted = true;
            }
        }
        return muted;
    }

    public void setMuted(boolean mute) throws IOException {

        if (mute) {
            volBackup = getVol();
            writeRaw(hexStringToByteArray("532581" + String.format("%02X", 128)));
        } else {
            float oldVol = volBackup;
            volBackup = 0.0f;
            setVol(oldVol);
        }
    }

    public Input getInput() throws IOException {

        Input intput = Input.UNKNOWN;
        String data = byteArrayToHexString(writeReadRaw(hexStringToByteArray("473080")));
        if (data != null && data.startsWith("523081")) {
            String input = data.substring(6, 8);
            switch (input) {
                case "12":
                    intput = Input.NETWORK;
                    break;
                case "1C":
                    intput = Input.USB;
                    break;
                case "1F":
                    intput = Input.BLUETOOTH_NC;
                    break;
                case "19":
                    intput = Input.BLUETOOTH;
                    break;
                case "1A":
                    intput = Input.AUX;
                    break;
                case "1B":
                    intput = Input.OPTICAL;
                    break;
                default:
                    intput = Input.UNKNOWN;
                    break;
            }
        }
        return intput;
    }

    public void setInput(Input input) throws IOException {

        switch (input) {
            case NETWORK:
                writeRaw(hexStringToByteArray("53308112"));
                break;
            case BLUETOOTH:
            case BLUETOOTH_NC:
                writeRaw(hexStringToByteArray("53308119"));
                break;
            case AUX:
                writeRaw(hexStringToByteArray("5330811A"));
                break;
            case OPTICAL:
                writeRaw(hexStringToByteArray("5330811B"));
                break;
            case USB:
                writeRaw(hexStringToByteArray("5330811C"));
                break;
            case UNKNOWN:
            default:
                // Nothing to do here
                break;
        }
    }

    public synchronized boolean stop() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_STOP_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_STOP.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            // Error code 701 probably means we are not in a playback state
            if (!isSoapError(response) || getSoapErrorCode(response) == 701) {
                return true;
            } else {
                logger.warn("Got soap error (stop action): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (stop action): \"" + ctrlUrl + "\"");
        }
        return false;
    }

    public synchronized boolean pause() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_PAUSE_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_PAUSE.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return true;
            } else {
                logger.warn("Got soap error (pause action): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (pause action): \"" + ctrlUrl + "\"");
        }
        return false;
    }

    public synchronized boolean next() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_NEXT_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_NEXT.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return true;
            } else {
                logger.warn("Got soap error (next action): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (next action): \"" + ctrlUrl + "\"");
        }
        return false;
    }

    public synchronized boolean previous() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_PREVIOUS_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_PREVIOUS.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return true;
            } else {
                logger.warn("Got soap error (previous action): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (previous action): \"" + ctrlUrl + "\"");
        }
        return false;
    }

    public synchronized boolean play() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_PLAY_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_PLAY.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return true;
            } else {
                logger.warn("Got soap error (play action): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (play action): \"" + ctrlUrl + "\"");
        }
        return false;
    }

    public synchronized void play(AudioStream audioStream, AudioHTTPServer audioHTTPServer, String baseUrl)
            throws UnsupportedAudioFormatException, UnsupportedAudioStreamException, IOException {

        if (audioHTTPServer == null) {
            logger.warn("Missing Audio HTTP server. Couldn't serve audio stream.");
            return;
        }

        if (audioStream == null) {
            logger.warn("Missing audio stream.");
            return;
        }

        String container = audioStream.getFormat().getContainer();
        String codec = audioStream.getFormat().getCodec();
        String fileEnding = "";

        if (container == null || codec == null) {
            throw new UnsupportedAudioFormatException("Audio format not supported. Got null.", audioStream.getFormat());
        }

        switch (container) {
            case AudioFormat.CONTAINER_WAVE:
                fileEnding = "wav";
                break;
            case AudioFormat.CONTAINER_OGG:
                fileEnding = "ogg";
                break;
            case AudioFormat.CONTAINER_NONE:
            default:
                switch (codec) {
                    case AudioFormat.CODEC_MP3:
                        fileEnding = "mp3";
                        break;
                    case AudioFormat.CODEC_AAC:
                        fileEnding = "aac";
                        break;
                    default:
                        throw new UnsupportedAudioFormatException("Audio format not supported",
                                audioStream.getFormat());
                }
                break;
        }

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        if (stop()) {
            String relativeUrl;
            if (audioStream instanceof FixedLengthAudioStream) {
                relativeUrl = audioHTTPServer.serve((FixedLengthAudioStream) audioStream, 60);
            } else {
                relativeUrl = audioHTTPServer.serve(audioStream);
            }
            String servingUrl = (baseUrl != null ? baseUrl : "") + relativeUrl + "." + fileEnding;

            Properties header = new Properties();
            header.setProperty("accept_encoding", "identity");
            header.setProperty("Soapaction", DLNA_SETUP_ACTION);
            String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                    new ByteArrayInputStream(
                            String.format(DLNA_SETUP, servingUrl.toString()).getBytes(StandardCharsets.UTF_8)),
                    "text/xml; charset=\"utf-8\"", CONNECTION_TIMEOUT);

            if (response != null) {
                if (!isSoapError(response)) {
                    if (play()) {
                        logger.debug("Started playback of file \"" + servingUrl + "\"");
                    } else {
                        logger.warn("Play action failed");
                    }
                } else {
                    logger.warn("Got soap error (setup action): \"" + getSoapError(response) + "\"");
                }
            } else {
                logger.warn("Couldn't get a dlna response from endpoint (setup action): \"" + ctrlUrl + "\"");
            }
        } else {
            logger.warn("Stop action failed");
        }
    }

    public synchronized TransportState getTransportState() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_GETTRANSPORT_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_GETTRANSPORT.getBytes(StandardCharsets.UTF_8)),
                "text/xml; charset=\"utf-8\"", CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return parseState(response);
            } else {
                logger.warn("Got soap error (get transport info): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (get transport info): \"" + ctrlUrl + "\"");
        }
        return TransportState.UNKNOWN;
    }

    public synchronized MediaInfo getMediaInfo() throws IOException {

        String ctrlUrl = "http://" + addr.getHostString() + ":8080/AVTransport/ctrl";

        Properties header = new Properties();
        header.setProperty("accept_encoding", "identity");
        header.setProperty("Soapaction", DLNA_GETMEDIA_ACTION);
        String response = HttpUtil.executeUrl("POST", ctrlUrl, header,
                new ByteArrayInputStream(DLNA_GETMEDIA.getBytes(StandardCharsets.UTF_8)), "text/xml; charset=\"utf-8\"",
                CONNECTION_TIMEOUT);

        if (response != null) {
            if (!isSoapError(response)) {
                return MediaInfo.fromXml(response);
            } else {
                logger.warn("Got soap error (get media info): \"" + getSoapError(response) + "\"");
            }
        } else {
            logger.warn("Couldn't get a dlna response from endpoint (get media info): \"" + ctrlUrl + "\"");
        }
        return null;
    }

    private synchronized void writeRaw(byte[] data) throws IOException {

        try (Socket sock = new Socket()) {
            sock.setKeepAlive(false);
            sock.setSoLinger(true, LINGER_TIMEOUT);
            long connectDiff = System.currentTimeMillis() - lastConnect;
            if (connectDiff < 100) {
                Thread.sleep(100 - connectDiff);
            }
            sock.connect(addr, CONNECTION_TIMEOUT);
            if (data.length > 0) {
                OutputStream ostream = sock.getOutputStream();
                ostream.write(data);
                ostream.flush();
            }
        } catch (SocketTimeoutException e) {
            logger.warn("Connection timeout while connecting to LS50 host" + addr);
            throw e;
        } catch (IOException e) {
            logger.warn("Could't write to LS50 host" + addr);
            throw e;
        } catch (InterruptedException e) {
            // Nothing to do here
        }
        lastConnect = System.currentTimeMillis();
    }

    private synchronized byte[] writeReadRaw(byte[] data) throws IOException {

        byte[] ret = new byte[8];
        try (Socket sock = new Socket()) {
            sock.setKeepAlive(false);
            sock.setSoTimeout(READ_TIMEOUT);
            sock.setSoLinger(true, LINGER_TIMEOUT);
            long connectDiff = System.currentTimeMillis() - lastConnect;
            if (connectDiff < 100) {
                Thread.sleep(connectDiff);
            }
            sock.connect(addr, CONNECTION_TIMEOUT);
            if (data.length > 0) {
                InputStream istream = sock.getInputStream();
                OutputStream ostream = sock.getOutputStream();
                ostream.write(data);
                ostream.flush();
                istream.read(ret);
            }
        } catch (SocketTimeoutException e) {
            logger.warn("Connection timeout while connecting to LS50 host" + addr);
            throw e;
        } catch (IOException e) {
            logger.warn("Could't write/read to/from LS50 host" + addr);
            throw e;
        } catch (InterruptedException e) {
            // Nothing to do here
        }
        lastConnect = System.currentTimeMillis();
        return ret;
    }

    private Socket socket = null;
    private Timer timer = new Timer();

    private synchronized Socket getSocket(InetSocketAddress addr) throws IOException {

        if (socket == null) {
            socket.setKeepAlive(false);
            socket.setSoTimeout(READ_TIMEOUT);
            socket.setSoLinger(true, LINGER_TIMEOUT);
            socket.connect(addr, CONNECTION_TIMEOUT);
        }

        return socket;
    }

    private synchronized void releaseSocket() {

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1000);
    }

    private static boolean isSoapError(String response) {
        if (response != null) {
            Pattern pattern = Pattern.compile("<[A-Za-z]*:?Fault>");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private static TransportState parseState(String response) {

        if (response != null) {
            Pattern pattern = Pattern.compile("<CurrentTransportState>[\\s\\S]*?<\\/CurrentTransportState>");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                String group = matcher.group();
                if (group != null) {
                    group = group.replace("<CurrentTransportState>", "");
                    return TransportState.valueOf(group.replace("</CurrentTransportState>", "").toUpperCase());
                }
            }
        }
        return TransportState.UNKNOWN;
    }

    private static String getSoapError(String response) {
        if (isSoapError(response)) {
            Pattern pattern = Pattern.compile("<errorDescription>[\\s\\S]*?<\\/errorDescription>");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                String group = matcher.group();
                if (group != null) {
                    group = group.replace("<errorDescription>", "");
                    return group.replace("</errorDescription>", "");
                }
            }
            return "Unknown error";
        }
        return null;
    }

    private static int getSoapErrorCode(String response) {
        if (isSoapError(response)) {
            Pattern pattern = Pattern.compile("<errorCode>[\\s\\S]*?<\\/errorCode>");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                String group = matcher.group();
                if (group != null) {
                    group = group.replace("<errorCode>", "");
                    return Integer.parseInt(group.replace("</errorCode>", ""), 10);
                }
            }
            return -1;
        }
        return 0;
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String byteArrayToHexString(byte[] bytes) {

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
}

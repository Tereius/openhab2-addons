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
import org.eclipse.smarthome.core.audio.AudioHTTPServer;
import org.eclipse.smarthome.core.audio.AudioStream;
import org.eclipse.smarthome.core.audio.FixedLengthAudioStream;
import org.eclipse.smarthome.core.audio.URLAudioStream;
import org.eclipse.smarthome.core.audio.internal.AudioServlet;
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
	private AudioHTTPServer audioHTTPServer = null;

	private static String DLNA_START = "<?xml\n" + "        version='1.0'\n" + "        encoding='utf-8'\n"
			+ "        ?>\n" + "    <s:Envelope\n"
			+ "        s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"\n"
			+ "        xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" + "        <s:Body>\n"
			+ "            <u:SetAVTransportURI\n"
			+ "                xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
			+ "                <InstanceID>\n" + "                    0\n" + "                    </InstanceID>\n"
			+ "                <CurrentURI>\n" + "                    %s\n" + "                    </CurrentURI>\n"
			+ "                <CurrentURIMetaData>\n" + "                    </CurrentURIMetaData>\n"
			+ "                </u:SetAVTransportURI>\n" + "            </s:Body>\n" + "        </s:Envelope>";

	private static String DLNA_PLAY = "<?xml\n" + "        version='1.0'\n" + "        encoding='utf-8'\n"
			+ "        ?>\n" + "    <s:Envelope\n"
			+ "        s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"\n"
			+ "        xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" + "        <s:Body>\n"
			+ "            <u:Play\n" + "                xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
			+ "                <InstanceID>\n" + "                    0\n" + "                    </InstanceID>\n"
			+ "                <Speed>\n" + "                    1\n" + "                    </Speed>\n"
			+ "                </u:Play>\n" + "            </s:Body>\n" + "        </s:Envelope>";

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
		if (data.startsWith("522581")) {
			int kefVol = Integer.parseInt(data.substring(6, 8), 16);
			if (kefVol <= 127 && kefVol >= 0)
				vol = (float) kefVol / 127 * 100;
		}
		return vol;
	}

	public void setVol(float vol) throws IOException {

		if (vol < 0)
			vol = 0;
		if (vol > 100)
			vol = 100;

		int kefVol = (int) (vol / 100 * 127 + .5);

		writeRaw(hexStringToByteArray("532581" + String.format("%02X", kefVol)));
	}

	public boolean isMuted() throws IOException {

		boolean muted = false;
		String data = byteArrayToHexString(writeReadRaw(hexStringToByteArray("472580")));
		if (data.startsWith("522581")) {
			int kefVol = Integer.parseInt(data.substring(6, 8), 16);
			if (kefVol > 127 || kefVol < 0)
				muted = true;
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

	public synchronized void play(AudioStream audioStream) throws IOException {

		String url;
		if (audioStream instanceof URLAudioStream) {
			// it is an external URL, the speaker can access it itself and play it.
			URLAudioStream urlAudioStream = (URLAudioStream) audioStream;
			url = urlAudioStream.getURL();
		} else {
			if (audioHTTPServer == null)
				audioHTTPServer = new AudioServlet();
			// we serve it on our own HTTP server
			String relativeUrl;
			if (audioStream instanceof FixedLengthAudioStream) {
				relativeUrl = audioHTTPServer.serve((FixedLengthAudioStream) audioStream, 10);
			} else {
				relativeUrl = audioHTTPServer.serve(audioStream);
			}
			url = relativeUrl;
		}

		Properties header = new Properties();
		header.setProperty("accept_encoding", "identity");
		// header.setProperty("content_type", "text/xml; charset=\"utf-8\"");
		header.setProperty("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"");
		InputStream stream = new ByteArrayInputStream(
				String.format(DLNA_START, url.toString()).getBytes(StandardCharsets.UTF_8));
		String response = HttpUtil.executeUrl("POST", addr.getHostName() + ":8080/AVTransport/ctrl", header, stream,
				"text/xml; charset=\"utf-8\"", CONNECTION_TIMEOUT);
		if (response != null) {
			header.setProperty("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"");
			InputStream streamPlay = new ByteArrayInputStream(DLNA_PLAY.getBytes(StandardCharsets.UTF_8));
			String responsePlay = HttpUtil.executeUrl("POST", addr.getHostName() + ":8080/AVTransport/ctrl", header,
					streamPlay, "text/xml; charset=\"utf-8\"", CONNECTION_TIMEOUT);
		}

//		URL url = new URL(addr.getHostName() + ":8080/AVTransport/ctrl");
//		HttpURLConnection con = (HttpURLConnection) url.openConnection();
//		con.setRequestMethod("POST");
//		con.setReadTimeout(READ_TIMEOUT);
//		con.setConnectTimeout(CONNECTION_TIMEOUT);
//		con.setRequestProperty("accept_encoding", "");
	}

	private synchronized void writeRaw(byte[] data) throws IOException {

		try (Socket sock = new Socket()) {
			sock.setKeepAlive(false);
			sock.setSoLinger(true, LINGER_TIMEOUT);
			long connectDiff = System.currentTimeMillis() - lastConnect;
			if (connectDiff < 100)
				Thread.sleep(100 - connectDiff);
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
			if (connectDiff < 100)
				Thread.sleep(connectDiff);
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

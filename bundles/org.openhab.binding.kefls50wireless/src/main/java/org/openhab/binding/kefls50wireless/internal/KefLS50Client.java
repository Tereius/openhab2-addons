package org.openhab.binding.kefls50wireless.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KefLS50Client {

	private final Logger logger = LoggerFactory.getLogger(KefLS50Client.class);

	private static final int KEF_PORT = 50001;
	private static final int CONNECTION_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 5000;
	private static final int LINGER_TIMEOUT = 5000;
	private SocketAddress addr = null;
	private float volBackup = 0.0f;
	private long lastConnect = System.currentTimeMillis();

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

	private synchronized void writeRaw(byte[] data) throws IOException {

		try (Socket sock = new Socket()) {
			sock.setKeepAlive(false);
			sock.setSoLinger(true, LINGER_TIMEOUT);
			long connectDiff = System.currentTimeMillis() - lastConnect;
			if(connectDiff < 100) Thread.sleep(100 - connectDiff);
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
			if(connectDiff < 100) Thread.sleep(connectDiff);
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

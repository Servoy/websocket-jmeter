package com.servoy.jmeter.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingParser {
	private final ArrayList<String> messagesQueue = new ArrayList<String>();
	// private static final HashMap<String, String[]> reqResValues = new
	// HashMap<String, String[]>();
	private static final String CMSGIDPATTERN = "\"cmsgid\":.";
	private static final String SMSGIDPATTERN = "\"smsgid\":.";
	private static final Pattern psmsgid = Pattern.compile(SMSGIDPATTERN);
	private static final Pattern pcmsgid = Pattern.compile(CMSGIDPATTERN);
	// private static final String keySign ="#@#";
	// private static final String valueSign ="##";

	public RecordingParser() {
		
	}

	/**
	 * This will read the target file as text. Values are taken as keys if the
	 * line starts with a hash at hash (#@#). The next line is taken as the
	 * value.
	 *
	 * For example: #@#KeyName This is the value
	 *
	 * Will result in a mapping of "KeyName" with value "This is the value"
	 *
	 * @param file
	 * @throws IOException
	 */
	public void loadConfig(File file) throws IOException {
		if (file != null && file.exists()) {
			BufferedReader stream = null;
			try {

				String initialClientMessage = ">{\"msg\":{\"sessionid\":\"c6b2ff4f-2cb2-4b94-9352-cdcf9a5c4e4c\",\"windowid\":\"0c91ce7f-9a2b-49f8-a2d5-559f25be7bd4\"}}";
				messagesQueue.add(initialClientMessage);
				// Open the stream
				stream = new BufferedReader(new FileReader(file));

				String line;
				while ((line = stream.readLine()) != null) {
					if (checkIfSmsgsidIsPresent(line) || checkIfCmsgsidIsPresent(line) || line.startsWith(">")) {
						messagesQueue.add(line);
					}

				}
			} finally {
				try {
					if (stream != null)
						stream.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public ArrayList<String> getMessageQueue(File file) {
		try {
			loadConfig(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return messagesQueue;
	}

	public static boolean checkIfSmsgsidIsPresent(String message) {
		Matcher msmsgid = psmsgid.matcher(message);
		if (msmsgid.find()) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean checkIfCmsgsidIsPresent(String message) {
		Matcher msmsgid = pcmsgid.matcher(message);
		if (msmsgid.find()) {
			return true;
		} else {
			return false;
		}
	}
}
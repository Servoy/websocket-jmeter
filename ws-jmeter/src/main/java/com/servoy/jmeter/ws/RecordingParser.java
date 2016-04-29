package com.servoy.jmeter.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingParser {
	private final ArrayList<String> messagesQueue = new ArrayList<String>();
	// private static final HashMap<String, String[]> reqResValues = new
	// HashMap<String, String[]>();
	public static final String CMSGIDPATTERN = "\"cmsgid\":[0-9]{1,10}";
	public static final String SMSGIDPATTERN = "\"smsgid\":[0-9]{1,10}";
	public static final String DEFIDPATTERN = "\"defid\":([0-9]{1,10})";
	public static final String DEFID_RETURN_PATTERN = "\"resolveDeferedEvent\",\"args\":\\[([0-9]{1,10})";
	
	public static final Pattern PSMSGID = Pattern.compile(SMSGIDPATTERN);
	public static final Pattern PCMSGID = Pattern.compile(CMSGIDPATTERN);
	public static final Pattern PDEFID = Pattern.compile(DEFIDPATTERN);
	public static final Pattern PDEFID_RETIURN = Pattern.compile(DEFID_RETURN_PATTERN);
	// private static final String keySign ="#@#";
	// private static final String valueSign ="##";

	public RecordingParser() {
		// doc
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
				// Open the stream
				stream = new BufferedReader(new FileReader(file));

				String line;
				while ((line = stream.readLine()) != null) {
					if (line.startsWith(">") || checkIfSmsgsidIsPresent(line) || checkIfCmsgsidIsPresent(line) || checkIfDefidIsPresent(line)) {
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

	public List<String> getMessageQueue(File file) {
		try {
			loadConfig(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Collections.synchronizedList(messagesQueue);
	}

	public static boolean checkIfSmsgsidIsPresent(String message) {
		Matcher msmsgid = PSMSGID.matcher(message);
		if (msmsgid.find()) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean checkIfCmsgsidIsPresent(String message) {
		Matcher msmsgid = PCMSGID.matcher(message);
		if (msmsgid.find()) {
			return true;
		} else {
			return false;
		}
	}
	
	public static boolean checkIfDefidIsPresent(String message) {
		Matcher msmsgid = PDEFID.matcher(message);
		if (msmsgid.find()) {
			return true;
		} else {
			return PDEFID_RETIURN.matcher(message).find();
		}
	}
}

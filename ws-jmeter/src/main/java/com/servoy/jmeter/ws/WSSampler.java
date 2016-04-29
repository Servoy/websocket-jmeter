package com.servoy.jmeter.ws;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.glassfish.tyrus.client.ClientManager;

@ClientEndpoint
public class WSSampler extends AbstractJavaSamplerClient {

	private static final Logger logger = Logger.getLogger("WSSampler");

	private volatile String response_message;
	private volatile CountDownLatch latch;

	private final List<String> receivedServerIds = Collections.synchronizedList(new ArrayList<String>());
	private final Map<String, SampleResult> startedActions = Collections
			.synchronizedMap(new HashMap<String, SampleResult>());

	private String ws_uri;
	private List<String> reqRes;

	private SampleResult testResult;
	private boolean waitingForNextRun = false;
	private boolean firstTime = true;
	private volatile Session session;

	@Override
	public Arguments getDefaultParameters() {
		Arguments params = new Arguments();
		// ws://localhost:8080/websocket/null/null/null?solution=Test
		params.addArgument("URI", "http://localhost:8080/solutions/Test/index.html");
		params.addArgument("recording file", "recording.txt");
		return params;
	}

	@Override
	public void setupTest(JavaSamplerContext context) {
		ws_uri = context.getParameter("URI");
		if (ws_uri.startsWith("http")) {
			ws_uri = ws_uri.replace("http", "ws");

			int index = ws_uri.indexOf("/solutions/");
			int index2 = ws_uri.indexOf('/', index + 11);
			if (index == -1 || index2 == -1)
				throw new RuntimeException("Url is not a supported ngclient url: " + context.getParameter("URI"));
			ws_uri = ws_uri.substring(0, index) + "/websocket/null/null/null?solution="
					+ ws_uri.substring(index + 11, index2);
		}

		File testCaseFile = new File(context.getParameter("recording file"));
		reqRes = new RecordingParser().getMessageQueue(testCaseFile);

		// first sleep a bit before starting it up.
		ClientManager client = ClientManager.createClient();
		try {
			client.connectToServer(this, new URI(ws_uri));
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
		SampleResult sr = new SampleResult();
		synchronized (this) {
			testResult = sr;
			latch = new CountDownLatch(1);
		}
		if (reqRes.size() == 0) {
			sr.setStopThread(true);
			sr.setSuccessful(true);
			sr.setResponseMessage("socket closed");
			sr.setSampleLabel("socket closed");
			sr.setResponseCode("200");
			return sr;
		}
		SampleResult retValue = null;
		try {
			boolean processQueue = false;
			synchronized (this) {
				processQueue = waitingForNextRun;
				waitingForNextRun = false;
			}
			if (processQueue)
				processQueue(session);
			if (!latch.await(30, TimeUnit.SECONDS)) {
				synchronized (this) {
					sr.setSuccessful(false);
					sr.setSamplerData("time out");
					sr.setResponseMessage("time out");
					sr.setStopThread(true);
					return sr;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		synchronized (this) {
			sr.setSuccessful(true);
			sr.setResponseMessage(response_message);
			sr.setResponseCode("200");
			if (reqRes.size() == 0)
				sr.setStopThread(true);
			if (response_message != null) {
				sr.setResponseData(response_message.getBytes());
			}
		}
		return sr;
	}

	@OnOpen
	public void onOpen(Session session) {
		this.session = session;
		logger.info("Connected ... " + session.getId());
	}

	@OnMessage
	public String onMessage(String message, Session session) {
		// logger.info(session.getId() + ": Received ...." + message);
		// check and extract the cmsgid or smsgid from the server messages
		String receivedMsgKey = getMessageId(message);

		if (receivedMsgKey != null) {
			if (startedActions.containsKey(receivedMsgKey)) {
				SampleResult subResult = startedActions.remove(receivedMsgKey);
				subResult.setSuccessful(true);
				logger.severe("sub result : " + subResult.getSampleLabel());
				subResult.sampleEnd();
				testResult = null;
				latch.countDown();
			}
			receivedServerIds.add(receivedMsgKey);
			// logger.info(session.getId() + ": Received ...." + message);
			try {
				processQueue(session);
			} catch (Exception e) {
				logger.severe("Unexpected error: " + e);
				e.printStackTrace();
			}

		}
		if (firstTime) {
			try {
				processQueue(session);
			} catch (Exception e) {
				logger.severe("Unexpected error: " + e);
				e.printStackTrace();
			}
			firstTime = false;
		}
		return response_message; // TODO need to return some string here
	}

	// check if message has cmsgid or smsgid or defid parameter. if it does
	// extract and
	// return it, otherwise return null
	public static String getMessageId(String message) {
		String id = getServerMessageId(message);
		if (id == null) {
			id = getClientMessageId(message);
			if (id == null) {
				id = getDeferedMessageId(message);
			}
		}
		return id;
	}

	public static String getServerMessageId(String message) {

		Matcher msmsgid = RecordingParser.PSMSGID.matcher(message);
		if (msmsgid.find()) {
			return msmsgid.group(0);
		}
		return null;
	}

	public static String getClientMessageId(String message) {
		Matcher mcmsgid = RecordingParser.PCMSGID.matcher(message);
		if (mcmsgid.find()) {
			return mcmsgid.group(0);
		}
		return null;
	}

	public static String getDeferedMessageId(String message) {
		Matcher mcmsgid = RecordingParser.PDEFID.matcher(message);
		if (mcmsgid.find()) {
			return mcmsgid.group(1);
		} else {
			mcmsgid = RecordingParser.PDEFID_RETIURN.matcher(message);
			if (mcmsgid.find()) {
				return mcmsgid.group(1);
			}
		}
		return null;
	}

	public static String getClientMessage(String message) {
		if (message.startsWith(">")) {
			return message.substring(1);
		}
		return null;
	}

	public void processQueue(Session session) throws InterruptedException, IOException {
		Iterator<String> queueIterator = reqRes.iterator();
		while (queueIterator.hasNext()) {
			String message = queueIterator.next();
			String clientMessage = getClientMessage(message);
			if (clientMessage != null) {
				// logger.info("Sending message " + msg);
				String id = getServerMessageId(clientMessage);
				if (id != null) {
					if (receivedServerIds.contains(id)) {
						// logger.info(session.getId() + ": Sending server
						// request...." + clientMessage);
						session.getBasicRemote().sendText(clientMessage);
						receivedServerIds.remove(id);
					} else {
						// wait until it receives it
						return;
					}
				} else {
					String label = "action";
					String clientMessageId = null;
					if (clientMessage.contains("executeEvent")) {
						clientMessageId = getClientMessageId(message);
						if (clientMessageId != null) {
							int actionIndex = message.indexOf("\"formname\":\"");
							if (actionIndex > 0) {
								label = message.substring(actionIndex + 12, message.indexOf("\"", actionIndex + 12));
							}

							actionIndex = message.indexOf("\"beanname\":\"");
							if (actionIndex > 0) {
								label += ":"
										+ message.substring(actionIndex + 12, message.indexOf("\"", actionIndex + 12));
							}

							actionIndex = message.indexOf("\"event\":\"");
							if (actionIndex > 0) {
								label += ":"
										+ message.substring(actionIndex + 9, message.indexOf("\"", actionIndex + 10));
							}

						}
					} else if (clientMessage.contains("handlerExec")) {
						clientMessageId = getDeferedMessageId(message);
						if (clientMessageId != null) {
							int actionIndex = message.indexOf("\"formname\":\"");
							if (actionIndex > 0) {
								label = message.substring(actionIndex + 12, message.indexOf("\"", actionIndex + 12));
							}

							actionIndex = message.indexOf("\"beanname\":\"");
							if (actionIndex > 0) {
								label += ":"
										+ message.substring(actionIndex + 12, message.indexOf("\"", actionIndex + 12));
							}

							label += ":column[]";

							actionIndex = message.indexOf("\"eventType\":\"");
							if (actionIndex > 0) {
								label += ":"
										+ message.substring(actionIndex + 13, message.indexOf("\"", actionIndex + 14));
							}

						}
					}
					if (clientMessageId != null) {
						// skip for now
						synchronized (this) {
							if (testResult == null) {
								waitingForNextRun = true;
								logger.info("test result not set yet, waiting for the next run test call " + label);
								return;
							}
							logger.info(" smaple start of " + label);
							testResult.sampleStart();
							logger.severe("sub result2 : " + testResult.getSampleLabel());
							testResult.setSampleLabel(label);
							startedActions.put(clientMessageId, testResult);
						}
					}
					// logger.info(session.getId() + ": Sending normal...." +
					// clientMessage);
					session.getBasicRemote().sendText(clientMessage);
				}
			} else {
				String clientMessageId = getClientMessageId(message);
				if (clientMessageId == null) {
					clientMessageId = getDeferedMessageId(message);
				}
				if (clientMessageId != null) {
					if (!receivedServerIds.contains(clientMessageId)) {
						// wait until it receives it
						return;
					} else {
						receivedServerIds.remove(clientMessageId);
					}
				}
			}
			queueIterator.remove();
		}
		// if there is still an action waiting, then first wait for the response
		// for that
		if (startedActions.size() > 0)
			return;

		logger.info("Closing the session " + session.getId());
		try {
			session.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "Conversation finished"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.info("Load test ended : " + new Date() + " id " + session.getId());
		if (latch.getCount() > 0)
			latch.countDown();
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		logger.info(String.format("Session %s close because of %s", session.getId(), closeReason));
		if (latch.getCount() > 0)
			latch.countDown();
	}

}
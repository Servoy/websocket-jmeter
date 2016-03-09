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
public class WSSampler extends AbstractJavaSamplerClient 
{
	
	//constants, true for every test case
	private static final String CMSGIDPATTERN = "\"cmsgid\":.";
	private static final String SMSGIDPATTERN = "\"smsgid\":.";
	private static final Pattern psmsgid = Pattern.compile(SMSGIDPATTERN);
	private static final Pattern pcmsgid = Pattern.compile(CMSGIDPATTERN);


	private String ws_uri;
	private String response_message;
	private List<Long> waitingTimes;
	private CountDownLatch latch;
 
	private List<String> reqRes;
	private Logger logger = Logger.getLogger("WSSampler");
	private List<String> receivedServerIds = Collections.synchronizedList(new ArrayList<String>()); 
 
	private SampleResult testResult;
	private Map<String,SampleResult> startedActions;
	private boolean firstTime = true;
	
    @Override
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();
        params.addArgument("URI", "ws://localhost:8080/websocket/null/null/null?solution=Test");
        params.addArgument("TestCaseFile", "reqResConfig.txt");
        params.addArgument("WaitingTime", "5,2,3,4,5");
        return params;
    }
 
    @Override
    public void setupTest(JavaSamplerContext context) {
        ws_uri = context.getParameter("URI");
        
        File testCaseFile = new File(context.getParameter("TestCaseFile")); 
        reqRes = new RecordingParser().getMessageQueue(testCaseFile);
        
        String waitingTime = context.getParameter("WaitingTime");//waiting time for each action
        if (waitingTime != null)
        {
        	String[] s = waitingTime.split(",");
        	waitingTimes = new ArrayList<Long>();
        	for (String t : s)
        	{
        		waitingTimes.add(Long.parseLong(t));
        	}
        }
    }
 
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        testResult = new SampleResult();
        startedActions = Collections.synchronizedMap(new HashMap<String,SampleResult>());
        latch = new CountDownLatch(1);
 
        ClientManager client = ClientManager.createClient();
        try {
            client.connectToServer(this, new URI(ws_uri));
            latch.await();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        
        testResult.setSuccessful(true);
        testResult.setResponseMessage(response_message);
        testResult.setResponseCode("200");
        if (response_message != null) {
        	testResult.setResponseData(response_message.getBytes());
        }
        return testResult;
    }
 
    @OnOpen
    public void onOpen(Session session) {
    	logger.info("Connected ... " + session.getId());
		try
		{
			testResult.sampleStart();
//			processQueue(session);
		}
		catch(Exception e)
		{
			logger.severe("Unexpected error: "+ e);
			e.printStackTrace();
		}
    }
 
    @OnMessage
    public String onMessage(String message, Session session) 
    {
//    	logger.info(session.getId() + ":   Received ...." + message);
		// check and extract the cmsgid or smsgid from the server messages
		String receivedMsgKey = getMessageId(message);

		if (receivedMsgKey != null) {
			
			if (startedActions.containsKey(receivedMsgKey))
			{
				SampleResult subResult = startedActions.remove(receivedMsgKey);
				subResult.setSuccessful(true);
				subResult.sampleEnd();
				testResult.addSubResult(subResult);
			}	
			receivedServerIds.add(receivedMsgKey);
//			logger.info(session.getId() + ":   Received ...." + message);
			try
			{
				processQueue(session);
			}
			catch(Exception e)
			{
				logger.severe("Unexpected error: "+ e);
				e.printStackTrace();
			}
		}
		if (firstTime) {
//	    	try {
//				Thread.sleep(500);
//			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
			try
			{
				processQueue(session);
			}
			catch(Exception e)
			{
				logger.severe("Unexpected error: "+ e);
				e.printStackTrace();
			}
			firstTime = false;
		}
    	return response_message; //TODO need to return some string here
    }
    
 // check if message has cmsgid or smsgid parameter. if it does extract and
 	// return it, otherwise return null
 	public String getMessageId(String message)
 	{
 		String id = getServerMessageId(message);
 		if (id == null)
 		{
 			id = getClientMessageId(message);
 		}
 		return id;
 	}
 	
 	public String getServerMessageId(String message) {

 		Matcher msmsgid = psmsgid.matcher(message);
 		if (msmsgid.find()) {
 			return msmsgid.group(0);
 		}
 		return null;
 	}

 	public String getClientMessageId(String message)
 	{
 		Matcher mcmsgid = pcmsgid.matcher(message);
 		if (mcmsgid.find()) {
 			return mcmsgid.group(0);
 		}
 		return null;
 	}
 	
 	public String getClientMessage(String message){
		if (message.startsWith(">")){
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
 				if (id != null)
 				{
 					if (receivedServerIds.contains(id))
 					{
// 						logger.info(session.getId() + ":   Sending server request...." + clientMessage);
 						session.getBasicRemote().sendText(clientMessage);
 						receivedServerIds.remove(id);
 					}
 					else
 					{
 						// wait until it receives it
 						return;
 					}	
 				}
 				else
 				{
 					if (clientMessage.contains("executeEvent"))
 					{
 						String clientMessageId = getClientMessageId(message);
 						if (clientMessageId != null)
 		 				{
 							// TODO make a sleep here that is defined in the arguments 
// 							testResult.samplePause();
// 							Thread.sleep(200);
// 							testResult.sampleResume();
 							SampleResult sub = new SampleResult();
 							sub.sampleStart();
 							String label = "action";
 							int actionIndex = message.indexOf("\"formname\":\"");
 							if (actionIndex >0)
 							{
 								label = message.substring(actionIndex+12, message.indexOf("\"", actionIndex+12));
 							}	
 							
 							actionIndex = message.indexOf("\"beanname\":\"");
 							if (actionIndex >0)
 							{
 								label += ":"+message.substring(actionIndex+12, message.indexOf("\"", actionIndex+12));
 							}
 							
 							actionIndex = message.indexOf("\"event\":\"");
 							if (actionIndex >0)
 							{
 								label += ":"+message.substring(actionIndex+9, message.indexOf("\"", actionIndex+10));
 							}
 							sub.setSampleLabel(label);
 							startedActions.put(clientMessageId, sub);
 		 				}
 					}	
// 	 				logger.info(session.getId() + ":   Sending normal...." + clientMessage);
 					session.getBasicRemote().sendText(clientMessage);
 				}	
 			}
 			else
 			{
 				String clientMessageId = getClientMessageId(message);
 				if (clientMessageId != null)
 				{
 					if (!receivedServerIds.contains(clientMessageId))
 					{
 						// wait until it receives it
 						return;
 					}
 					else
 					{
 						receivedServerIds.remove(clientMessageId);
 					}
 				}	
 			}	
 			queueIterator.remove();
 		}
 		
 		logger.info("Closing the session " + session.getId());
 		try {
 			session.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "Conversation finished"));
 		} catch (IOException e) {
 			throw new RuntimeException(e);
 		}
 		logger.info("Load test ended : " + new Date() + " id " + session.getId());
 		if (latch.getCount() > 0) latch.countDown();
 	}

 
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
    	logger.info(String.format("Session %s close because of %s", session.getId(), closeReason));
    	if (latch.getCount() > 0) latch.countDown();
    }
 
 
}
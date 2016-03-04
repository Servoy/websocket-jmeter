package com.servoy.jmeter.ws;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.glassfish.tyrus.client.ClientManager;

import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
 
@ClientEndpoint
public class WSSampler extends AbstractJavaSamplerClient 
{
	
	//constants, true for every test case
	private static final String CMSGIDPATTERN = "\"cmsgid\":.";
	private static final String SMSGIDPATTERN = "\"smsgid\":.";
	private static final Pattern psmsgid = Pattern.compile(SMSGIDPATTERN);
	private static final Pattern pcmsgid = Pattern.compile(CMSGIDPATTERN);


	private static String ws_uri;
	private static String response_message;
	private List<Long> waitingTimes;
	private static CountDownLatch latch;
 
	private ArrayList<String> reqRes;
	private Logger logger = Logger.getLogger("WSSampler");
	private List<String> receivedServerIds = new ArrayList<String>(); 
 
    @Override
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();
        params.addArgument("URI", "ws://localhost:8080/websocket/null/null/null?solution=test_components&f=test_crosstab");
        params.addArgument("TestCaseFile", "reqResConfig.txt");
        params.addArgument("WaitingTime", "5,2,3,4,5");
        return params;
    }
 
 
    @Override
    public void setupTest(JavaSamplerContext context) {
        ws_uri = context.getParameter("URI");
        
        File testCaseFile = new File("TestCaseFile"); 
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
        SampleResult rv = new SampleResult();
        rv.sampleStart();
        latch = new CountDownLatch(1);
 
        ClientManager client = ClientManager.createClient();
        try {
            client.connectToServer(WSSampler.class, new URI(ws_uri));
            latch.await(waitingTimes.get(0), TimeUnit.SECONDS); //TODO we need to identify which message (number)/action is this
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        rv.setSuccessful(true);
        rv.setResponseMessage(response_message);
        rv.setResponseCode("200");
        if (response_message != null) {
            rv.setResponseData(response_message.getBytes());
        }
        rv.sampleEnd();
        return rv;
    }
 
    @OnOpen
    public void onOpen(Session session) {
    	logger.info("Connected ... " + session.getId());
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
 
    @OnMessage
    public String onMessage(String message, Session session) 
    {
    	logger.info("Received ...." + message);

		// check and extract the cmsgid or smsgid from the server messages
		String receivedMsgKey = getMessageId(message);

		if (receivedMsgKey != null) {
			
			receivedServerIds.add(receivedMsgKey);
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
 						logger.info("Sending ...." + clientMessage);
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
 					logger.info("Sending ...." + clientMessage);
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
 		logger.info("Load test ended : " + new Date());
 	}

 
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
    	logger.info(String.format("Session %s close because of %s", session.getId(), closeReason));
		latch.countDown();
    }
 
 
}
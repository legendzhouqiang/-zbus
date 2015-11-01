package org.zbus.mq.server;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.zbus.kit.FileKit;
import org.zbus.kit.JsonKit;
import org.zbus.kit.log.Logger;
import org.zbus.mq.Protocol;
import org.zbus.mq.Protocol.BrokerInfo;
import org.zbus.mq.Protocol.MqInfo;
import org.zbus.mq.Protocol.MqMode;
import org.zbus.mq.server.support.DiskQueuePool;
import org.zbus.mq.server.support.DiskQueuePool.DiskQueue;
import org.zbus.mq.server.support.MessageDiskQueue;
import org.zbus.mq.server.support.MessageMemoryQueue;
import org.zbus.net.core.IoAdaptor;
import org.zbus.net.core.Session;
import org.zbus.net.http.Message;
import org.zbus.net.http.Message.MessageHandler;
import org.zbus.net.http.MessageCodec;

public class MqAdaptor extends IoAdaptor implements Closeable {
	private static final Logger log = Logger.getLogger(MqAdaptor.class);

	private final Map<String, AbstractMQ> mqTable;
	private final Map<String, Session> sessionTable;
	private final Map<String, MessageHandler> handlerMap = new ConcurrentHashMap<String, MessageHandler>();
	
	private boolean verbose = false;    
	private final MqServer mqServer;
	private String registerToken = "";

 
	public MqAdaptor(MqServer mqServer){
		codec(new MessageCodec());   
		this.mqServer = mqServer;
		this.mqTable = mqServer.getMqTable();
		this.sessionTable = mqServer.getSessionTable(); 
		this.registerToken = mqServer.getRegisterToken();
		
		registerHandler(Protocol.Produce, produceHandler); 
		registerHandler(Protocol.Consume, consumeHandler);  
		registerHandler(Protocol.Route, routeHandler);  
		registerHandler(Protocol.CreateMQ, createMqHandler); 
		registerHandler(Protocol.Test, testHandler);
		registerHandler(Protocol.Query, queryHandler);
		
		registerHandler("", homeHandler);  
		registerHandler(Protocol.Data, dataHandler); 
		registerHandler(Protocol.Jquery, jqueryHandler);
		
		registerHandler(Message.HEARTBEAT, heartbeatHandler);   
		
	} 
	
	private Message handleUrlMessage(Message msg){
		UrlInfo url = new UrlInfo(msg.getRequestString()); 
		if(url.empty){
			msg.setCmd(""); //default to home monitor
			return msg;
		}   
		
		if(url.mq != null){
			if(msg.getMq() == null){
				msg.setMq(url.mq);
			}
			String method = url.method;
			if(method == null){
				method = "";
			}
			if(url.method != null || url.cmd == null){ 
				AbstractMQ mq = mqTable.get(url.mq);
				if(mq != null && MqMode.isEnabled(mq.getMode(), MqMode.RPC)){
					msg.setMq(url.mq);
					msg.setAck(false); 
					msg.setCmd(Protocol.Produce);
					String module = url.module == null? "" : url.module;   
					String json = "{";
					json += "\"module\": " + "\"" + module + "\"";
					json += ", \"method\": " + "\"" + method + "\"";
					if(url.params != null){
						json += ", \"params\": " + "[" + url.params + "]";  
					}
					json += "}";
					msg.setJsonBody(json);	
				}
			} 
		} 
		
		if(url.cmd != null){
			if(msg.getCmd() == null){
				msg.setCmd(url.cmd);
			}
		}  
		
		return msg;
	}
    
    public void onMessage(Object obj, Session sess) throws IOException {  
    	Message msg = (Message)obj;  
    	msg.setSender(sess.id());
		msg.setServer(mqServer.getServerAddr()); 
		msg.setRemoteAddr(sess.getRemoteAddress());
		
		if(verbose){
			log.info("\n%s", msg);
		}
		
		String cmd = msg.getCmd(); 
		
		if(cmd == null){ //处理URL消息格式，否则url忽略不计 
			msg = handleUrlMessage(msg);
			cmd = msg.getCmd();
		} 
    	if(cmd != null){
	    	MessageHandler handler = handlerMap.get(cmd);
	    	if(handler != null){
	    		handler.handle(msg, sess);
	    		return;
	    	}
    	}
    	
    	Message res = new Message();
    	res.setId(msg.getId()); 
    	res.setResponseStatus(400);
    	String text = String.format("Bad format: command(%s) not support", cmd);
    	res.setBody(text); 
    	sess.write(res); 
    } 
	
    private AbstractMQ findMQ(Message msg, Session sess) throws IOException{
		String mqName = msg.getMq();
		AbstractMQ mq = mqTable.get(mqName); 
    	if(mq == null){
    		ReplyKit.reply404(msg, sess); 
    		return null;
    	} 
    	return mq;
	}
     
    public void registerHandler(String command, MessageHandler handler){
    	this.handlerMap.put(command, handler);
    } 
    
	private MessageHandler produceHandler = new MessageHandler() { 
		@Override
		public void handle(Message msg, Session sess) throws IOException { 
			AbstractMQ mq = findMQ(msg, sess);
			if(mq == null) return;
			if(!auth(mq, msg)){ 
				ReplyKit.reply403(msg, sess);
				return;
			}
			
			boolean ack = msg.isAck();
			msg.removeHead(Message.CMD);
			msg.removeHead(Message.ACK);
			
			mq.produce(msg, sess);
			mq.lastUpdateTime = System.currentTimeMillis();
			if(ack){
				ReplyKit.reply200(msg, sess);
			}
		}
	}; 
	
	private MessageHandler consumeHandler = new MessageHandler() { 
		@Override
		public void handle(Message msg, Session sess) throws IOException { 
			AbstractMQ mq = findMQ(msg, sess);
			if(mq == null) return;
			if(!auth(mq, msg)){ 
				ReplyKit.reply403(msg, sess);
				return;
			}
			
			mq.consume(msg, sess);
			
			String mqName = sess.attr("mq");
			if(!msg.getMq().equals(mqName)){
				sess.attr("mq", mq.getName()); //mark
				mqServer.pubEntryUpdate(mq); //notify TrackServer
			} 
		}
	}; 
	
	private MessageHandler routeHandler = new MessageHandler() { 
		@Override
		public void handle(Message msg, Session sess) throws IOException { 
			String recver = msg.getRecver();
			if(recver == null) {
				return; //just igmore
			}
			Session target = sessionTable.get(recver);
			if(target == null) {
				log.warn("Missing target %s", recver); 
				return; //just ignore
			} 
			msg.removeHead(Message.ACK);
			msg.removeHead(Message.RECVER);
			msg.removeHead(Message.CMD);
			try{
				target.write(msg);
			} catch(Exception ex){
				log.warn("Target(%s) write failed, Ignore", recver); 
				return; //just ignore
			}
		}
	}; 
	
	private MessageHandler createMqHandler = new MessageHandler() {  
		@Override
		public void handle(Message msg, Session sess) throws IOException { 
			String registerToken = msg.getHead("register_token", "");
			if(!MqAdaptor.this.registerToken.equals(registerToken)){
				msg.setBody("registerToken unmatched");
				ReplyKit.reply403(msg, sess);
				return; 
			}
    		
			String mqName = msg.getHead("mq_name", "");
			mqName = mqName.trim();
			if("".equals(mqName)){
				msg.setBody("Missing mq_name");
				ReplyKit.reply400(msg, sess);
				return;
			}
			String mqMode = msg.getHead("mq_mode", "");
			mqMode = mqMode.trim();
			if("".equals(mqMode)){
				msg.setBody("Missing mq_mode");
				ReplyKit.reply400(msg, sess);
				return;
			}
			int mode = 0;
    		try{
    			mode = Integer.valueOf(mqMode); 
    		} catch (Exception e){
    			msg.setBody("mq_mode invalid");
    			ReplyKit.reply400(msg, sess);
        		return;  
    		}
    		
    		String accessToken = msg.getHead("access_token", ""); 
    		AbstractMQ mq = null;
    		synchronized (mqTable) {
    			mq = mqTable.get(mqName);
    			if(mq != null){
    				ReplyKit.reply200(msg, sess);
    				return;
    			}
    			
    			AbstractQueue<Message> support = null;
				if(MqMode.isEnabled(mode, MqMode.Memory) ||
						MqMode.isEnabled(mode, MqMode.RPC)){
					support = new MessageMemoryQueue();
				} else {
					support = new MessageDiskQueue(mqName, mode);
				}
				
    			if(MqMode.isEnabled(mode, MqMode.PubSub)){ 
    				mq = new PubSub(mqName, support);
    			} else {
    				mq = new MQ(mqName, support);
    			}
    			mq.setMode(mode);
    			mq.creator = sess.getRemoteAddress();
    			mq.setAccessToken(accessToken);
    			
    			log.info("MQ Created: %s", mq);
    			mqTable.put(mqName, mq);
    			ReplyKit.reply200(msg, sess);
    			
    			mqServer.pubEntryUpdate(mq);
    		}
		}
	};  
	
	private MessageHandler testHandler = new MessageHandler() {
		public void handle(Message msg, Session sess) throws IOException {
			Message res = new Message();
			res.setResponseStatus(200); 
			res.setId(msg.getId()); 
			res.setBody("OK");
			sess.write(res);
		}
	};
	
	private MessageHandler homeHandler = new MessageHandler() {
		public void handle(Message msg, Session sess) throws IOException {
			msg = new Message();
			msg.setResponseStatus("200");
			msg.setHead("content-type", "text/html");
			String body = FileKit.loadFileContent("zbus.htm");
			if ("".equals(body)) {
				body = "<strong>zbus.htm file missing</strong>";
			}
			msg.setBody(body);
			sess.write(msg);
		}
	};
	
	private MessageHandler jqueryHandler = new MessageHandler() {
		public void handle(Message msg, Session sess) throws IOException {
			msg = new Message();
			msg.setResponseStatus("200");
			msg.setHead("content-type", "application/javascript");
			String body = FileKit.loadFileContent("jquery.js");
			msg.setBody(body);
			sess.write(msg);
		}
	};
	
	private MessageHandler dataHandler = new MessageHandler() {
		public void handle(Message msg, Session sess) throws IOException {
			BrokerInfo info = getStatInfo();

			Message data = new Message();
			data.setResponseStatus("200");
			data.setId(msg.getId());
			data.setHead("content-type", "application/json");
			data.setBody(JsonKit.toJson(info));
			sess.write(data);
		}
	};
	
	private MessageHandler queryHandler = new MessageHandler() {
		public void handle(Message msg, Session sess) throws IOException {
			String json = "";
			if(msg.getMq() == null){
				BrokerInfo info = getStatInfo();
				json = JsonKit.toJson(info);
			} else { 
				AbstractMQ mq = findMQ(msg, sess);
		    	if(mq == null){ 
					return;
				} else {
					json = JsonKit.toJson(mq.getMqInfo());
				}
			}

			Message data = new Message();
			data.setResponseStatus("200");
			data.setId(msg.getId());
			data.setHead("content-type", "application/json");
			data.setBody(json);
			sess.write(data);
		}
	};
	
	private MessageHandler heartbeatHandler = new MessageHandler() {
		@Override
		public void handle(Message msg, Session sess) throws IOException {
			// just ignore
		}
	};
	
	private void cleanSession(Session sess){
		log.info("Clean: " + sess);
		sessionTable.remove(sess.id());
		String mqName = sess.attr("mq");
		if(mqName == null) return;
		
		AbstractMQ mq = mqTable.get(mqName); 
		if(mq == null) return; 
		mq.cleanSession(sess);
		
		mqServer.pubEntryUpdate(mq); 
	}
	
	protected void onSessionAccepted(Session sess) throws IOException {
		sessionTable.put(sess.id(), sess);
		super.onSessionAccepted(sess); 
	}

	@Override
	protected void onException(Throwable e, Session sess) throws IOException { 
		cleanSession(sess);
		super.onException(e, sess);
	}
	
	@Override
	protected void onSessionToDestroy(Session sess) throws IOException { 
		cleanSession(sess);
		super.onSessionToDestroy(sess);
	} 
	
	private boolean auth(AbstractMQ mq, Message msg){
		String appid = msg.getHead("appid", "");
		String token = msg.getHead("token", "");
		return mq.auth(appid, token);
	}
	
    public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}  
     
    public BrokerInfo getStatInfo(){
    	Map<String, MqInfo> table = new HashMap<String, MqInfo>();
   		for(Map.Entry<String, AbstractMQ> e : this.mqTable.entrySet()){
   			MqInfo info = e.getValue().getMqInfo();
   			info.consumerInfoList.clear(); //clear to avoid long list
   			table.put(e.getKey(), info);
   		}  
		BrokerInfo info = new BrokerInfo();
		info.broker = mqServer.getServerAddr();
		info.mqTable = table;  
		return info;
    }
    
    public void loadMQ(String storePath){ 
    	log.info("Loading DiskQueues...");
    	mqTable.clear();
		DiskQueuePool.init(storePath); 
		
		Map<String, DiskQueue> dqs = DiskQueuePool.getQueryMap();
		for(Entry<String, DiskQueue> e : dqs.entrySet()){
			AbstractMQ mq;
			String name = e.getKey();
			DiskQueue diskq = e.getValue();
			int flag = diskq.getFlag(); 
			AbstractQueue<Message> queue = new MessageDiskQueue(name, diskq);
			if( MqMode.isEnabled(flag, MqMode.PubSub)){ 
				mq = new PubSub(name, queue); 
			}  else {
				mq = new MQ(name, queue);  
			}
			mq.setMode(flag);
			mq.lastUpdateTime = System.currentTimeMillis(); 
			mq.creator = "System";
			mqTable.put(name, mq);
			
			//notify
			mqServer.pubEntryUpdate(mq);
		}
    }   
    
    public void close() throws IOException {    
    	DiskQueuePool.destory();  
    } 
}
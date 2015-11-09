/**
 * The MIT License (MIT)
 * Copyright (c) 2009-2015 HONG LEIMING
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.zbus.mq.server;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.zbus.broker.ha.ServerEntry;
import org.zbus.broker.ha.TrackPub;
import org.zbus.kit.ConfigKit;
import org.zbus.kit.log.Logger;
import org.zbus.mq.Protocol.MqInfo;
import org.zbus.net.Client.ConnectedHandler;
import org.zbus.net.Server;
import org.zbus.net.core.Dispatcher;
import org.zbus.net.core.Session;
import org.zbus.proxy.thrift.ThriftCodec;

public class MqServer extends Server{ 
	private static final Logger log = Logger.getLogger(MqServer.class); 
	
	private final Map<String, Session> sessionTable = new ConcurrentHashMap<String, Session>();
	private final Map<String, AbstractMQ> mqTable = new ConcurrentHashMap<String, AbstractMQ>();
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	private long cleanInterval = 3000; 
	private long trackInterval = 5000;
	
	private MqServerConfig config;
	private String registerToken = "";
	
	public MqServer(MqServerConfig config){ 
		this.config = config;   
		serverName = "MqServer";   
		registerToken = config.registerToken;
		serverMainIpOrder = config.serverMainIpOrder;
		dispatcher = new Dispatcher();
		dispatcher.selectorCount(config.selectorCount);
		dispatcher.executorCount(config.executorCount); 
		
		this.scheduledExecutor.scheduleAtFixedRate(new Runnable() { 
			public void run() {  
				Iterator<Entry<String, AbstractMQ>> iter = mqTable.entrySet().iterator();
		    	while(iter.hasNext()){
		    		Entry<String, AbstractMQ> e = iter.next();
		    		AbstractMQ mq = e.getValue(); 
		    		mq.cleanSession();
		    	}
			}
		}, 1000, cleanInterval, TimeUnit.MILLISECONDS); 
		registerDefaultMqAdaptor();
	}
	
	private MqAdaptor defaultMqAdaptor;
	private void registerDefaultMqAdaptor(){
		if(defaultMqAdaptor != null) return;
		//将MqAdaptor与MqServer分离是为了做其他编码支持
		defaultMqAdaptor = new MqAdaptor(this); 
		defaultMqAdaptor.setVerbose(config.verbose);
		defaultMqAdaptor.loadMQ(config.storePath);  
		registerAdaptor(config.getServerAddress(), defaultMqAdaptor, "HttpExt");
	}
	
	@Override
	public void start() throws IOException { 
		log.info("MqServer starting ...");
		super.start(); 
		if(config.trackServerList!= null){
			log.info("Running at HA mode, connect to TrackServers");
			setupTracker(config.trackServerList, dispatcher);
		}  
		log.info("MqServer started successfully");
	}
	
	@Override
	public void close() throws IOException { 
		if(defaultMqAdaptor != null){
			defaultMqAdaptor.close();
		}
		if(trackPub != null){
    		trackPub.close();
    	}
		scheduledExecutor.shutdown();
		super.close();  
		if(dispatcher != null){
			dispatcher.close();
		}
	}
	
	private TrackPub trackPub;
    public void setupTracker(String trackServerList, Dispatcher dispatcher){
    	trackPub = new TrackPub(trackServerList, dispatcher);
    	trackPub.onConnected(new ConnectedHandler() {
    		@Override
    		public void onConnected(Session sess) throws IOException { 
    			trackPub.pubServerJoin(serverAddr);
    			for(AbstractMQ mq : mqTable.values()){
    				pubEntryUpdate(mq);
    			}
    		}
		});
    	trackPub.start();
    	
    	scheduledExecutor.scheduleAtFixedRate(new Runnable() { 
			@Override
			public void run() { 
				for(AbstractMQ mq : mqTable.values()){
    				pubEntryUpdate(mq);
    			}
			}
		}, 2000, trackInterval, TimeUnit.MILLISECONDS);
    }  
     
    public void pubEntryUpdate(AbstractMQ mq){
    	if(trackPub == null) return;
    	 
    	MqInfo info = mq.getMqInfo();
    	
    	ServerEntry se = new ServerEntry();
    	se.entryId = info.name;
    	se.serverAddr = serverAddr;
    	se.consumerCount = info.consumerCount;
    	se.mode = info.mode;
    	se.unconsumedMsgCount = info.unconsumedMsgCount;
    	se.lastUpdateTime = mq.lastUpdateTime;

    	trackPub.pubEntryUpdate(se);
    } 
	
    public String getRegisterToken(){
    	return registerToken;
    }
    
	public Map<String, AbstractMQ> getMqTable() {
		return mqTable;
	} 

	public Map<String, Session> getSessionTable() {
		return sessionTable;
	}

	public static void main(String[] args) throws Exception {
		MqServerConfig config = new MqServerConfig();
		config.serverHost = ConfigKit.option(args, "-h", "0.0.0.0");
		config.serverPort = ConfigKit.option(args, "-p", 15555);
		config.selectorCount = ConfigKit.option(args, "-selector", 0); //0 means default to CPU/4
		config.executorCount = ConfigKit.option(args, "-executor", 64);
		config.verbose = ConfigKit.option(args, "-verbose", false);
		config.storePath = ConfigKit.option(args, "-store", "store");
		config.trackServerList = ConfigKit.option(args, "-track", null);
		config.thriftServer = ConfigKit.option(args, "-thrift", null);
		config.serverMainIpOrder = ConfigKit.option(args, "-ipOrder", null);
		
		final MqServer server = new MqServer(config);  
		if(config.thriftServer != null){
			MqAdaptor thriftAdaptor = new MqAdaptor(server);
			thriftAdaptor.codec(new ThriftCodec());
			server.registerAdaptor(config.thriftServer, thriftAdaptor, "Thrift");
		}
		
		server.start(); 
		
		Runtime.getRuntime().addShutdownHook(new Thread(){ 
			public void run() { 
				try { 
					server.close();
					log.info("MqServer shutdown completed");
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		});   
	} 
}



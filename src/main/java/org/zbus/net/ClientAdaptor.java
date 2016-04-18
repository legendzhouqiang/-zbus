package org.zbus.net;
 
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.zbus.kit.log.Logger;
import org.zbus.net.Sync.Id;
import org.zbus.net.Sync.ResultCallback;
import org.zbus.net.Sync.Ticket;
 
public abstract class ClientAdaptor<REQ extends Id, RES extends Id> implements Client<REQ, RES> { 
	private static final Logger log = Logger.getLogger(ClientAdaptor.class); 
	
	protected Session session;
	
	protected final String host;
	protected final int port; 
	protected int readTimeout = 3000;
	protected int connectTimeout = 3000; 
	protected CountDownLatch activeLatch = new CountDownLatch(1); 
	
	protected volatile MsgHandler<RES> msgHandler; 
	protected volatile ErrorHandler errorHandler;
	protected volatile ConnectedHandler connectedHandler;
	protected volatile DisconnectedHandler disconnectedHandler;
	
	protected volatile ScheduledExecutorService heartbeator = null;
	
	protected ConcurrentMap<String, Object> attributes = null;
	
	protected final Sync<REQ, RES> sync = new Sync<REQ, RES>();
	
	protected CodecInitializer codecInitializer; 
	protected Thread asyncConnectThread; 
	 
	public ClientAdaptor(String address){
		String[] bb = address.split(":");
		if(bb.length > 2) {
			throw new IllegalArgumentException("Address invalid: "+ address);
		}
		host = bb[0].trim();
		if(bb.length > 1){
			port = Integer.valueOf(bb[1]);
		} else {
			port = 80;
		}  
		
		onConnected(new ConnectedHandler() { 
			@Override
			public void onConnected() throws IOException {
				String msg = String.format("Connection(%s:%d) OK", host, port);
				log.info(msg);
			}
		});
		
		onDisconnected(new DisconnectedHandler() { 
			@Override
			public void onDisconnected() throws IOException {
				log.warn("Disconnected from(%s:%d)", host, port);
				ensureConnectedAsync();//automatically reconnect by default
			}
		});
	}
	
	private synchronized void cleanSession() throws IOException{
		if(session != null){
			session.close();
			session = null;
			activeLatch = new CountDownLatch(1);
		} 
	}
	 
	public void codec(CodecInitializer codecInitializer) {
		this.codecInitializer = codecInitializer;
	} 
	
	public synchronized void startHeartbeat(int heartbeatInterval){
		if(heartbeator == null){
			heartbeator = Executors.newSingleThreadScheduledExecutor();
			this.heartbeator.scheduleAtFixedRate(new Runnable() {
				public void run() {
					try {
						heartbeat();
					} catch (Exception e) {
						log.warn(e.getMessage(), e);
					}
				}
			}, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
		}
	}
	
	@Override
	public void heartbeat() {
		
	}
	
	
	public boolean hasConnected() {
		return session != null && session.isActive();
	}
	
	public void ensureConnectedAsync(){
		if(hasConnected()) return;
		if(asyncConnectThread != null) return;
		
		asyncConnectThread = new Thread(new Runnable() { 
			@Override
			public void run() {
				try {
					ensureConnected();
					asyncConnectThread = null;
				} catch (InterruptedException e) {
					//ignore
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		});
		asyncConnectThread.setName("ClientConnectionAync");
		asyncConnectThread.start(); 
	}
	
	 
	public void ensureConnected() throws IOException, InterruptedException{
		if(hasConnected()) return; 
		
		synchronized (this) {
			while(!hasConnected()){
				try {
		    		connectAsync();
					activeLatch.await(readTimeout,TimeUnit.MILLISECONDS);
					
					if(hasConnected()){ 
						break;
					}
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				} 
				
				String msg = String.format("Connection(%s:%d) timeout, trying again in %.1f seconds",
						host, port, connectTimeout/1000.0); 
				log.warn(msg);
				cleanSession();
				Thread.sleep(connectTimeout);
			}
		} 
	} 
	
	public void sendMessage(REQ req) throws IOException, InterruptedException{
		ensureConnected();  
    	session.writeAndFlush(req);
    } 
	
	 
	@Override
	public void close() throws IOException {
		onConnected(null);
		onDisconnected(null);
		
		if(asyncConnectThread != null){
			asyncConnectThread.interrupt();
			asyncConnectThread = null;
		}
		
		if(session != null){
			session.close();
			session = null;
		}  
		if(heartbeator != null){
			heartbeator.shutdownNow();
			heartbeator = null;
		} 
	}
	
	
	@SuppressWarnings("unchecked")
	public <V> V attr(String key) {
		if (this.attributes == null) {
			return null;
		}

		return (V) this.attributes.get(key);
	}

	public <V> void attr(String key, V value) {
		if(value == null){
			if(this.attributes != null){
				this.attributes.remove(key);
			}
			return;
		}
		if (this.attributes == null) {
			synchronized (this) {
				if (this.attributes == null) {
					this.attributes = new ConcurrentHashMap<String, Object>();
				}
			}
		} 
		this.attributes.put(key, value);
	}
	
	public void onMessage(MsgHandler<RES> msgHandler){
    	this.msgHandler = msgHandler;
    }
    
    public void onError(ErrorHandler errorHandler){
    	this.errorHandler = errorHandler;
    } 
    
    public void onConnected(ConnectedHandler connectedHandler){
    	this.connectedHandler = connectedHandler;
    } 
    
    public void onDisconnected(DisconnectedHandler disconnectedHandler){
    	this.disconnectedHandler = disconnectedHandler;
    }

	@Override
	public void onSessionAccepted(Session sess) throws IOException { 
		//server side
	}
 
	@Override
	public void onSessionRegistered(Session sess) throws IOException {
		//ignore
	}

	@Override
	public void onSessionConnected(Session sess) throws IOException { 
		this.session = sess;
		activeLatch.countDown();
		if(connectedHandler != null){
			connectedHandler.onConnected();
		}
	}

	public void onSessionToDestroy(Session sess) throws IOException {
		if(this.session != null){
			this.session.close(); 
			this.session = null;
		}
		sync.clearTicket();
		
		if(disconnectedHandler != null){
			disconnectedHandler.onDisconnected();
		}   
	} 

	@Override
	public void onException(Throwable e, Session sess) throws IOException { 
		if(errorHandler != null){
			errorHandler.onError(e, session);
		} else {
			log.error(e.getMessage(), e);
		}
	} 
	 
	public void invokeAsync(REQ req, ResultCallback<RES> callback) throws IOException { 
		Ticket<REQ, RES> ticket = null;
		if(callback != null){
			ticket = sync.createTicket(req, readTimeout, callback);
		} else {
			if(req.getId() == null){
				req.setId(Ticket.nextId());
			}
		} 
		try{
			sendMessage(req); 
		} catch(IOException e) {
			if(ticket != null){
				sync.removeTicket(ticket.getId());
			}
			throw e;
		} catch (InterruptedException e) {
			log.warn(e.getMessage(), e);
		}  
	} 
	
	public RES invokeSync(REQ req) throws IOException, InterruptedException {
		return this.invokeSync(req, this.readTimeout);
	}
	 
	public RES invokeSync(REQ req, int timeout) throws IOException, InterruptedException {
		Ticket<REQ, RES> ticket = null;
		try { 
			ticket = sync.createTicket(req, timeout);
			sendMessage(req);   
			if (!ticket.await(timeout, TimeUnit.MILLISECONDS)) {
				return null;
			}
			return ticket.response();
		} finally {
			if (ticket != null) {
				sync.removeTicket(ticket.getId());
			}
		}
	} 
	
	@Override
	public void onMessage(Object msg, Session sess) throws IOException {
		@SuppressWarnings("unchecked")
		RES res = (RES)msg;  
    	Ticket<REQ, RES> ticket = sync.removeTicket(res.getId());
    	if(ticket != null){
    		ticket.notifyResponse(res); 
    		return;
    	}   
    	
    	if(msgHandler != null){
    		msgHandler.handle(res, sess);
    		return;
    	} 
    	
    	log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!Drop,%s", res);
	}  
	
	@Override
	public String toString() { 
		return String.format("(connected=%s, remote=%s:%d)", hasConnected(), host, port);
	}
}

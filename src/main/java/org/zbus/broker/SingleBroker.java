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
package org.zbus.broker;

import java.io.IOException;

import org.zbus.kit.log.Logger;
import org.zbus.kit.pool.ObjectFactory;
import org.zbus.kit.pool.Pool;
import org.zbus.net.Sync.ResultCallback;
import org.zbus.net.core.Dispatcher;
import org.zbus.net.http.Message;
import org.zbus.net.http.MessageClient;

public class SingleBroker implements Broker {
	private static final Logger log = Logger.getLogger(SingleBroker.class);     
	
	private final Pool<MessageClient> pool; 
	private String serverAddress; 
	
	private BrokerConfig config;
	private Dispatcher dispatcher = null;
	private boolean ownDispatcher = false;
	 
	public SingleBroker(BrokerConfig config) throws IOException{ 
		this.config = config;
		this.serverAddress = config.getServerAddress(); 
		
		if(config.getDispatcher() == null){
			this.ownDispatcher = true;
			this.dispatcher = new Dispatcher();
			this.dispatcher.selectorCount(config.getSelectorCount());
			this.dispatcher.executorCount(config.getExecutorCount());
			this.config.setDispatcher(dispatcher);
		} else {
			this.dispatcher = config.getDispatcher();
			this.ownDispatcher = false;
		}
		this.dispatcher.start(); 
		
		this.pool = Pool.getPool(new MessageClientFactory(), this.config); 
	}  

	@Override
	public void close() throws IOException { 
		this.pool.close(); 
		if(ownDispatcher && this.dispatcher != null){
			try {
				this.dispatcher.close();
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
		}
	}  
	
	public void invokeAsync(Message msg, ResultCallback<Message> callback) throws IOException {  
		MessageClient client = null;
		try {
			client = this.pool.borrowObject(); 
			client.invokeAsync(msg, callback);
		} catch(IOException e){
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new BrokerException(e.getMessage(), e);
		} finally{
			if(client != null){
				this.pool.returnObject(client);
			}
		}
	} 

	public Message invokeSync(Message req, int timeout) throws IOException {
		MessageClient client = null;
		try {
			client = this.pool.borrowObject(); 
			return client.invokeSync(req, timeout);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new BrokerException(e.getMessage(), e);
		} finally{
			if(client != null){
				this.pool.returnObject(client);
			}
		}
	}
	
	public MessageClient getClient(BrokerHint hint) throws IOException{ 
		MessageClient client = new MessageClient(this.serverAddress, this.dispatcher);
		client.attr("server", serverAddress);
		return client;
	}

	public void closeClient(MessageClient client) throws IOException {
		client.close();
	}

	
	private class MessageClientFactory implements ObjectFactory<MessageClient> {		
		@Override
		public boolean validateObject(MessageClient client) { 
			if(client == null) return false;
			return client.hasConnected();
		}
		
		@Override
		public void destroyObject(MessageClient client){ 
			try {
				client.close();
			} catch (IOException e) {
				log.error(e.getMessage(), e); 
			}
		}
		
		@Override
		public MessageClient createObject() { 
			return new MessageClient(serverAddress, dispatcher); 
		}
	}
}




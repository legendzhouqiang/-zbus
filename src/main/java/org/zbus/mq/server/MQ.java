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
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.zbus.kit.log.Logger;
import org.zbus.mq.Protocol.ConsumerInfo;
import org.zbus.mq.Protocol.MqInfo;
import org.zbus.net.core.Session;
import org.zbus.net.http.Message;

public class MQ extends AbstractMQ{
	private static final Logger log = Logger.getLogger(MQ.class);  
	
	protected final Map<String, Session> pullSessions = new ConcurrentHashMap<String, Session>();
	
	protected final BlockingQueue<PullSession> pullQ = new LinkedBlockingQueue<PullSession>();
	
	public MQ(String name, AbstractQueue<Message> msgQ) {
		super(name, msgQ);
	}
	
	@Override
	public void consume(Message msg, Session sess) throws IOException {  
		if(!pullSessions.containsKey(sess.id())){
			pullSessions.put(sess.id(), sess);
		}
		for(PullSession pull : pullQ){
			if(pull.getSession() == sess){
				pull.setPullMessage(msg);
				this.dispatch();
				return; 
			}
		} 
		
		PullSession pull = new PullSession(sess, msg);
		pullQ.offer(pull);  
		this.dispatch();
	}
	
	void dispatch() throws IOException{  
		while(pullQ.peek() != null && msgQ.size() > 0){
			PullSession pull = pullQ.poll(); 
			if(pull == null){
				continue;
			}
			if( !pull.getSession().isActive() ){ 
				continue;
			} 
			
			Message msg = msgQ.poll();
			if(msg == null){
				continue;
			} 
			this.lastUpdateTime = System.currentTimeMillis();
			
			try {  
				Message pullMsg = pull.getPullMessage(); 
				Message writeMsg = Message.copyWithoutBody(msg);
				
				writeMsg.setRawId(msg.getId());  //保留原始消息ID
				writeMsg.setId(pullMsg.getId()); //配对订阅消息！
				if(writeMsg.getResponseStatus() == null){
					writeMsg.setResponseStatus(200); //default to 200
				}
				pull.getSession().write(writeMsg); 
			
			} catch (IOException ex) {   
				log.error(ex.getMessage(), ex); 
				msgQ.offer(msg);
			} 
		} 
	}

	@Override
	public void cleanSession(Session sess) {
		pullSessions.remove(sess.id());
		
		Iterator<PullSession> iter = pullQ.iterator();
		while(iter.hasNext()){
			PullSession pull = iter.next();
			if(sess == pull.session){
				iter.remove();
				break;
			}
		}
	}
	
	@Override
	public void cleanSession() { 
		Iterator<PullSession> iter = pullQ.iterator();
		while(iter.hasNext()){
			PullSession pull = iter.next();
			if(!pull.session.isActive()){
				pullSessions.remove(pull.session.id());
				iter.remove();
			}
		}
	}
	
	@Override
	public MqInfo getMqInfo() { 
		MqInfo info = new MqInfo(); 
		info.name = name;
		info.lastUpdateTime = lastUpdateTime;
		info.creator = creator;
		info.mode = this.mode;
		info.unconsumedMsgCount = msgQ.size();
		info.consumerCount = pullSessions.size();
		info.consumerInfoList = new ArrayList<ConsumerInfo>();
		for(PullSession pull : pullQ){ 
			info.consumerInfoList.add(pull.getConsumerInfo());
		} 
		return info;
	}

	@Override
	public String toString() {
		return "MQ [name=" + name + ", creator=" + creator + "]";
	}
	
	
}

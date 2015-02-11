package org.zbus.client;

import java.io.IOException;

import org.zbus.common.Proto;
import org.zbus.remoting.Message;
import org.zbus.remoting.ticket.ResultCallback;

/**
 * 生产者,
 * @author 洪磊明(rushmore)
 *
 */
public class Producer {     
	private final Broker broker; 
	private final String mq;
	private String accessToken = "";
	private String registerToken = "";

	public Producer(Broker broker, String mq) {
		this.broker = broker;
		this.mq = mq; 
	} 
	
	public Producer(MqConfig config){
		this.broker = config.getBroker();
		this.mq = config.getMq();
		this.accessToken = config.getAccessToken();
		this.registerToken = config.getRegisterToken();
	}

	
	public void send(Message msg, final ResultCallback callback)
			throws IOException {
		msg.setCommand(Proto.Produce);
		msg.setMq(this.mq);
		msg.setToken(this.accessToken);
		
		broker.invokeAsync(msg, callback);
	}
	

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRegisterToken() {
		return registerToken;
	}

	public void setRegisterToken(String registerToken) {
		this.registerToken = registerToken;
	}
}

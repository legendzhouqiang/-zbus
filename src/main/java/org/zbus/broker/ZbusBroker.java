package org.zbus.broker;

import java.io.IOException;

import org.zbus.broker.ha.HaBroker;
import org.zbus.net.Sync.ResultCallback;
import org.zbus.net.http.Message;
import org.zbus.net.http.Message.MessageInvoker;

/**
 * Broker factory class, abstraction of all broker types
 * 1) JvmBroker, brokerAddess=null/jvm
 * 2) SingleBroker, brokerAddress=<ip>:<port>, eg. 127.0.0.1:15555
 * 3) HaBroker, brokerAddress=[<ip>:<port>;<ip>:<port>], 
 * 	 '[' and ']' could be omitted, ';' ',' and ' ' are supported to split trackServer <ip>:<port> list
 *   eg. [127.0.0.1:16666;127.0.0.1:166667], [127.0.0.1:16666]
 *   127.0.0.1:16666;127.0.0.1:16667
 *   
 * @author rushmore (洪磊明)
 *
 */
public class ZbusBroker implements Broker{
	private Broker support;
	
	/**
	 * Default to SingleBroker to localhost:15555
	 * @throws IOException
	 */
	public ZbusBroker() throws IOException { 
		this(new BrokerConfig());
	}
	
	/**
	 * Build underlying Broker by borkerAddress
	 * @param config
	 * @throws IOException
	 */
	public ZbusBroker(BrokerConfig config) throws IOException { 
		String brokerAddress = config.getBrokerAddress();
		if(brokerAddress == null || "jvm".equalsIgnoreCase(brokerAddress)){
			if(config.getMqServer() != null){
				support = new JvmBroker(config.getMqServer());
			} else {
				if(config.getMqServerConfig() != null){
					support = new JvmBroker(config.getMqServerConfig());
				} else {
					support = new JvmBroker();
				}
			}
			return;
		}
		brokerAddress = brokerAddress.trim();
		
		if(brokerAddress.matches("[\\[\\], ;]")){ 
			if(brokerAddress.startsWith("[") && brokerAddress.endsWith("]")){
				brokerAddress = brokerAddress.substring(1, brokerAddress.length()-1);
			}
			config.setBrokerAddress(brokerAddress);
			support = new HaBroker(config); 
			return;
		}
		
		config.setBrokerAddress(brokerAddress);
		support = new SingleBroker(config);
	}
	
	@Override
	public Message invokeSync(Message req, int timeout) throws IOException, InterruptedException {
		return support.invokeSync(req, timeout);
	}

	@Override
	public void invokeAsync(Message req, ResultCallback<Message> callback) throws IOException {
		invokeAsync(req, callback);
	}

	@Override
	public void close() throws IOException {
		support.close();
	}

	@Override
	public MessageInvoker getClient(BrokerHint hint) throws IOException {
		return support.getClient(hint);
	}

	@Override
	public void closeClient(MessageInvoker client) throws IOException {
		support.closeClient(client);
	}

}

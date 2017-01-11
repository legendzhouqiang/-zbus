package io.zbus.examples.pubsub;

import java.io.IOException;

import io.zbus.mq.Broker;
import io.zbus.mq.ConsumeGroup;
import io.zbus.mq.Consumer;
import io.zbus.mq.Consumer.ConsumerHandler;
import io.zbus.mq.MqConfig;
import io.zbus.mq.broker.ZbusBroker;
import io.zbus.net.http.Message;

public class Sub_FilterTag_Sharp {
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception{   
		final Broker broker = new ZbusBroker("127.0.0.1:15555");
		
		
		
		MqConfig config = new MqConfig();
		config.setBroker(broker);
		config.setMq("MyMQ"); 
		
		ConsumeGroup group = new ConsumeGroup();
		group.setGroupName("Group6");
		group.setFilterTag("abc.#"); //abc.xx, abc.yy.
		
		config.setConsumeGroup(group);  
		
		Consumer c = new Consumer(config);    
		c.declareQueue();
		
		c.start(new ConsumerHandler() { 
			@Override
			public void handle(Message msg, Consumer consumer) throws IOException {   
				System.out.println(msg); 
			}
		});    
	} 
}
package io.zbus.examples.mq;

import java.io.IOException;

import io.zbus.mq.Broker;
import io.zbus.mq.Consumer;
import io.zbus.mq.ConsumerHandler;
import io.zbus.mq.Message;
import io.zbus.mq.broker.ZbusBroker;

public class ConsumerCloseClear { 
	public static void main(String[] args) throws Exception {  
		Broker broker = new ZbusBroker("127.0.0.1:15555");   
		Consumer consumer = new Consumer(broker, "MyMQ");  
		 
		consumer.start(new ConsumerHandler() { 
			@Override
			public void handle(Message msg, Consumer consumer) throws IOException { 
				System.out.println(msg);
			}
		});    
		
		Thread.sleep(1000); 
		consumer.close();
		
		broker.close();
		System.out.println("closed");
	}
}

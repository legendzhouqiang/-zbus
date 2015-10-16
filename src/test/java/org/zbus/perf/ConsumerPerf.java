package org.zbus.perf;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.SingleBroker;
import org.zbus.kit.ConfigKit;
import org.zbus.mq.Consumer;
import org.zbus.mq.MqConfig;
import org.zbus.net.core.Session;
import org.zbus.net.http.Message;
import org.zbus.net.http.Message.MessageHandler;

public class ConsumerPerf {
	public static void main(String[] args) throws Exception{   
		final String serverAddress = ConfigKit.option(args, "-b", "127.0.0.1:15555");
		final int threadCount = ConfigKit.option(args, "-c", 4); 
		final String mq = ConfigKit.option(args, "-mq", "MyMQ");
		final int interval = ConfigKit.option(args, "-int", 10000); 
		
		BrokerConfig brokerConfig = new BrokerConfig();
		brokerConfig.setServerAddress(serverAddress);
		Broker broker = new SingleBroker(brokerConfig);
		
		MqConfig config = new MqConfig(); 
		config.setBroker(broker);
		config.setMq(mq); 
		
		final AtomicLong counter = new AtomicLong(0);
		final AtomicLong lastMark = new AtomicLong(System.currentTimeMillis());
		for(int i=0;i<threadCount;i++){ 
			@SuppressWarnings("resource")
			Consumer c = new Consumer(config);  
			c.onMessage(new MessageHandler() { 
				@Override
				public void handle(Message msg, Session sess) throws IOException {
					counter.incrementAndGet();
					long curr = counter.get();
					if(curr %interval == 0){
						long start = lastMark.get();
						lastMark.set(System.currentTimeMillis());
						long end = System.currentTimeMillis();
						System.out.format("Consumed:%d, QPS: %.4f\n", curr, interval*1000.0/(end-start) );
					}
				}
			}); 
			c.start(); 
		}
	} 
}

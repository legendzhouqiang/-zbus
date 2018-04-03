package io.zbus.mq;

import java.util.HashMap;
import java.util.Map;

import io.zbus.net.EventLoop;

public class Sub2 {
	
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		EventLoop loop = new EventLoop();  
		MqClient ws = new MqClient("ws://localhost:15555", loop);

		ws.onMessage = msg -> {
			System.out.println(msg); 
		};

		ws.onOpen = () -> { 
			Map<String, Object> req = new HashMap<>();
			req.put("cmd", "sub"); 
			req.put("topic", "/abc"); 
			 
			ws.sendMessage(req);
		};

		ws.connect();
	} 
}
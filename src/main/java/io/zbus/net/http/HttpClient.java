package io.zbus.net.http;
 

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.zbus.net.Client;
import io.zbus.net.ErrorHandler;
import io.zbus.net.EventLoop;
import io.zbus.net.MessageHandler;

public class HttpClient extends Client<HttpMsg, HttpMsg> {
	
	public HttpClient(URI uri, final EventLoop loop) {
		super(uri, loop);
		setup();
	}
	
	public HttpClient(String address, final EventLoop loop) {
		super(address, loop);
		setup();
	} 
	
	private void setup(){
		codec(p -> {
			p.add(new HttpRequestEncoder());
			p.add(new HttpResponseDecoder());
			p.add(new HttpObjectAggregator(loop.getPackageSizeLimit()));
			p.add(new HttpMsgClientCodec());
		});

		onClose = null; // Disable auto reconnect
		onError = null;
	}
	
	private void fillCommonHeader(HttpMsg req){
		req.setHeader("host", host);
	}

	public HttpMsg request(HttpMsg req) throws IOException, InterruptedException {
		fillCommonHeader(req);
		return request(req, 3000);
	}

	public HttpMsg request(HttpMsg req, long timeout) throws IOException, InterruptedException {
		fillCommonHeader(req);
		CountDownLatch countDown = new CountDownLatch(1);
		AtomicReference<HttpMsg> res = new AtomicReference<HttpMsg>();
		onMessage = resp -> {
			res.set(resp);
			countDown.countDown();
		};
		
		onOpen = ()->{
			sendMessage(req);
		}; 
		
		connect(); 
		
		countDown.await(timeout, TimeUnit.MILLISECONDS);
		if(res.get() == null){
			String msg = String.format("Timeout(%dms) for request:\n", timeout);
			throw new IOException(msg + req);
		}
		return res.get();
	}

	public void request(HttpMsg req, final MessageHandler<HttpMsg> messageHandler,
			final ErrorHandler errorHandler) {
		fillCommonHeader(req);
		
		onMessage = messageHandler;
		onError = errorHandler;
		
		onOpen = ()->{
			sendMessage(req);
		}; 
		
		connect();  
	}  
}

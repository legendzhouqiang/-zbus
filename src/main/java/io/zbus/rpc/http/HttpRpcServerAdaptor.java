package io.zbus.rpc.http;

import java.io.IOException;

import com.alibaba.fastjson.JSON;

import io.zbus.kit.HttpKit;
import io.zbus.kit.HttpKit.UrlInfo;
import io.zbus.net.ServerAdaptor;
import io.zbus.net.Session;
import io.zbus.net.http.HttpMessage;
import io.zbus.rpc.Request;
import io.zbus.rpc.Response;
import io.zbus.rpc.RpcProcessor;

public class HttpRpcServerAdaptor extends ServerAdaptor { 
	protected final RpcProcessor processor;  
	
	public HttpRpcServerAdaptor(RpcProcessor processor) {
		this.processor = processor;
	}
	
	@Override
	public void onMessage(Object msg, Session sess) throws IOException { 
		HttpMessage reqMsg = null;
		Request request = null;
		boolean writeHttp = true;
		if(msg instanceof HttpMessage){
			reqMsg = (HttpMessage)msg;
			request = handleUrlMessage(reqMsg);
			if(request == null){
				request = JSON.parseObject(reqMsg.getBodyString(), Request.class);
			} 
		} else if(msg instanceof byte[]){
			request = JSON.parseObject((byte[])msg, Request.class); 
			writeHttp = false;
		}   
		
		Response response = processor.process(request);  
		
		byte[] data = JSON.toJSONBytes(response);
		
		if(writeHttp){
			HttpMessage resMsg = new HttpMessage();
			resMsg.setStatus(200);
			resMsg.setBody(data);
			sess.write(resMsg);
		} else {
			sess.write(data);
		}
	} 
	
	protected Request handleUrlMessage(HttpMessage msg) {
		String url = msg.getUrl(); 
    	if(url == null || "/".equals(url)){ 
    		return null;
    	} 
    	if(msg.getBody() != null) return null;
    	
		UrlInfo info = HttpKit.parseUrl(url);     
		
		Request req = new Request();
    	if(info.path.size()>=1){
    		req.properties.put(Request.MODULE, info.path.get(0));
    	}
    	if(info.path.size()>=2){
    		req.command = info.path.get(1);
    	} 
    	
    	if(info.path.size()>2){ 
    		for(int i=0;i<info.path.size()-2;i++){
    			req.params.add(info.path.get(2+i));
    		} 
    	}   
    	return req;
	}
}

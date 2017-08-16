package io.zbus.transport.http;

import java.util.List;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.zbus.transport.CodecInitializer;
import io.zbus.transport.CompositeClient;
import io.zbus.transport.EventLoop;
import io.zbus.transport.IoAdaptor;
import io.zbus.transport.inproc.InprocClient;
import io.zbus.transport.tcp.TcpClient;
import io.zbus.transport.tcp.TcpClient.HeartbeatMessageBuilder;

public class MessageClient extends CompositeClient<Message, Message>{ 
	protected int hearbeatInterval = 60000; //60s
	
	public MessageClient(String address, final EventLoop loop){ 
		TcpClient<Message, Message> tcp = new TcpClient<Message, Message>(address, loop);
		support = tcp;
		
		tcp.codec(new CodecInitializer() {
			@Override
			public void initPipeline(List<ChannelHandler> p) {
				p.add(new HttpRequestEncoder()); 
				p.add(new HttpResponseDecoder());  
				p.add(new HttpObjectAggregator(loop.getPackageSizeLimit()));
				p.add(new MessageCodec());
			}
		}); 
		
		tcp.startHeartbeat(hearbeatInterval, new HeartbeatMessageBuilder<Message>() { 
			@Override
			public Message build() { 
				Message hbt = new Message();
				hbt.setCommand(Message.HEARTBEAT);
				return hbt;
			} 
		});  
	}
	
	public MessageClient(IoAdaptor serverIoAdaptor){ 
		support = new InprocClient<Message, Message>(serverIoAdaptor);
	} 
	
	//IPC support TODO 
}
 

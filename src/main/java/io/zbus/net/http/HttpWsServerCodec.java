package io.zbus.net.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;
import io.zbus.kit.logging.Logger;
import io.zbus.kit.logging.LoggerFactory;
import io.zbus.net.http.HttpMessage.FileForm; 


public class HttpWsServerCodec extends MessageToMessageCodec<Object, Object> {
	private static final Logger log = LoggerFactory.getLogger(HttpWsServerCodec.class); 
	private WebSocketServerHandshaker handshaker;

	//File upload
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed
    private HttpPostRequestDecoder decoder; 

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file // on exit (in normal // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on  exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    } 
    
    
    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
    	return msg instanceof HttpMessage || msg instanceof byte[];
    }
     
    
	@Override
	protected void encode(ChannelHandlerContext ctx, Object obj,  List<Object> out) throws Exception {
		//1) WebSocket mode  
		if(obj instanceof byte[]){
			if(handshaker == null){ 
				log.warn("Handshake not finished");
				return;
			}
			byte[] msg = (byte[])obj;
			ByteBuf buf = Unpooled.wrappedBuffer(msg);
			WebSocketFrame frame = new TextWebSocketFrame(buf);
			out.add(frame); 
			return; 
		} 
		
		if(!(obj instanceof HttpMessage)){
			log.warn("HttpMessage required");
			return;
		} 
		
		//2) HTTP mode  
		FullHttpMessage httpMessage = null;  
		HttpMessage msg = (HttpMessage)obj;
		if (msg.getStatus() == null) {// as request
			httpMessage = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(msg.getMethod()),
					msg.getUrl()); 
		} else {// as response
			httpMessage = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
					HttpResponseStatus.valueOf(Integer.valueOf(msg.getStatus())));
		}
		//content-type and encoding
		String contentType = msg.getHeader(HttpMessage.CONTENT_TYPE); 
		String encoding = msg.getHeader(HttpMessage.ENCODING);
		if(encoding != null){
			encoding = "utf-8";
			if(contentType == null) {
				contentType = "text/plain";
			}
			contentType += "; charset=" + encoding;
		}
		if(contentType != null){
			httpMessage.headers().set(HttpMessage.CONTENT_TYPE, contentType);
		}
		
		for (Entry<String, String> e : msg.getHeaders().entrySet()) {
			if(e.getKey().equalsIgnoreCase(HttpMessage.CONTENT_TYPE)) continue;
			if(e.getKey().equalsIgnoreCase(HttpMessage.ENCODING)) continue;
			
			httpMessage.headers().add(e.getKey().toLowerCase(), e.getValue());
		}
		if (msg.getBody() != null) {
			httpMessage.content().writeBytes(msg.getBody());
		}

		out.add(httpMessage);
	}
	
	@Override
	protected void decode(ChannelHandlerContext ctx, Object obj, List<Object> out) throws Exception {
		//1) WebSocket mode
		if(obj instanceof WebSocketFrame){
			byte[] msg = decodeWebSocketFrame(ctx, (WebSocketFrame)obj);
			if(msg != null){
				out.add(msg);
			}
			return;
		}
		
		//2) HTTP mode
		if(!(obj instanceof io.netty.handler.codec.http.HttpMessage)){
			throw new IllegalArgumentException("HttpMessage object required: " + obj);
		}
		 
		io.netty.handler.codec.http.HttpMessage httpMsg = (io.netty.handler.codec.http.HttpMessage) obj; 
		HttpMessage msg = decodeHeaders(httpMsg); 
		String contentType = msg.getHeader(HttpMessage.CONTENT_TYPE);
		
		//Body
		ByteBuf body = null;
		if (httpMsg instanceof FullHttpMessage) {
			FullHttpMessage fullReq = (FullHttpMessage) httpMsg;
			body = fullReq.content();
		}
		
		//Special case for file uploads
		if(httpMsg instanceof HttpRequest 
				&& contentType != null 
				&& contentType.startsWith(HttpMessage.CONTENT_TYPE_UPLOAD) ){
			if(body != null){
				body = body.duplicate(); //read form will change the body
			}
			
			HttpRequest req = (HttpRequest) httpMsg;
			decoder = new HttpPostRequestDecoder(factory, req);   
			handleUploadMessage(httpMsg, msg);
			out.add(msg); 
		}  
		
		if (body != null) { 
			int size = body.readableBytes();
			if (size > 0) {
				byte[] data = new byte[size];
				body.readBytes(data);
				msg.setBody(data);
			}
		}

		out.add(msg);
	} 

	private HttpMessage decodeHeaders(io.netty.handler.codec.http.HttpMessage httpMsg){
		HttpMessage msg = new HttpMessage();
		Iterator<Entry<String, String>> iter = httpMsg.headers().iteratorAsString();
		while (iter.hasNext()) {
			Entry<String, String> e = iter.next();
			if(e.getKey().equalsIgnoreCase(HttpMessage.CONTENT_TYPE)){ //encoding and type
				String[] typeInfo = httpContentType(e.getValue());
				msg.setHeader(HttpMessage.CONTENT_TYPE, typeInfo[0]); 
				if(msg.getHeader(HttpMessage.ENCODING) == null) {
					msg.setHeader(HttpMessage.ENCODING, typeInfo[1]);
				}
			} else {
				msg.setHeader(e.getKey().toLowerCase(), e.getValue());
			} 
		}  

		if (httpMsg instanceof HttpRequest) {
			HttpRequest req = (HttpRequest) httpMsg;
			msg.setMethod(req.method().name());
			msg.setUrl(req.uri());
		} else if (httpMsg instanceof HttpResponse) {
			HttpResponse resp = (HttpResponse) httpMsg;
			int status = resp.status().code();
			msg.setStatus(status);
		}
		return msg;
	}
	
	private void handleUploadMessage(io.netty.handler.codec.http.HttpMessage httpMsg, HttpMessage uploadMessage) throws IOException{
		if (httpMsg instanceof HttpContent) { 
            HttpContent chunk = (HttpContent) httpMsg;
            decoder.offer(chunk); 
            try {
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next();
                    if (data != null) {
                        try { 
                        	handleUploadFile(data, uploadMessage);
                        } finally {
                            data.release();
                        }
                    }
                }
            } catch (EndOfDataDecoderException e1) { 
            	//ignore
            }
            
            if (chunk instanceof LastHttpContent) {  
            	resetUpload();
            }
        }
	}
	
	private void handleUploadFile(InterfaceHttpData data, HttpMessage uploadMessage) throws IOException{
		FileForm fileForm = uploadMessage.fileForm;
        if(uploadMessage.fileForm == null){
        	uploadMessage.fileForm = fileForm = new FileForm();
        }
        
		if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            fileForm.attributes.put(attribute.getName(), attribute.getValue());
            return;
		}
		
		if (data.getHttpDataType() == HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            HttpMessage.FileUpload file = new HttpMessage.FileUpload();
            file.fileName = fileUpload.getFilename();
            file.contentType = fileUpload.getContentType();
            file.data = fileUpload.get(); 
            
            List<HttpMessage.FileUpload> uploads = fileForm.files.get(data.getName());
            if(uploads == null){
            	uploads = new ArrayList<HttpMessage.FileUpload>();
            	fileForm.files.put(data.getName(), uploads);
            }
            uploads.add(file);
		}
	}
	
	private void resetUpload() {  
        decoder.destroy();
        decoder = null;
    } 
	
	 
	private static String[] httpContentType(String value){
		String type="text/plain", charset="utf-8";
		String[] bb = value.split(";");
		if(bb.length>0){
			type = bb[0].trim();
		}
		if(bb.length>1){
			String[] bb2 = bb[1].trim().split("=");
			if(bb2[0].trim().equalsIgnoreCase("charset")){
				charset = bb2[1].trim();
			}
		}
		return new String[]{type, charset};
	} 
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if(msg instanceof FullHttpRequest){
			FullHttpRequest req = (FullHttpRequest) msg; 
			
			//check if websocket upgrade encountered
			if(req.headers().contains("Upgrade") || req.headers().contains("upgrade")) { 
				WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
						getWebSocketLocation(req, ctx), null, true, 1024 * 1024 * 1024);
				handshaker = wsFactory.newHandshaker(req);
				if (handshaker == null) {
					WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
				} else {
					handshaker.handshake(ctx.channel(), req);
				}
				return;
			}
		}
		
		super.channelRead(ctx, msg);
	}
 
	private byte[] decodeWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			return null;
		}
		
		if (frame instanceof PingWebSocketFrame) {
			ctx.write(new PongWebSocketFrame(frame.content().retain()));
			return null;
		}
		
		if (frame instanceof TextWebSocketFrame) {
			TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
			return parseMessage(textFrame.content());
		}
		
		if (frame instanceof BinaryWebSocketFrame) {
			BinaryWebSocketFrame binFrame = (BinaryWebSocketFrame) frame;
			return parseMessage(binFrame.content());
		}
		
		log.warn("Message format error: " + frame); 
		return null;
	}
	
	private byte[] parseMessage(ByteBuf buf){
		int size = buf.readableBytes();
		byte[] data = new byte[size];
		buf.readBytes(data); 
		return data;
	}

	private static String getWebSocketLocation(io.netty.handler.codec.http.HttpMessage req, ChannelHandlerContext ctx) {
		String location = req.headers().get(HttpHeaderNames.HOST) + "/";
		if (ctx.pipeline().get(SslHandler.class) != null) {
			return "wss://" + location;
		} else {
			return "ws://" + location;
		}
	}
}
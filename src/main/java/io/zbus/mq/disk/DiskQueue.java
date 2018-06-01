package io.zbus.mq.disk;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zbus.kit.JsonKit;
import io.zbus.mq.Protocol;
import io.zbus.mq.disk.support.DiskMessage;
import io.zbus.mq.disk.support.Index;
import io.zbus.mq.disk.support.QueueWriter;
import io.zbus.mq.model.ChannelReader;
import io.zbus.mq.model.MessageQueue.AbstractMessageQueue;

public class DiskQueue extends AbstractMessageQueue {
	private static final Logger logger = LoggerFactory.getLogger(DiskQueue.class); 
	final Index index;     
	private final QueueWriter writer;   
	
	public DiskQueue(String mqName, File baseDir) throws IOException { 
		super(mqName); 
		File mqDir = new File(baseDir, mqName);
		index = new Index(mqDir);
		writer = new QueueWriter(index);
		
		loadChannels();
	} 
	
	@Override
	protected ChannelReader buildChannelReader(String channelId) throws IOException {
		return new DiskChannelReader(channelId, this);
	}
	
	private void loadChannels() {
		File[] channelFiles = index.getReaderDir().listFiles( pathname-> {
			return Index.isReaderFile(pathname); 
		});
        if (channelFiles != null && channelFiles.length> 0) {
            for (File channelFile : channelFiles) {  
            	String channelName = channelFile.getName();
            	channelName = channelName.substring(0, channelName.lastIndexOf('.'));  
            }
        } 
	}
	 
	private DiskMessage diskMessage(Map<String, Object> message) {
		DiskMessage diskMsg = new DiskMessage();
		diskMsg.id = (String)message.get(Protocol.ID);
		diskMsg.tag = (String)message.get(Protocol.TOPIC);
		diskMsg.body = JsonKit.toJSONBytes(message, "UTF8");
		return diskMsg;
	}
	@Override
	public void write(Map<String, Object> message) { 
		try {  
			writer.write(diskMessage(message)); 
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} 
	} 
	
	@Override
	public void write(List<Map<String, Object>> messages) {
		try { 
			DiskMessage[] diskMsgs = new DiskMessage[messages.size()];
			for(int i=0;i<messages.size();i++) { 
				diskMsgs[i] = diskMessage(messages.get(i)); 
			} 
			writer.write(diskMsgs);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} 
	} 

	@Override
	public Integer getMask() {
		return index.getMask(); 
	}
	
	@Override
	public void setMask(Integer mask) { 
		if(mask == null) return;
		index.setMask(mask);
	}

	@Override
	public void flush() { 
		
	}
	
	@Override
	public void destroy() { 
		try {
			writer.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		try {
			index.delete();
		} catch (IOException e) { 
			logger.error(e.getMessage(), e);
		} 
	} 
}

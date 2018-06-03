package io.zbus.mq.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zbus.mq.Protocol;

/** 
 *  
 * MessageQueue:
 * 
 *      queue_name |||||||||||(message/topic)|||||||||||||||
 *                 ------- channel1
 *                 ------- channel2
 *                 
 * By default: 
 *   channel = unique generated, each subscriber with unique channel
 *   
 * Noted: Topic is an attribute of a message, not a message queue. A Message Queue may contains messages with different topics
 * 
 * Queue   -- message container, with name as identifier
 * Channel -- subscriber isolation, each channel share same message pointer for reading
 * Topic   -- message's topic, subscriber may filter on it. e.g. /abc, follows MQTT standard
 * 
 * Flexible messaging models based on Channel
 * 
 * 1) PubSub: default, each subscriber generated unique channel
 * 2) LoadBalance: subscribers share same channel
 * 3) Mixed: each group of subscribers share a same channel 
 * 
 * 
 * @author leiming.hong
 *
 */
public interface MessageQueue { 
	/**
	 * Name of message queue, identifier
	 * @return name of mq
	 */
	String name();
	
	/**
	 * Type of message queue, identifier
	 * @return type of mq
	 */
	String type();
	
	/**
	 * Message count
	 * @return size of message queue
	 */
	long size();
	
	/**
	 * Info of Message queue, memory|disk|db
	 * @return info of mq
	 */ 
	Map<String, Object> info();
	/**
	 * Write message to queue
	 * 
	 * @param message message
	 */
	void write(Map<String, Object> message); 
	
	/**
	 * Batch write message to queue
	 * 
	 * @param message message list
	 */
	void write(List<Map<String, Object>> messages); 
	
	/**
	 * Read message from queue by channel 
	 * 
	 * @param channelId id of channel 
	 * @return message
	 */
	Map<String, Object> read(String channelId) throws IOException;   
	
	
	/**
	 * Batch rRead message from queue by channel
	 * If the length of result is less than count, queue end reached.
	 * 
	 * @param channelId id of channel
	 * @param count maximum count of message to read
	 * @return list of message
	 */
	List<Map<String, Object>> read(String channelId, int count) throws IOException;   
	
	/**
	 * Add or update channel to the queue
	 * @param channel Channel object to save
	 */
	void saveChannel(Channel channel) throws IOException;
	
	/**
	 * Remove channel by channel's Id
	 * 
	 * @param channelId
	 */
	void removeChannel(String channelId) throws IOException; 
	
	/**
	 * Get channel by id
	 * 
	 * @param channelId id of channel
	 * @return Channel object
	 */
	Channel channel(String channelId);
	
	/** 
	 * @return all channels inside of the queue
	 */
	Set<String> channels(); 
	
	/** 
	 * @return attribute map of the queue
	 */
	Integer getMask();
	
	/**
	 * Set mask value
	 * @param mask
	 */
	void setMask(Integer mask);
	
	/**
	 * Flush message in memory to disk if support
	 */
	void flush();
	
	/**
	 * Destroy of this queue
	 */
	void destroy();
	
	
	public static abstract class AbstractMessageQueue implements MessageQueue {
		private static final Logger logger = LoggerFactory.getLogger(AbstractMessageQueue.class);  
		protected Map<String, ChannelReader> channelTable = new HashMap<>(); 
		protected final String name;
		
		public AbstractMessageQueue(String name) {
			this.name = name;
		}
		
		@Override
		public String name() { 
			return name;
		}  
		
		@Override
		public Map<String, Object> info() {
			Map<String, Object> info = new HashMap<>();
			info.put(Protocol.MQ, name());
			info.put(Protocol.MQ_TYPE, type());
			info.put(Protocol.MQ_MASK, getMask());
			info.put(Protocol.MQ_SIZE, size());
			return info;
		}
	 
		@Override
		public Map<String, Object> read(String channelId) throws IOException {
			ChannelReader reader = channelTable.get(channelId);
			if(reader == null) {
				throw new IllegalArgumentException("Missing channel: " + channelId);
			}    
			return reader.read(); 
		}

		@Override
		public List<Map<String, Object>> read(String channelId, int count) throws IOException { 
			ChannelReader reader = channelTable.get(channelId);
			if(reader == null) {
				throw new IllegalArgumentException("Missing channel: " + channelId);
			}    
			return reader.read(count); 
		}

		@Override
		public Channel channel(String channelId) { 
			ChannelReader reader = channelTable.get(channelId);
			if(reader == null) return null;
			return reader.channel();
		}
		
		protected abstract ChannelReader buildChannelReader(String channelId) throws IOException;

		@Override
		public void saveChannel(Channel channel) { 
			try {
				ChannelReader dc = channelTable.get(channel.name);
				if(dc == null) {
					dc = buildChannelReader(channel.name);
					channelTable.put(channel.name, dc);
				} 
				
				if(channel.mask != null) {
					dc.setMask(channel.mask); 
				}
				if(channel.filter != null) {
					dc.setFilter(channel.filter);
				}
				if(channel.offset != null) {
					dc.seek(channel.offset, channel.offsetChecksum);
				}  
				
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}

		@Override
		public void removeChannel(String channelId) throws IOException { 
			ChannelReader dc = channelTable.remove(channelId);
			if(dc != null) {
				dc.destroy();
			}
		}

		@Override
		public Set<String> channels() {  
			return channelTable.keySet();
		}
	 
		@Override
		public void flush() { 
			
		} 
	}
}

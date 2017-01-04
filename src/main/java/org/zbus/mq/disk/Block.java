package org.zbus.mq.disk;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
 
class Block implements Closeable {  
	private final Index index; 
	private final int blockNumber; 
	
	private RandomAccessFile diskFile; 
	private final Lock lock = new ReentrantLock();  
	
	Block(Index index, File file, int blockNumber) throws IOException{   
		this.index = index;
		this.blockNumber = blockNumber;
		if(this.blockNumber < 0){
			throw new IllegalArgumentException("blockNumber should>=0 but was " + blockNumber);
		}
		if(this.blockNumber >= index.getBlockCount()){
			throw new IllegalArgumentException("blockNumber should<"+index.getBlockCount() + " but was " + blockNumber);
		}
		
		if(!file.exists()){
			File dir = file.getParentFile();
			if(!dir.exists()){
				dir.mkdirs();
			}  
		}  
		
		this.diskFile = new RandomAccessFile(file,"rw");   
	}   
	
	
	
	public int write(byte[] data) throws IOException{
		try{
			lock.lock();
			
			int endOffset = endOffset();
			if(endOffset >= Index.BlockMaxSize){
				return 0;
			}
			diskFile.seek(endOffset);
			diskFile.writeLong(endOffset);
			diskFile.writeInt(data.length);
			diskFile.write(data);
			endOffset += 8 + 4 + data.length;  
			
			index.writeEndOffset(endOffset);
			
			index.newDataAvailable.get().countDown();
			index.newDataAvailable.set(new CountDownLatch(1));
			return data.length;
		} finally {
			lock.unlock();
		}
	}  

	
    public byte[] read(int pos) throws IOException{
    	try{
			lock.lock();
			diskFile.seek(pos); 
			diskFile.readLong(); //offset 
			int size = diskFile.readInt();
			byte[] data = new byte[size];
			diskFile.read(data, 0, size);
			return data;
    	} finally {
			lock.unlock();
		}
	}
    
    /**
     * Check if endOffset of block reached max block size allowed
     * @return true if max block size reached, false other wise
     * @throws IOException 
     */
    public boolean isFull() throws IOException{
    	return endOffset() >= Index.BlockMaxSize;
    }
    
    /**
     * Check if offset reached the end, for read.
     * @param offset offset of reading
     * @return true if reached the end of block(available data), false otherwise
     * @throws IOException 
     */
    public boolean isEndOfBlock(int offset) throws IOException{  
    	return offset >= endOffset();
    }
    
    private int endOffset() throws IOException{
    	return index.readOffset(blockNumber).endOffset;
    }
	
    public int getBlockNumber() {
		return blockNumber;
	}
    
	@Override
	public void close() throws IOException {  
		this.diskFile.close();
	} 
}

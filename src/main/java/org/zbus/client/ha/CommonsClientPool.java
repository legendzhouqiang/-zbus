package org.zbus.client.ha;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.remoting.ClientDispachterManager;
import org.remoting.RemotingClient;
import org.zbus.client.ClientPool;

public class CommonsClientPool implements ClientPool{
	private final ObjectPool<RemotingClient> pool;
	private final GenericObjectPoolConfig config;
	private final String broker;
	
	public CommonsClientPool(GenericObjectPoolConfig config, String broker, ClientDispachterManager clientMgr){
		this.config = config;
		this.broker = broker;
		RemotingClientFactory factory = new RemotingClientFactory(clientMgr, this.broker);	 
		this.pool = new GenericObjectPool<RemotingClient>(factory, this.config);
	}
	
	public CommonsClientPool(GenericObjectPoolConfig config, String broker) throws IOException {
		this(config, broker, defaultClientDispachterManager());
	}  
	
	public CommonsClientPool(PoolConfig config, String broker) throws IOException {
		this(config, broker, null);
	}  
	
	public CommonsClientPool(PoolConfig config, String broker, ClientDispachterManager clientMgr){
		this(toObjectPoolConfig(config), broker, clientMgr);
	}
	
	private static ClientDispachterManager defaultClientDispachterManager() throws IOException{
		ClientDispachterManager clientMgr = new ClientDispachterManager();
		clientMgr.start();
		return clientMgr;
	}
	
	private static GenericObjectPoolConfig toObjectPoolConfig(PoolConfig config){
		//TODO
		return new GenericObjectPoolConfig();
	}
	
 
	public RemotingClient borrowClient(String mq) throws Exception {
		return pool.borrowObject();
	}

	public List<RemotingClient> borrowEachClient(String mq) throws Exception {
		return Arrays.asList(borrowClient(mq));
	}
	
	public void invalidateClient(RemotingClient client) throws Exception{
		pool.invalidateObject(client);
	}
	
	public void returnClient(RemotingClient client) throws Exception {
		pool.returnObject(client);  
	}
	
	public void returnClient(List<RemotingClient> clients) throws Exception { 
		for(RemotingClient client : clients){
			returnClient(client);
		}
	}
	
	
	public void destroy() { 
		pool.close();
	}

	
}

class RemotingClientFactory extends BasePooledObjectFactory<RemotingClient> {
	
	private final ClientDispachterManager cliengMgr;
	private final String broker; 
	
	public RemotingClientFactory(final ClientDispachterManager clientMgr, final String broker){
		this.cliengMgr = clientMgr;
		this.broker = broker;
	}
	
	
	@Override
	public RemotingClient create() throws Exception { 
		return new RemotingClient(broker, cliengMgr);
	}

	@Override
	public PooledObject<RemotingClient> wrap(RemotingClient obj) { 
		return new DefaultPooledObject<RemotingClient>(obj);
	} 
}
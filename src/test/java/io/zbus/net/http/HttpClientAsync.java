package io.zbus.net.http;

import io.zbus.kit.JsonKit;
import io.zbus.kit.StrKit;
import io.zbus.net.EventLoop;
import io.zbus.rpc.Request;

public class HttpClientAsync {

	public static void main(String[] args) throws Exception, InterruptedException {
		EventLoop loop = new EventLoop();

		HttpClient client = new HttpClient("http://localhost", loop);

		HttpMessage reqMsg = new HttpMessage();
		Request req = new Request();
		req.setMethod("echo");
		req.setParams(new Object[]{"hi"});
		req.setId(StrKit.uuid());

		reqMsg.setBody(JsonKit.toJSONString(req));

		client.request(reqMsg, resp -> {
			System.out.println(resp);

			client.close();
			loop.close();
		}, e -> {
			client.close();
			loop.close();
		});
	}
}

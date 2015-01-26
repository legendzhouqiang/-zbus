
#include "zbus.h" 

int main_consumer(int argc, char* argv[]){
	rclient_t* client = rclient_connect("127.0.0.1:15555", 10000);
	consumer_t* consumer = consumer_new(client, "MyMQ2", MODE_MQ);
	msg_t*res = NULL;
	int rc;
	while(1){
		rc = consumer_recv(consumer, &res, 10000);
		if(rc<0) continue; 
		if(rc>=0 && res){
			msg_print(res); 
			msg_destroy(&res);
		}
	}
	getchar();
	consumer_destroy(&consumer);
	rclient_destroy(&client);
	return 0;
}
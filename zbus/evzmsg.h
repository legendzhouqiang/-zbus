#ifndef __EVZMSG_H_
#define __EVZMSG_H_

#include "platform.h"
#include "msg.h" 
#include "hash.h"
#include <event2/buffer.h>

#ifdef __cplusplus
extern "C" {
#endif 

typedef struct zmsg zmsg_t;

#define HEAD_SPLIT "\r\n\r\n"


struct zmsg {
	char* sender;    //��Ϣ��Դ��ַ��ʶ
	char* recver;    //��ϢĿ���ַ��ʶ
	char* msgid;     //��ϢID(�ɷ�����ָ����������Ϣ�����ڲ����ã�
	char* mq;		 //��ϢͶ�ݵ�ַ����Ϣ���б�ʶ��
	char* token;     //���ʿ�����
	
	char* command;   //��Ϣ������

	
	char* status;    //ָ����Ϣ���״̬��Ϣ����200,OK
	char* head;      //��Ϣͷ������̬����չ, ����Key-Value��ֵ
	void* body;      //��Ϣ��
	size_t body_size;//��Ϣ��body��С


	hash_t* head_kvs;//�ڲ�ʹ�ã���head=null, ���� head_set/get�ᴥ��head=NULL
};

zmsg_t* zmsg_new();
void zmsg_destroy(zmsg_t** self_p);

void zmsg_sender(zmsg_t* self, char* sender);
void zmsg_recver(zmsg_t* self, char* recver);
void zmsg_msgid(zmsg_t* self, char* msgid);
void zmsg_mq(zmsg_t* self, char* mq);
void zmsg_token(zmsg_t* self, char* token);

void zmsg_command(zmsg_t* self, char* command); 

void zmsg_status(zmsg_t* self, char* status);
void zmsg_body(zmsg_t* self, char* body);
void zmsg_body_blob(zmsg_t* self, void* body, size_t size);
void zmsg_body_nocopy(zmsg_t* self, void* body, size_t size);
void zmsg_print(zmsg_t*, FILE*);

char* zmsg_head_str(zmsg_t* self);
void  zmsg_head_set(zmsg_t* self, char* key, char* val);
char* zmsg_head_get(zmsg_t* self, char* key);


void evbuffer_add_frame(struct evbuffer* buf, frame_t* frame);
void evbuffer_add_msg(struct evbuffer* buf, msg_t* msg);
void evbuffer_add_zmsg(struct evbuffer* buf, zmsg_t* zmsg);

frame_t* evbuffer_read_frame(struct evbuffer* buf);
msg_t* evbuffer_read_msg(struct evbuffer* buf);
zmsg_t* evbuffer_read_zmsg(struct evbuffer* buf);

msg_t* to_msg(zmsg_t* svcmsg);
zmsg_t* to_zmsg(msg_t* zmsg);



#ifdef __cplusplus
}
#endif

#endif

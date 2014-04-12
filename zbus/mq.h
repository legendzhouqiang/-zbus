#ifndef __MQ_H_
#define __MQ_H_

#include "platform.h" 
#include "list.h"
#include "hash.h"
#include "evzmsg.h"
#include <event2/bufferevent.h>

#ifdef __cplusplus
extern "C" {
#endif
 
#define MQ_ROLLER 0
#define MQ_MATCH  1
#define MQ_FILTER 2


typedef struct mq mq_t;
typedef struct sess sess_t;



/*
 * ��Ϣ����
 * ���в����㷨���Ӷ�O(1): ������Ϣ��������Ϣ��ָ��Session������Ϣ...
 * ��Ϣ����ģʽ��1) Roller, ��Ϣ������Round-Robin������ȡ
 *               2) Match�����ݶ�����ID��ȡ��Ϣ
 *               3) Filter, ���ݶ�����ָ��������ȡ��Ϣ
 */
struct mq {
	/* MQ��ʶ */
	char* name;
	/* ���ʿ����� */
	char* token;
	
	/* ��������Ϣ���м��ϣ��Խ����߱�ʶΪ��������Ϣֻ��ָ��Ȩ */
	hash_t* recv_msg_hash;
	/* ��Ϣ��������ȫ����Ϣ�б�����Ϣ������Ȩ*/
	list_t* glob_msg_list;
	
	/* ������ӳ�䣬��Sessionֻ��ָ��Ȩ */
	hash_t* recv_hash;
	/* �������б���Session������Ȩ*/
	list_t* recv_list;
	
	/* ��Ϣ����ģʽ��Roller(0), Match(1), Filter(2)*/
	int mode; 
};

/* 
 * ����Ĭ��Session��ʶ��Ĭ��ID=Socket��FD
 * ���Σ�default_sessid���������ṩ�ռ�
 */
void gen_default_sessid(char* default_sessid, struct bufferevent* bev);
/* 
 * ����Session��sessidΪNULLʱ������Ĭ�ϵ�ID, ��bevֻ��ָ��Ȩ
 */
sess_t* sess_new(char* sessid, struct bufferevent* bev);
void sess_destroy(sess_t** self_p); 


mq_t* mq_new(char* name, char* token, int mode);
void mq_destroy(mq_t** self_p);

/* 
 * ����ָ�������߱�ʶ����Ϣ��recver_id=NULL���ȡ��Ϣ�����е�ͷ����Ϣ
 * ����Ϣ����NULL
 * �㷨���Ӷ�O(1)
 */
zmsg_t* mq_fetch_msg(mq_t* self, char* recver);
/* 
 * ��Ϣ�����β����������Ϣrecver��ʶ������Ϣ��receiver��������Ϣ����β��
 * �㷨���Ӷ�O(1)
 */
void  mq_push_msg(mq_t* self, zmsg_t* zmsg);

/* 
 * ����Ϣ���еĶ����߶��У�recver=NULL��ʹ��Ĭ��ID
 * �㷨���Ӷ�O(1)
 */
void mq_put_recver(mq_t* self, char* recver, struct bufferevent* bev);

/* 
 * ����recver��ʶɾ�������ߣ�recver����ΪNULL
 * �㷨���Ӷ�O(1)
 */
void mq_rem_recver(mq_t* self, char* recver); 
/* 
 * ���ݶ����߱�ʶ��recver����ȡ�����ߣ��Ҳ�������NULL
 * �㷨���Ӷ�O(1)
 */
sess_t* mq_get_recver(mq_t* self, char* recver); 

/* 
 * �ַ���Ϣ��������Ϣ��recver��ʶ�ַ�����Ϣ���еĶ����߶���
 * ��Ϣ����Ȩ�ͷ� 1��������Ķ����ߣ���Ϣ���ͳ�ȥ
 *                2) ������Ķ����ߣ���Ϣ���������߶���
 */
void mq_dispatch_zmsg(mq_t* self, zmsg_t* zmsg);
/* 
 * �ַ�Session��ʶָ����������Ϣ
 * �ַ���Sesion��ʶָ����Session
 */
void mq_dispatch_recver(mq_t* self, char* recver);


#ifdef __cplusplus
}
#endif

#endif

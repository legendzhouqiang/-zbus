REM -msmq_s MSMQ���ȷ�����IP, ���� -msmq_s127.0.0.1
REM -msmq_c �����������IP, ���� -msmq_c127.0.0.1 
REM -msmq_t MSMQ��ʱ���ã���λ�����룩, ���� -msmq_t10000

REM -b	 zbus�������ߵ�ַ��ip:port��ʽ��, ���� -b172.24.180.42:15555
REM -s	 ������ע�ᵽzbus���ߵı�ʶ����, ���� -sTrade 
REM -c	 ������ע�ᵽzbus���ߵĲ����߳���, ���� -c2 
REM -kreg	 zbusע����֤��, ���� -kregxyz 
REM -kacc	 ���ñ���������֤��, ���� -kaccxyz 
REM -log ������־�ļ���·��, ���� -logC:\logs 
REM -v	 �Ƿ����־, ���� -v1  
zbus-trade -blocalhost:15555 -msmq_s172.24.180.180 -msmq_c172.24.180.28 -msmq_t10000 -v1 -c2 -sTrade -loglogs
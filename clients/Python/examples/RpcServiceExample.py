#encoding=utf8 

'''
MyService is just a simple Python object
''' 
class MyService(object):  
    def getString(self, ping):
        return ping
     
    def echo(self, ping):
        return ping
     
    def save(self, user): 
        return 'OK'
          
    def plus(self, a, b): 
        return int(a) + int(b) 
    
    def testEncoding(self): 
        return u'中文' 


from zbus import Broker, Consumer, RpcProcessor 

p = RpcProcessor() 
p.add_module(MyService) #could be class or object
 

broker = Broker()
broker.add_tracker('localhost:15555') 
c = Consumer(broker, 'MyRpc')
c.on_message = p #RpcProcessor is callable
c.start()

package com.requestBroker;

import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import com.requestBroker.core.*;

/**
 * Hello world!
 * 
 */
public class Server {
	static class Work {
		Socket work;
		
		public Work(Context c) {
			work = c.socket(ZMQ.DEALER);
			work.connect("tcp://127.0.0.1:6661");
			
			ZFrame zf = new ZFrame(AbstractBroker.PPP_READY.getBytes());
			zf.send(work, 0);//先发一帧 说明work已经就绪
		}
		
		public void start(){
			long heartbeat_at = AbstractBroker.HEARTBEAT_INTERVAL*AbstractBroker.HEARTBEAT_LIVENESS+System.currentTimeMillis();
			PollItem[] pollItem = {new PollItem(work,Poller.POLLIN)}; 
			while( true ){
				ZMQ.poll(pollItem,1,-1);
				if( pollItem[0].isReadable() ){
					ZMsg ms = ZMsg.recvMsg(work);
					//一帧=心跳
					if( ms.size() == 1 ){
						if( ms.getLast().toString() .equals( AbstractBroker.PPP_HEARTBEAT ) ){
							ms.destroy();
							continue;
						}
						ms.removeLast();//client发送的请求
						byte[] rq = new byte[2560];
						ms.addLast(rq);//应答
						ms.send(work);
					}
				}
				//发送心跳给空闲的work
				if( System.currentTimeMillis() >= heartbeat_at ){
					ZFrame zf_ = new ZFrame(AbstractBroker.PPP_HEARTBEAT.getBytes());//发送一帧心跳
					work.send(zf_.getData(), 0);	
					heartbeat_at = AbstractBroker.HEARTBEAT_INTERVAL*AbstractBroker.HEARTBEAT_LIVENESS+System.currentTimeMillis();
				}
			}
		}
	}

	public static void main(String[] args) {
		Context ct = ZMQ.context(1);
		Work w = new Work(ct);
		w.start();
		
	}
}

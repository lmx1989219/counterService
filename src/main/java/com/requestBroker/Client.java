package com.requestBroker;

import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
 * 1W次请求 ，每次发送64byte ，响应2560byte ,处理约等于24m数据，平均一次请求耗时5.4s
 * request count:10000 read bytes :25600000 byte cost :5319 ms avg request cost:53ms
 * request count:22500 read bytes :57600000 byte cost :13160 ms avg request cost:87ms
 * 
 */
public class Client {
	public static void main(String[] args) {
		final Context c = ZMQ.context(1);
		final long begin = System.currentTimeMillis();
		final AtomicLong count = new AtomicLong(0);
		final AtomicLong count_ = new AtomicLong(0);
		final int reqNo = 5;
		final int sendNo = 4;
		
		for( int i=0;i<reqNo;++i ){
			final int m = i;
			new Thread(){
				public void run(){
					Socket req = c.socket(ZMQ.REQ);
					req.connect("tcp://127.0.0.1:6660");
//					byte[] rq = new byte[64];
					long len = 0;
					for( int j=0;j<sendNo;++j ){
						req.send((m+"").getBytes(),0);
						boolean isEnd = false;
						
						while( !isEnd ){
							PollItem[] pollItem = {new PollItem(req,Poller.POLLIN)}; 
							int rc = ZMQ.poll(pollItem,1,-1);
							if( rc == -1 )
								break;
							if( pollItem[0].isReadable() ){
								byte[] rep = req.recv();
								System.out.println("req"+m+" rep"+new String(rep));
								len = count.addAndGet(rep.length);
								count_.getAndIncrement();
								isEnd = true;
							}
						}
					}
					if( count_.get() == reqNo*sendNo )
						System.out.println("request count:"+reqNo*sendNo+" read bytes :"+len+" byte cost :"+(System.currentTimeMillis()-begin)+" ms"
								+" avg request cost:"+(System.currentTimeMillis()-begin)/(reqNo*sendNo)+"ms");
				}
			}.start();
		}
	}
}

package com.requestBroker.core;

import java.util.Iterator;
import java.util.LinkedList;

import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

public abstract class AbstractBroker {
	
	public static final long HEARTBEAT_INTERVAL = 3 ;
	public static final long HEARTBEAT_LIVENESS = 5000;
	public static final String PPP_READY = "PPP_READY";
	public static final String PPP_HEARTBEAT = "PPP_HEARTBEAT";
	
	@SuppressWarnings("static-access")
	public static void initBroker(){
		Context ct = ZMQ.context(1);
		Socket front = ct.socket(ZMQ.ROUTER);
		front.bind("tcp://*:6660");
		Socket back = ct.socket(ZMQ.ROUTER);
		back.bind("tcp://*:6661");
		//存放服务器端work
		LinkedList<Work> wks = new LinkedList<Work>();
		LinkedList<ZFrame> rq = new LinkedList<ZFrame>();
		long heartbeat_at = HEARTBEAT_INTERVAL*HEARTBEAT_LIVENESS+System.currentTimeMillis();
			while( true ){
				PollItem[] pollItem = {new PollItem(front,Poller.POLLIN),new PollItem(back,Poller.POLLIN)}; 
				int rc = ZMQ.poll(pollItem,2,-1);
				if( rc == -1 )
					break;
				//获取client请求
				if( pollItem[0].isReadable() ){
					ZMsg zms = ZMsg.recvMsg(front);
					ZFrame zf = zms.unwrap();//取出第一帧=socket地址
					rq.addFirst(zf);
					Work w = new Work(zf);
					if( wks.size() > 0 ){
						//包上一层信封 ,这里加入可用work地址，构造成 work地址+null+客户端content
						zms.push(w.getNextAvalibleWork(wks));
						zms.send(back);
					}
				}
				//获取work请求
				if( pollItem[1].isReadable() ){
					ZMsg zms = ZMsg.recvMsg(back);
					ZFrame zf = zms.unwrap();
					Work w = new Work(zf);
					w.workReady(w, wks);
					if( zms.size() == 1 ){
						ZFrame f = zms.getFirst();
						String bb = new String(f.getData());
						if( bb.equals( PPP_HEARTBEAT) || bb.equals( PPP_READY) ){
//							System.out.println("-------------------heart beat--------------------");
							zms.destroy();
						}
						else{
//							System.out.println("broker dispatch resp is end");
							//封装信封，归还给请求方
							zms.addFirst("");
							zms.addFirst(rq.removeFirst());
							zms.send(front);
						}
					}
				}

				if( wks.size() > 0 ){
					//发送心跳给空闲的work
					if( System.currentTimeMillis() > heartbeat_at ){
						Iterator<Work> zfs = wks.iterator();
						while( zfs.hasNext() ){
							Work first = zfs.next();
							ZFrame zf = first.server_address;
							back.send(zf.getData(), ZFrame.REUSE+ZFrame.MORE);

							ZFrame zf_ = new ZFrame(PPP_HEARTBEAT.getBytes());//发送一帧心跳
							back.send(zf_.getData(), 0);	
						}
						heartbeat_at = HEARTBEAT_INTERVAL*HEARTBEAT_LIVENESS+System.currentTimeMillis();
					}
					Work.findExpiryWork(wks);
				}
//				while( wks.iterator().hasNext() ){
//					Work w = wks.pop();
//					w.server_address.destroy();
//				}
			}
		}

	
	public static void main(String[] args) {
		initBroker();
	}
	
	static class Work{
		ZFrame server_address;	//套接口地址
		String socket_identify;	//套接口标识
		long expiry;			//过期时间
		
		public Work(ZFrame server_address) {
			this.server_address = server_address;
			this.socket_identify = server_address.strhex();
			this.expiry = System.currentTimeMillis()+HEARTBEAT_INTERVAL*HEARTBEAT_LIVENESS;
		}

		// 销毁 worker 结构，包括标识
		static public void destroyWork(Work w){
			assert w == null;
			if( w != null ){
				w.server_address.destroy();
				w.socket_identify = "";
				w = null;
			}
		}
		
		/**
		 *  worker 已就绪，将其移至列表末尾
		 * @param w
		 * @param wks
		 */
		static public void workReady(Work w, LinkedList<Work> wks){
			if( wks.size() > 0 ){
				Iterator<Work> zfs = wks.iterator();
				while( zfs.hasNext() ){
					Work first = zfs.next();
					if( first.socket_identify.equals(w.socket_identify) ){
						wks.remove(first);
						first.server_address.destroy();
						break;
					}
				}
			}
			wks.addLast(w);
		}
		
		/**
		 *  返回下一个可用的 worker 地址
		 * @param wks
		 * @return
		 */
		static public ZFrame getNextAvalibleWork(LinkedList<Work> wks){
			if( wks.size() > 0 ){
				ZFrame zf = wks.getFirst().server_address;
				if( zf != null ){
					return zf;
				}
			}
			return null;
		}
		
		/**
		 *  寻找并销毁已过期的worker。
		 *  由于列表中最旧的worker 排在最前，所以当找到第一个未过期的worker 时就停止
		 * @param wks
		 */
		static public void findExpiryWork(LinkedList<Work> wks){
			Iterator<Work> zfs = wks.iterator();
			while( zfs.hasNext() ){
				Work first = zfs.next();
				if( System.currentTimeMillis() < first.expiry )
					break;// worker 未过期，停止扫描
//				System.out.println("work "+first.server_address+" is over now");
				wks.remove(first);
				first.server_address.destroy();
			}
		}
		
	}
}

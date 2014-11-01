package com.requestBroker;

import java.util.LinkedList;

import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import com.google.protobuf.InvalidProtocolBufferException;
import com.requestBroker.BaseProtocol.dzhProcotol;
import com.requestBroker.BaseProtocol.dzhProcotol.MakeOrder;
import com.requestBroker.BaseProtocol.dzhProcotol.QueryContracts;

public class CounterBroker {
	
	//模拟客户端
	public static class Client{
		public void start(){
		
			new Thread(new Runnable(){
				public void run(){
					ZMQ.Context context = ZMQ.context(1);
					ZMQ.Socket socket = context.socket(ZMQ.REQ);
					
					socket.connect("tcp://localhost:8080");
					for(int i=0; i<10; i++){
						dzhProcotol dzhpro = testdecode(i);
						socket.send(dzhpro.toByteArray(), 0);
						String result = new String(socket.recv());
						System.out.println("result="+result);
					}
					
					socket.close();
					context.term();
				}
			}).start();
		}
	}
	
	//模拟多个客户端
	public static class MutlClient{
		public void start(){
		
			Thread thread = new Thread(new Runnable(){
				public void run(){
					ZMQ.Context context = ZMQ.context(1);
					ZMQ.Socket socket = context.socket(ZMQ.REQ);
					
					socket.connect("tcp://localhost:8080");
					for(int i=0; i<10; i++){
						dzhProcotol dzhpro = testdecode(i);
						socket.send(dzhpro.toByteArray(), 0);
						String result = new String(socket.recv());
						System.out.println("result="+result);
					}
					
					socket.close();
					context.term();
				}
			});
			for(int i=0; i<10;i++){
				new Thread(thread).start();
			}

		}
			
	}
	
	//模拟柜台
	public static class Counter{
		public void start(){
			new Thread(new Runnable(){
				public void run(){
					
					ZMQ.Context context = ZMQ.context(1);
					ZMQ.Socket socket = context.socket(ZMQ.REQ);
					socket.connect("tcp://localhost:8081");
					socket.send("ready".getBytes(),0);
					while(!Thread.currentThread().isInterrupted()){
						ZMsg msg = ZMsg.recvMsg(socket);
						ZFrame request = msg.removeLast();
						byte[] message = request.getData();
						if(message!=null){
							try {
			        			dzhProcotol pro2 = dzhProcotol.parseFrom(message);
			        			int id = pro2.getProtocolNo();
			        			switch (id%2) {    //id号区分具体协议
			        			case 0:
			        				QueryContracts contracts = pro2.getQueryContracts();
			        				String code = contracts.getCode();
			        				String name = contracts.getName();
			        				System.out.println(code+"----"+name);
			        				break;
			        			case 1:
			        				MakeOrder order = pro2.getMakeOrder();
			        				code = order.getCode();
			        				name = order.getName();
			        				System.out.println(code+"----"+name);
			        				break;
			        			default:
			        				break;
			        			}
			        		} catch (InvalidProtocolBufferException e) {
			        			// TODO Auto-generated catch block
			        			e.printStackTrace();
			        		}
						}
						ZFrame frame = new ZFrame("success".getBytes());
						msg.addLast(frame);
						msg.send(socket);
					}
					socket.close();
					context.term();
					
				}
			}).start();
		}
	}
	
	//模拟sleep柜台
	public static class Counter_slow{
		public void start(){
			new Thread(new Runnable(){
				public void run(){
					
					ZMQ.Context context = ZMQ.context(1);
					ZMQ.Socket socket = context.socket(ZMQ.REQ);
					socket.connect("tcp://localhost:8081");
					socket.send("ready".getBytes(),0);
					while(!Thread.currentThread().isInterrupted()){
						ZMsg msg = ZMsg.recvMsg(socket);
						ZFrame request = msg.removeLast();
						byte[] message = request.getData();
						if(message!=null){
							try {
			        			dzhProcotol pro2 = dzhProcotol.parseFrom(message);
			        			int id = pro2.getProtocolNo();
			        			switch (id%2) {    //id号区分具体协议
			        			case 0:
			        				QueryContracts contracts = pro2.getQueryContracts();
			        				String code = contracts.getCode();
			        				String name = contracts.getName();
			        				System.out.println(code+"----"+name);
			        				break;
			        			case 1:
			        				MakeOrder order = pro2.getMakeOrder();
			        				code = order.getCode();
			        				name = order.getName();
			        				System.out.println(code+"----"+name);
			        				break;
			        			default:
			        				break;
			        			}
			        		} catch (InvalidProtocolBufferException e) {
			        			// TODO Auto-generated catch block
			        			e.printStackTrace();
			        		}
						}
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						ZFrame frame = new ZFrame("success".getBytes());
						msg.addLast(frame);
						msg.send(socket);
					}
					socket.close();
					context.term();
					
				}
			}).start();
		}
	}

	
	//中间代理层
	public static class Broker{
		private LinkedList<ZFrame> workers;		//缓存空闲柜台的身份标示（identity）
		private LinkedList<ZMsg> requests;		//队列缓存客户端请求
		private ZMQ.Context context;
		private ZMQ.Poller poller;
		
		public Broker(){
			this.workers = new LinkedList<ZFrame>();
			this.requests = new LinkedList<ZMsg>();
			this.context =  ZMQ.context(1);
			this.poller = new ZMQ.Poller(2);
		}
		public void start(){
			ZMQ.Socket frontend = this.context.socket(ZMQ.ROUTER);
			ZMQ.Socket backend = this.context.socket(ZMQ.ROUTER);
			
			frontend.bind("tcp://*:8080");
			
			backend.bind("tcp://*:8081");
			
			ZMQ.PollItem frontItem = new ZMQ.PollItem(frontend, ZMQ.Poller.POLLIN);
			ZMQ.PollItem backItem = new ZMQ.PollItem(backend, ZMQ.Poller.POLLIN);
			this.poller.register(frontItem);
			this.poller.register(backItem);
			
			while(!Thread.currentThread().isInterrupted()){
				this.poller.poll();
				if(frontItem.isReadable()){//客户端请求
					ZMsg msg = ZMsg.recvMsg(frontItem.getSocket());
					requests.addLast(msg);
					
				}
				if(backItem.isReadable()){//柜台回复
					ZMsg msg = ZMsg.recvMsg(backItem.getSocket());
					ZFrame identity = msg.unwrap();
					this.workers.addLast(identity);
					ZFrame rep = msg.getFirst();
					if(new String(rep.getData()).equals("ready")){
						msg.destroy();
					}
					else{
						msg.send(frontend);
					}
				}
				while(this.requests.size()>0 && this.workers.size()>0){
					ZMsg msg = requests.removeFirst();
					ZFrame frame = workers.removeFirst();
					msg.wrap(frame);	//包装identity
					msg.send(backend);
				}
			}

			frontend.close();
			backend.close();
			context.term();
		}
	}
	
	//下单probuf协议
	static dzhProcotol testdecode(int i){
		dzhProcotol pro;
		dzhProcotol.Builder dzhproto = dzhProcotol.newBuilder();
		dzhproto.setProtocolNo(i);
		if(i%2 == 1){  //模拟下单协议
			MakeOrder.Builder makeorder = MakeOrder.newBuilder();
			makeorder.setCode("sh"+i);
			makeorder.setName("shanghai");

			dzhproto.setMakeOrder(makeorder);
			pro = dzhproto.build();
		}else{			//模拟查询协议
			QueryContracts.Builder contracts = QueryContracts.newBuilder();
			contracts.setCode("sz"+i);
			contracts.setName("shenzhen");
			dzhproto.setQueryContracts(contracts);
			pro = dzhproto.build();
		}
		return pro;
	}
	public static void main(String args[]){
//		Client client = new Client();
//		client.start();
		Counter counter = new Counter();
		counter.start();
		
		Counter_slow counter_slow = new Counter_slow();
		counter_slow.start();
		
		MutlClient mutlclient = new MutlClient();
		mutlclient.start();
		
		Broker broker = new Broker();
		broker.start();
		
	}
}

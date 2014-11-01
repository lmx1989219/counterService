package com.requestBroker;

import org.zeromq.ZMQ;

import com.requestBroker.BaseProtocol.dzhProcotol;
import com.requestBroker.BaseProtocol.dzhProcotol.MakeOrder;
import com.requestBroker.BaseProtocol.dzhProcotol.QueryContracts;

public class CounterClient {

	//下单
	private static class Order implements Runnable{
		public void run(){
			ZMQ.Context context= ZMQ.context(1);
			ZMQ.Socket sender = context.socket(ZMQ.PUSH);
			sender.bind("tcp://*:5500");
			for(int i =0;i<=10;i++){
				dzhProcotol dzhpro = testdecode(i);
				byte[] message = dzhpro.toByteArray();
				sender.send(message,0);
			}
			sender.close();
			context.term();
		}
	}
	//撮合返回结果
	private static class Result implements Runnable{
		public void run(){
			ZMQ.Context context = ZMQ.context(1);
			ZMQ.Socket receive = context.socket(ZMQ.PULL);
			receive.bind("tcp://*:5501");
			for(int i=0;i<=10;i++){
				byte[] message = receive.recv();
	            System.out.println(new String(message));
			}
			receive.close();
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
		new Thread(new Order()).start();
		new Thread(new Result()).start();
	}
}

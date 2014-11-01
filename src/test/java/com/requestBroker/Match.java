package com.requestBroker;

import org.zeromq.ZMQ;

import com.google.protobuf.InvalidProtocolBufferException;
import com.requestBroker.BaseProtocol.dzhProcotol;
import com.requestBroker.BaseProtocol.dzhProcotol.MakeOrder;
import com.requestBroker.BaseProtocol.dzhProcotol.QueryContracts;

public class Match {

	public static void main(String args[]) throws Exception{
		ZMQ.Context context = ZMQ.context(1);
		ZMQ.Socket receive = context.socket(ZMQ.PULL);
		receive.connect("tcp://localhost:5500");
		
		ZMQ.Socket sender = context.socket(ZMQ.PUSH);
		sender.connect("tcp://localhost:5501");
		
		while (!Thread.currentThread().isInterrupted()){
			byte[] message = receive.recv();
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
			 sender.send("success".getBytes(),0);  //返回结果
			}
			receive.close();
			context.term();
			
	}
}

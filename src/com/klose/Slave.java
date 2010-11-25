package com.klose;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.protobuf.RpcCallback;
import com.googlecode.protobuf.socketrpc.SocketRpcChannel;
import com.googlecode.protobuf.socketrpc.SocketRpcController;
import com.klose.MsConnProto.RegisterSlaveService;
import com.klose.MsConnProto.SlaveRegisterRequest;
import com.klose.MsConnProto.SlaveRegisterResponse;

public class Slave {
	private static int port = 22667;
	public static void main(String [] args) throws UnknownHostException{
		System.out.println(InetAddress.getLocalHost().getHostAddress());
		SlaveRegisterRequest registerToMasterRequest =  com.klose.MsConnProto.SlaveRegisterRequest.newBuilder()
		.setIpPort(InetAddress.getLocalHost().getHostAddress()+":"+port).setState(0).build();
		SocketRpcChannel socketRpcChannel = new SocketRpcChannel("10.5.0.55", 12267);
		SocketRpcController rpcController = socketRpcChannel.newRpcController();
		RegisterSlaveService registerToMaster = RegisterSlaveService.newStub(socketRpcChannel);
		registerToMaster.slaveRegister(rpcController, registerToMasterRequest, 
				new RpcCallback<SlaveRegisterResponse>(){
					@Override
					public void run(SlaveRegisterResponse response) {
						// TODO Auto-generated method stub
						System.out.println("response:"+ response.getIsSuccess());
					}
			});
	}
}

package com.klose;

import java.net.UnknownHostException;
import java.util.concurrent.Executors;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.googlecode.protobuf.socketrpc.RpcServer;
import com.googlecode.protobuf.socketrpc.SocketRpcServer;
import com.klose.MsConnProto.RegisterSlaveService;


public class Master{
//	public static class RegisterServiceImpl extends RegisterSlaveService{
//
//		@Override
//		public void slaveRegister(RpcController controller,
//				SlaveRegisterRequest request,
//				RpcCallback<SlaveRegisterResponse> done) {
//			// TODO Auto-generated method stub
//			// If "ip:port" doesn't exist in the record, add the slave in the list of slave.
//			// and response; on the contrary, reform the node that it has been already established 
//			// in the master.
//			SlaveRegisterResponse response = SlaveRegisterResponse.newBuilder()
//			.setIsSuccess(true).build();
//			System.out.println(request.getIpPort());
//			done.run(response);
//			
//		}
//		
//	}
	public static void main(String [] args) throws UnknownHostException {
		MasterArgsParser confParser = new MasterArgsParser(args); 
		confParser.loadValue();
		SocketRpcServer masterServer = new SocketRpcServer(confParser.getPort(),
				    Executors.newFixedThreadPool(10));
		System.out.println("Master started at "+ confParser.constructIdentity());
		
		RegisterToMasterService registerToMaster = new RegisterToMasterService();
		masterServer.registerService(registerToMaster);
		SlaveHeartbeatService heartbeatService = new SlaveHeartbeatService();
		masterServer.registerService(heartbeatService);
		SlaveExitService slaveExitService = new SlaveExitService();
		masterServer.registerService(slaveExitService);
		masterServer.run();
		 
	}
}

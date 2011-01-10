package com.klose.Slave;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.googlecode.protobuf.socketrpc.SocketRpcServer;
import com.klose.MsConnProto.AllocateIdentity;
import com.klose.MsConnProto.AllocateTaskService;
import com.klose.MsConnProto.ConfirmMessage;
import com.klose.MsConnProto.TState;
import com.klose.common.TaskDescriptor;
import com.klose.common.TaskState;

/**
 *When master schedules tasks to slave,
 *SlaveExecutorManager is responsible for receiving the tasks allocated by master,
 * launching the task,  monitoring the state of task, and reporting tasks' state changes.      
 * @author Bing Jiang
 *
 */
public class SlaveExecutorManager extends Thread{
	/*taskExecQueue the meaning of map is <taskid:the descriptor of task>*/
	private  static final HashMap<String, TaskDescriptor> taskExecQueue 
			= new HashMap<String, TaskDescriptor>();
	private static final HashMap<String, TaskState.STATES> taskStates 
			= new HashMap<String, TaskState.STATES>();
	private static final HashMap<String, SlaveExecutor> taskExecutors
			= new HashMap<String, SlaveExecutor> ();
	private static final Logger LOG = Logger.getLogger(SlaveExecutorManager.class.getName());
	private SocketRpcServer slaveServer;
	private SlaveArgsParser confParser;
	//	private static final HashMap<String, TaskDescriptor> 
	public SlaveExecutorManager(SlaveArgsParser confParser, SocketRpcServer slaveServer) {
		this.confParser = confParser;
		this.slaveServer = slaveServer;
	}
	public void run() {
		TaskAllocateService allocateService = new TaskAllocateService();
		this.slaveServer.registerService(allocateService);
		LOG.log(Level.INFO, "SlaveExecutorManager: start managing the tasks of slave.");
		SlaveReportTaskState reportUtil = new SlaveReportTaskState(confParser); 
		while(true) {
			try {
				synchronized(taskExecutors) {
					Iterator<String> iter = taskExecutors.keySet().iterator();
					while(iter.hasNext()) {
						String taskId = iter.next();
						TaskState.STATES state = taskStates.get(taskId);
						if( state.equals(taskExecutors.get(taskId).getTaskState()) ) {
							continue;
						}
						else {
							state = taskExecutors.get(taskId).getTaskState(); 
							reportUtil.report(taskId, state.toString());
							taskStates.put(taskId, state);
						}
					}
				}
				this.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	} 
	
	/**
	 * TaskAllocateService  is a rpc service's class,
	 * and it will extends the AllocateTaskService, which is defined
	 * by google protobuf-socket-rpc service.
	 * receive the task Master allocated.  
	 * @author Bing Jiang
	 *
	 */
	class TaskAllocateService extends AllocateTaskService {
		@Override
		public void allocateTasks(RpcController controller,
				AllocateIdentity request, RpcCallback<TState> done) throws IOException {
			// TODO Auto-generated method stub
			String taskId = request.getTaskIds();
			TState state = null;
			if(taskStates.containsKey(taskId)) {
				state = TState.newBuilder()
				.setTaskState(taskStates.get(taskId).toString()).build();
				//TODO the progress of task can be reported from here.
				LOG.log(Level.INFO, "Master has requested the state of task-"+taskId + " :" 
						+ state.getTaskState());
			}
			else {
				synchronized(taskStates) {
					taskStates.put(taskId,TaskState.STATES.RUNNING);
				}
				TaskDescriptor taskDes = new TaskDescriptor(taskId);
				taskExecQueue.put(taskId, taskDes);
				SlaveExecutor executor = new SlaveExecutor(taskDes);
				executor.start();
				synchronized(taskExecutors) {
					taskExecutors.put(taskId, executor);
				}
				state = TState.newBuilder()
						.setTaskState(TaskState.STATES.RUNNING.toString()).build();
				LOG.log(Level.INFO, "Slave is running task-"+taskId);
			}
			done.run(state);	
		}
	}	
}
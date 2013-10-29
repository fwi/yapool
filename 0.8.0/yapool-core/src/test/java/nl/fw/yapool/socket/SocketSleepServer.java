package nl.fw.yapool.socket;

import nl.fw.yapool.socket.SocketAcceptor;
import nl.fw.yapool.socket.SocketTask;

public class SocketSleepServer extends SocketAcceptor {
	
	public SocketSleepServer() {
		super();
		setCloseWaitTimeMs(1000L);
	}
	
	@Override
	public SocketTask getSocketTask() {
		return new SocketSleepTask();
	}
	
}

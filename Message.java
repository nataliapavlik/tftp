import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Message {

	Helper helper = new Helper();
	boolean lastPackage;
	
	// creates a requestMessage
	// message format for a WRQ/RRQ: opcode (2bytes), filename (n-bytes), delimiter (1-byte), mode (n-bytes), delimiter (1-byte)
	protected byte[] requestMessage(final byte opcode, final String filename, final String transfermode){

		// get the request length
		int requestLength = 2 + filename.length() + 1 + transfermode.length() + 1;

		// creating a new byte-array
		byte[] requestArray = new byte[requestLength];
		
		// adds the opcode to the byte-array
		requestArray[0] = 0;
		requestArray[1] = opcode;
		
		// adds the filename to the byte-array
		int position = 2;
		for (int i = 0; i < filename.length(); i++) {
			requestArray[position] = (byte) filename.charAt(i);
			position++;
		}

		// adds a delimiter to the byte-array
		requestArray[position] = 0;
		position++;
		
		// adds the transfer-mode to the byte-array
		for (int j = 0; j< transfermode.length(); j++) {
			requestArray[position] = (byte) transfermode.charAt(j);
			position++;
		}
		
		// adds another delimiter to the byte-array
		requestArray[position] = 0;
		
		// return the byte-array
		return requestArray;
	}
	
	// sends a requestMessage
	// needed parameter: opcode, filename, mode, sendData(byte-array), ipAddress, port, socket
	protected void sendRequestMessage(byte opcode, String filename, String mode, byte[] sendData, InetAddress ipAddress, int port, DatagramSocket socket){
		sendData = requestMessage(opcode, filename, mode);
		DatagramPacket sendRequest = new DatagramPacket(sendData, sendData.length, ipAddress, port);
		try {
			socket.send(sendRequest);
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	// creates a dataMessage
	// message format: opcode (2-bytes), block-number (2-bytes), message (512-bytes)
	protected byte[] dataMessage(final byte[] blocknumber, final byte[] data){
		
		byte[] dataArray = new byte[516];
		dataArray[0] = 0;
		dataArray[1] = 3;
		dataArray[2] = blocknumber[0];
		dataArray[3] = blocknumber[1];
		int position = 4;

		for (int i = 0; i < data.length; i++) {
			dataArray[position] = data[i];
			position++;
		}
		return dataArray;
	}
		
	// creates a static-data-message with a predefined message to be returned 
	// it's 300 bytes long, so it doesn't need to be divided in several blocks
	protected byte[] staticDataMessage(){

		byte[] dataArray = new byte[516];
		dataArray[0] = 0;
		dataArray[1] = 3;
		dataArray[2] = 0;
		dataArray[3] = 1;
		int position = 4;
		
		// http://loremipsum.de/downloads/original
		String message = new String();
		message = "Lorem ipsum dolor sit amet, consectetur adipiscing elitr, sed diam nonumy eirmod tempor\n"
				+ "invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et\n"
				+ "justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem\n"
				+ "ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr,  sed diam\n"
				+ "nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua.\n";
		
		for (int i = 0; i < message.length(); i++) {
			dataArray[position] = (byte) message.charAt(i);
			position++;
		}
		return dataArray;
										
	}
		
	// creates an ackMessage
	// message format: opcode (2-bytes), blocknumber (2-bytes)
	protected byte[] ackMessage(final byte[] blocknumber){
		
		byte[] ackArray = new byte[4];
		ackArray[0] = 0;
		ackArray[1] = 4;
		ackArray[2] = blocknumber[0];
		ackArray[3] = blocknumber[1];
		return ackArray;
	}
		
	// sends an ackMessage
	// needed parameter: received blocknumber from the dataMessage that will be acknowledged, sendData(byte-array), ipAddress, port, socket
	protected void sendAckMessage(byte[] receivedblockNumber, byte[] sendData, InetAddress ipAddress, int port, DatagramSocket socket){
		
		sendData = ackMessage(receivedblockNumber);
		// trims the message for an easier overview in Wireshark -> length = 4
		byte[] trimmedSendData = helper.trim(sendData);
		
		DatagramPacket sendAckMessage = new DatagramPacket(trimmedSendData, trimmedSendData.length, ipAddress, port);
		try {
			socket.send(sendAckMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// creates an errorMessage
	// message format: opcode (2-bytes), errorNumber (2-bytes), errorMessage (n-bytes), delimiter (1-byte)
	protected byte[] errorMessage(final byte errorNumber, final String errorMessage){
		byte[] errorArray = new byte[516];
		errorArray[0] = 0;
		errorArray[1] = 5;
		// 4 defined errors, therefore the first byte of the errorNumber remains a 0
		errorArray[2] = 0;
		errorArray[3] = errorNumber; 		
		
		int position = 4;
		for (int i = 0; i < errorMessage.length(); i++) {
			errorArray[position] = (byte) errorMessage.charAt(i);
			position++;
		}
		errorArray[position] = 0; 
		return errorArray;
	}
	
	// sends an errorMessage
	// needed parameter: errorNumber, errorMessage, sendData(byte-array), ipAddress, port, socket
	protected void sendErrorMessage(byte errorNumber, String errorMessage, byte[] sendData, InetAddress ipAddress, int port, DatagramSocket socket){
		sendData = errorMessage(errorNumber, errorMessage);
		DatagramPacket sendRequest = new DatagramPacket(sendData, sendData.length, ipAddress, port);
		try {
		
			socket.send(sendRequest);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

public class Server{

	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;
	private static final int PACKET_SIZE = 516;   // 512 bytes actual data, 2 bytes opcode, 2 bytes blocknumber
	private static int blockNumberSending = 1;    // blocknumber which is given to the next message to be transmitted
	private static int blockNumberExpected;       // blocknumber of the message which it expects to receive
	private static int blockNumberWRQ = 99;
	private static int actualReceivedBlockNumber;
	private static int numberOfAttemptedResends = 1;
	private static int sendingPackageNumber = 0;
	private static byte errorNumber;

	private static byte[] incomingData = new byte[PACKET_SIZE];
	private static byte[] sendData = new byte[PACKET_SIZE];
	private static byte[][] splitUserInput;
	private static byte[] trimmedSendData;
	private static byte[] messageFromClient;
	private static ArrayList<Integer> receivedBlockNumbers;
	
	private static DatagramSocket serverSocket;
	private static DatagramPacket sendDataMessage;
	private static DatagramPacket incomingPacket;
	private static InetAddress ipAddress;
	private static int clientPort;
	
	private static Message message;
	private static Helper helper;
	private static DataOutputStream dos;
	private static ByteArrayOutputStream baos;
	private static String errorMessage;

	private static boolean lastPackage = false;		
	private static boolean ackNeeded = false;
	
	public static void main(String[] args) throws Exception {
		
		message = new Message();
		helper = new Helper();
		serverSocket = new DatagramSocket(2018);
		incomingPacket = new DatagramPacket(incomingData, incomingData.length);
		baos = new ByteArrayOutputStream();
		
		// puts all receivedBlockNumbers in an int-ArrayList to check whether a message has already been received or not
		receivedBlockNumbers = new ArrayList<Integer>();

		
		while(true){
			
			// checks if an ACK is needed
			if (ackNeeded){
				
				// checks if an ACK was received; if it was received, everything goes on as expected
				try{
					serverSocket.receive(incomingPacket);	
					
					if (incomingData[1] == OP_ACK) {
						// stops/resets the timer when as an ACK was received
						serverSocket.setSoTimeout(0);
						System.out.println("SERVER: " + "ACK received.");

						// goes on until the last package was sent
						if(!lastPackage){		
							// send the received Message back to the Client, all packets after ACK
							sendDataMessage();
							// starts the timer
							serverSocket.setSoTimeout(3000);
						
						} else {
							ackNeeded = false;
						}
					
					} else if (incomingData[1] == OP_ERROR){

						byte[] receivedErrorNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
						errorNumber = receivedErrorNumber[1];
						
						errorMessage = new String(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
						System.out.println("Received from CLIENT: ErrorNumber: " + errorNumber + ", ErrorMessage: " + errorMessage + "!");
					}
				
				// if now ACK was received, the helper-method handleMissingAck() will take care of it
				} catch (IOException e) {
					handleMissingAck();
				}
				
				// if there is no ACK expected, it can be checked whether the incoming message is a Data- or an Error-Message
			} else {
				serverSocket.receive(incomingPacket);
				ipAddress = incomingPacket.getAddress();
				clientPort = incomingPacket.getPort();
				
				if (incomingData[1] == OP_WRQ){
					// received a WRQ from the client
					byte[] blockNrWrqByteArray = helper.intToByteArray(blockNumberWRQ);
					// sends an ACK to the client, so he knows it is allowed to send data to the server
					message.sendAckMessage(blockNrWrqByteArray, sendData, ipAddress, clientPort, serverSocket);		

				}  else if (incomingData[1] == OP_DATA) {
					
					// receives DataMessage and puts it into dataOutputStream
					dos = new DataOutputStream(baos);
					
					// gets blocknumber from the received DataMessage and saves it in a byteArray, so it can be used in sendAckMessage
					byte[] receivedBlockNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
					actualReceivedBlockNumber = helper.byteArrayToInt(receivedBlockNumber);
					
					// handles received messages 
					// *checks if it can be added to the dataOutputStream or if message has to be discarded/ignored since it was already received
					handleReceivedMessage();
					
					/* checks if the received bocknumber equals the expected blocknumber
					*  if the package was already received, handleReceivedMessage() takes care of it
					*  if received package has a higher blocknumber than expected message, an error will be send (package missing)
					*/
						
					if (actualReceivedBlockNumber > blockNumberExpected){
						// ERROR NUMBER 4 - Illegal TFTP operation  (here: package got lost)
						errorNumber = 4;
						message.sendErrorMessage(errorNumber, "Received blocknumber is higher than expected BN -> Package missing.", sendData, ipAddress, clientPort, serverSocket);
					}

					// sends the ACK for received Message
					message.sendAckMessage(receivedBlockNumber, sendData, ipAddress, clientPort, serverSocket);		
					
					// checks if the last package was received and then puts the message in the messageFromClient-variable
					// and prints a list of all received blocks
					if (incomingPacket.getLength() < 516){
						System.out.println("SERVER: " + "Message from client received (Received blocks: "	+ receivedBlockNumbers.toString() +").");
						messageFromClient = baos.toByteArray();
						
					}
									
				} else if (incomingData[1] == OP_RRQ) {
					System.out.println("SERVER: " + "RRQ received.");
				
					// if there was a WRQ and the server received a message from the client, it will send the message back
					if (messageFromClient != null){	
						
						// sends the received Message back to the Client, it's the first packet after RRQ
						sendDataMessage();
						// starts the timer
							serverSocket.setSoTimeout(3000);
												
					// if there was no user input, the server will send an already defined message to the client when there is a RRQ
					} else {
						sendData = message.staticDataMessage();
						trimmedSendData = helper.trim(sendData);
						sendDataMessage = new DatagramPacket(trimmedSendData, trimmedSendData.length, ipAddress, clientPort);
						serverSocket.send(sendDataMessage);
						lastPackage = true;
					}
					
				// handles incoming Error-Messages
				} else if (incomingData[1] == OP_ERROR){

					byte[] receivedErrorNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
					errorNumber = receivedErrorNumber[1];
					
					errorMessage = new String(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
					System.out.println("(Received from client:) ErrorNumber: " + errorNumber + ", ErrorMessage: " + errorMessage + "!");
				}				
			}
		} 	
	}

	// Helper-Method for sending a DataMessage
	private static void sendDataMessage(){
		System.out.println("SERVER: " + "DataMessage sent.");
		
		byte[] blockNumberAsByteArray = helper.intToByteArray(blockNumberSending);
		splitUserInput = helper.divideArray(messageFromClient);
							
		sendData = message.dataMessage(blockNumberAsByteArray, splitUserInput[sendingPackageNumber]);
		sendingPackageNumber++;
		blockNumberSending++; // blocknumber that is given to the next message to be transmitted
		
		// trims the message (removes empty bytes) so it is possible to detect the last package
		trimmedSendData = helper.trim(sendData);
		
		sendDataMessage = new DatagramPacket(trimmedSendData, trimmedSendData.length, ipAddress, clientPort);
		try {
			serverSocket.send(sendDataMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		ackNeeded = true;
		
		if (trimmedSendData.length < 516){
			lastPackage = true;
		}
	}
	
	/*
	 * Helper-Method for handling a missing ACK
	 * If an ACK is needed but none was received, the message will be sent again but only until the attempts exceed a preset limit of three (counter)
	 * After three attempts it sends an Error Message and the whole transfer aborts
	 */
	private static void handleMissingAck() {
		System.out.println("SERVER: " + "No ACK received.");

		
		// chosen number of resending attempts: 3
		if (numberOfAttemptedResends <= 3) {
			
			// ERROR NUMBER 1 - File not found (here: no ACK received)
			errorNumber = 1;
			message.sendErrorMessage(errorNumber, "No ACK received - resending the message.", sendData, ipAddress, clientPort, serverSocket);
			
			System.out.println("SERVER: " + "Resending attempt Nr. :" + numberOfAttemptedResends + ".");
			
			// counting the blockNumberSending and the sendingPackageNumber -1 so that the last package get�s resent 
			blockNumberSending = blockNumberSending - 1;
			sendingPackageNumber = sendingPackageNumber - 1;
			
			sendDataMessage();
			numberOfAttemptedResends++;

		// if the number of resending attempts exceeds, the connection will be aborted
		} else {
			try {
				// sets the timer to 0 to avoid a timeout exception
				serverSocket.setSoTimeout(0);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			System.out.println("SERVER: " + "Max. number of attempts to resend the message exceeded!");
			
			// ERROR NUMBER 4 - Illegal TFTP operation (here: too many resending attempts)
			errorNumber = 4;
			message.sendErrorMessage(errorNumber, "Data-Transfer due to max resending attempts aborted.", sendData, ipAddress, clientPort, serverSocket);			
		}
	}
	
	// Helper-Method to check if a message with the same blocknumber was already received
	private static void handleReceivedMessage(){
		// check if the arrayList with the received blockNumbers contains the last received blocknumber
		if (!receivedBlockNumbers.contains(actualReceivedBlockNumber)){
			// if the arrayList with the blocknumbers of the received messages does not contain the current received Message (blocknumber), the blocknumber will be added to the array
			receivedBlockNumbers.add(actualReceivedBlockNumber);
			// only messages which were't received yet will be added to the data output stream
			try {
				dos.write(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
				// System.out.println("(Client:) Block with the Blocknumber (" + actualReceivedBlockNumber + ") was added to the DataOutputStream");
				blockNumberExpected++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// ERROR NUMBER 6 - File already exists
			errorNumber = 6;
			message.sendErrorMessage(errorNumber, "A message with the same blocknumber was already received and will therefore be discarded.", sendData, ipAddress, clientPort, serverSocket);	
		}	
	}	
}

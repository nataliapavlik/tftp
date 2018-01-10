import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;

public class Client {

	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;
	private static final int PACKET_SIZE = 516;       // 512 bytes actual data, 2 bytes opcode, 2 bytes blocknumber
	private static int blockNumberSending = 1;        // blocknumber which is given to the next message to be transmitted
	private static int blockNumberExpected;           // blocknumber of the message which it expects to receive
	private static int actualReceivedBlockNumber;
	private static int numberOfAttemptedResends = 1;
	private static int sendingPackageNumber = 0;
	private static byte errorNumber;

	private static byte[] sendData = new byte[PACKET_SIZE];
	private static byte[] incomingData = new byte[PACKET_SIZE];
	private static byte[][] splitUserInput;
	private static byte[] userInputByteArray;
	private static byte[] trimmedSendData;
	private static ArrayList<Integer> receivedBlockNumbers;

	private static DatagramSocket clientSocket;
	private static DatagramPacket sendDataMessage;
	private static DatagramPacket incomingPacket;
	private static InetAddress ipAddress;
	private static final int SERVER_PORT = 2018;       // random chosen port (has to be higher than 1024)

	private static Message message;
	private static Helper helper;
	private static DataOutputStream dos;
	private static ByteArrayOutputStream baos;
	private static String errorMessage;

	private static boolean lastPackage = false;
	private static boolean RRQsent = false;
	private static boolean ackNeeded = false;
	

	public static void main(String[] args) throws Exception {

		message = new Message();
		helper = new Helper();
		clientSocket = new DatagramSocket();
		ipAddress = InetAddress.getByName("localhost");
		// puts all receivedBlockNumbers in an int-ArrayList to check whether a message has already been received or not
		receivedBlockNumbers = new ArrayList<Integer>();

		// asks the user whether the user wants to send a WRQ or a RRQ
		System.out.println("Do you want to send a WRQ or a RRQ?");
		BufferedReader inFromUserWRQRRQ = new BufferedReader(new InputStreamReader(System.in));
		String userinputWRQRRQ = inFromUserWRQRRQ.readLine();
		String lowerCaseInput = userinputWRQRRQ.toLowerCase();

		// if the user chooses to send a WRQ
		if (lowerCaseInput.equals("wrq")) {
			
			// sends a WRQ to the server
			message.sendRequestMessage(OP_WRQ, "filename.txt", "octet",	sendData, ipAddress, SERVER_PORT, clientSocket);
			System.out.println("WRQ sent.");

			// gets the user-input and transfers it into a byteArray
			System.out.println("Please type in your message:");
			BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
			String userinput = inFromUser.readLine();
			userInputByteArray = helper.getUserInputToByteArray(userinput);

			// when a WRQ was sent, an ACK is needed
			ackNeeded = true;

			// initiates the DatagramPacket for incoming Data
			incomingPacket = new DatagramPacket(incomingData, incomingData.length);

			// creates a byteArrayOutputStream to handle the incoming data-message
			baos = new ByteArrayOutputStream();

			while (true) {
				// checks if an ACK is needed
				if (ackNeeded) {

					// checks if an ACK was received; if it was received, everything goes on as expected
					try {
						clientSocket.receive(incomingPacket);

						if (incomingData[1] == OP_ACK) {
						
							// stops/resets the timer when an ACK was received
							clientSocket.setSoTimeout(0);
							System.out.println("CLIENT: " + " " + "ACK received.");
							// checks if a RRQ was already sent
							if (!RRQsent) {
								// checks if the last package was already sent
								if (lastPackage) {
									// sends a RRQ to the server in case no RRQ was sent yet and if the last package of the DataMessage was sent
									message.sendRequestMessage(OP_RRQ, "filename.txt", "octet", sendData, ipAddress, SERVER_PORT, clientSocket);
									RRQsent = true;
									ackNeeded = false;

								} else {
									sendDataMessage();
									// starts the timer
									clientSocket.setSoTimeout(3000);
								}
							}
						} else if (incomingData[1] == OP_ERROR) {
						
								byte[] receivedErrorNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
								errorNumber = receivedErrorNumber[1];
								
								errorMessage = new String(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
								System.out.println("Received from SERVER: ErrorNumber = " + errorNumber + ", ErrorMessage = " + errorMessage + "!");
						}
						
					// if no ACK was received, the helper-method handleMissingAck() will take care of it
					} catch (IOException e) {
						handleMissingAck();
					}

					// if there is no ACK expected, it can be checked whether the incoming message is a Data- or an Error-Message
				} else {
					clientSocket.receive(incomingPacket);

					if (incomingData[1] == OP_DATA) {

						// initiates a data output stream for incoming messages
						dos = new DataOutputStream(baos);

						// gets the blocknumber from the received DataMessage and saves it in a byteArray, so it can be used in sendAckMessage
						byte[] receivedBlockNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
						actualReceivedBlockNumber = helper.byteArrayToInt(receivedBlockNumber);

						// handles received messages 
						// checks if it can be added to the dataOutputStream or if message has to be discarded/ignored since it was already received
						handleReceivedMessage();
						
						/* checks if the received blocknumber equals the expected blocknumber
						*  if the package was already received, handleReceivedMessage() takes care of it
						*  if received package has a higher blocknumber than expected message, an error will be send (package missing)
						*/
						
						if(actualReceivedBlockNumber > blockNumberExpected){
							// ERROR NUMBER 4 - Illegal TFTP operation (here: package got lost)
							errorNumber = 4;
							message.sendErrorMessage(errorNumber, "Received blocknumber is higher than expected BN -> Package missing.", sendData, ipAddress, SERVER_PORT, clientSocket);					
						}

						// sends the ACK for received Message
						message.sendAckMessage(receivedBlockNumber, sendData, ipAddress, SERVER_PORT, clientSocket);

						// checks if the last package was received and then prints the completely received data in a byteArrayOutputStream
						// also prints a list of all received blocks
						if (incomingPacket.getLength() < 516) {
							System.out.println("CLIENT: " + " " + "Message from server received (Received blocks: " + receivedBlockNumbers.toString() + ").");
							System.out.println("\n" + "CLIENT: " + " " +  "Complete Message: \n"	+ new String(baos.toByteArray()) +"\n");
						}

					// handles incoming Error-Messages
					} else if (incomingData[1] == OP_ERROR) {
						
						byte[] receivedErrorNumber = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
						errorNumber = receivedErrorNumber[1];
						
						errorMessage = new String(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
						System.out.println("Received from SERVER: ErrorNumber: " + errorNumber + ", ErrorMessage: " + errorMessage + "!");
					}
				}
			}

		// if the user chooses to send a RRQ
		} else if (lowerCaseInput.equals("rrq")) {

			// sends a requestMessage
			message.sendRequestMessage(OP_RRQ, "filename.txt", "octet",	sendData, ipAddress, SERVER_PORT, clientSocket);
			incomingPacket = new DatagramPacket(incomingData, incomingData.length);

			while (true) {
				// receives the dataMessage and prints it to the console
				clientSocket.receive(incomingPacket);
				System.out.println("\n" + "CLIENT: " +  "Received message: \n\n" + new String(incomingPacket.getData(), 4, incomingPacket.getLength() - 4));

				// gets the received blocknumber and sends an ACK
				byte[] receivedBlNrRRQ = helper.getBlockNumberFromReceivedMessageAsByteArray(incomingData);
				message.sendAckMessage(receivedBlNrRRQ, sendData, ipAddress, SERVER_PORT, clientSocket);
			}
		}
	}

	// Helper-Method for sending a DataMessage
	private static void sendDataMessage() {
		System.out.println("CLIENT: " + " " + "DataMessage sent.");
		
		byte[] blockNumberAsByteArray = helper.intToByteArray(blockNumberSending);
		splitUserInput = helper.divideArray(userInputByteArray);

		sendData = message.dataMessage(blockNumberAsByteArray, splitUserInput[sendingPackageNumber]);
		sendingPackageNumber++;
		blockNumberSending++; // blocknumber that is given to the next message to be transmitted

		// trims the message (removes empty bytes), so the last message can be detected
		trimmedSendData = helper.trim(sendData);

		sendDataMessage = new DatagramPacket(trimmedSendData, trimmedSendData.length, ipAddress, SERVER_PORT);
		try {
			clientSocket.send(sendDataMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}

		ackNeeded = true;

		if (trimmedSendData.length < 516) {
			lastPackage = true;
		}
	}

	/*
	 * Helper-Method for handling a missing ACK
	 * If an ACK is needed but none was received, the message will be sent again but only until the attempts exceed a preset limit of three (counter)
	 * After three attempts it sends an Error Message and the whole transfer aborts
	 */
	private static void handleMissingAck() {
		System.out.println("CLIENT: " + "No ACK received.");

		// chosen number of resending attempts: 3
		if (numberOfAttemptedResends <= 3) { 
			
			// ERROR NUMBER 1 - File not found (here: no ACK received)
			errorNumber = 1;
			message.sendErrorMessage(errorNumber, "No ACK received - resending the message.", sendData, ipAddress, SERVER_PORT, clientSocket);
			
			System.out.println("CLIENT: " + "Resending attempt Nr. :" + numberOfAttemptedResends + ".");
			
			// counting the blockNumberSending and the sendingPackageNumber -1 so that the last package gets resent 
			blockNumberSending = blockNumberSending - 1;
			sendingPackageNumber = sendingPackageNumber - 1;

			sendDataMessage();
			numberOfAttemptedResends++;

		// if the number of resending attempts exceeds, the connection will be aborted
		} else {
			try {
				// sets the timer to 0 to avoid a timeout exception
				clientSocket.setSoTimeout(0);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			
			System.out.println("CLIENT: " + "Max number of attempts to resend the message exceeded!");
			
			// ERROR NUMBER 4 - Illegal TFTP operation (here: too many resending attempts)
			errorNumber = 4;
			message.sendErrorMessage(errorNumber, "Data-Transfer due to max resending attempts aborted.", sendData, ipAddress, SERVER_PORT, clientSocket);			
		}
	}
	
	// Helper-Method to check if a message with the same blocknumber was already received
	private static void handleReceivedMessage(){
		// checks if the arrayList with the received blockNumbers contains the last received blockNumber
		if (!receivedBlockNumbers.contains(actualReceivedBlockNumber)){
			// if the arrayList with the BlockNumbers of the received messages does not contain the current received Message (blocknumber), the blocknumber will be added to the array
			receivedBlockNumbers.add(actualReceivedBlockNumber);
			// only messages which were't received yet will be added to the data output stream
			try {
				dos.write(incomingPacket.getData(), 4, incomingPacket.getLength() - 4);
				// System.out.println("CLIENT:  Block with the Blocknumber (" + actualReceivedBlockNumber + ") was added to the DataOutputStream");	
				blockNumberExpected++;
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {
			// ERROR NUMBER 6 - File already exists
			errorNumber = 6;
			message.sendErrorMessage(errorNumber, "A message with the same blocknumber was already received and will therefore be discarded.", sendData, ipAddress, SERVER_PORT, clientSocket);	

		}	
	}
}

import java.util.Arrays;

public class Helper {

	// removes 0 from byteArrays
	byte[] trim(byte[] bytes)	{
		int i = bytes.length - 1;
	    while (i >= 0 && bytes[i] == 0) {
	        --i;
	    }
	    return Arrays.copyOf(bytes, i + 1);
	}

	// divides a byte-array in blocks of 512 bytes each -> returns a 2-dimensional array ([0]: packagenumber, [1]: split byte-array -> always 512 bytes) 
	public byte[][] divideArray(byte[] source) {
	        
		byte[][] splitSource;

			if ((int)Math.ceil(source.length % (double)512) != 0){
				splitSource = new byte[(int)Math.ceil(source.length / (double)512)][512];
			} else {
				// if the source-length is divisible by 512, there will be an extra empty data-package with 0 bytes to be able to define the end of the data-message
				splitSource = new byte[((int)Math.ceil(source.length / (double)512)+1)][512];
			}
			
	        int start = 0;
	        for(int i = 0; i < splitSource.length; i++) {
	            splitSource[i] = Arrays.copyOfRange(source,start, start + 512);
	            start += 512 ;
	        }
	        return splitSource;
	 }
		
	// converts an int-value into byte and returns a byte-array
	byte[] intToByteArray(int number){
		
	  return new byte[] { 
	         (byte) ((number >> 8) & 0xFF),   
	         (byte) (number & 0xFF)};	
	}
	
	//converts a byteArray into a number -> returns an int-value
	int byteArrayToInt(byte[] byteArray){
		

	    return  byteArray[1] & 0xFF |
	            (byteArray[0] & 0xFF) << 8;   
	}
		
	// reads the blocknumber from an incoming message and returns it as a byte-array
	byte[] getBlockNumberFromReceivedMessageAsByteArray(byte[] incomingData){
		
		byte [] receivedBlockNumber = new byte[2];
		receivedBlockNumber[0] = incomingData[2];
		receivedBlockNumber[1] = incomingData[3];
		
		return receivedBlockNumber;
	}
	
	// converts a string to a byte-array
	byte[] getUserInputToByteArray(String userinput){
		
		byte [] userInputByteArray = new byte[userinput.length()];
		int pos = 0;
		for (int i = 0; i < userinput.length(); i++) {
			userInputByteArray[pos] = (byte) userinput.charAt(i);
			pos++;
		}
		return userInputByteArray;
	}
	
}

package server;

import shared.Message;
import shared.MessageCreator;
import shared.MessageType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogManager {
	static ConcurrentHashMap<Integer, List<Message>> userMessageLogs;
	static ConcurrentHashMap<Integer, List<Message>> chatroomMessageLogs;
	private static ConcurrentLinkedQueue<Message> messageQueue = new ConcurrentLinkedQueue<Message>();

	LogManager(List<Integer> allUserIDs, List<Integer> allChatroomIDs) {
		userMessageLogs = new ConcurrentHashMap<Integer, List<Message>>();
		chatroomMessageLogs = new ConcurrentHashMap<Integer, List<Message>>();
		loadUserMessages(allUserIDs);
		loadChatroomMessages(allChatroomIDs);
		
		Thread logReader = new Thread(() -> startLogReader());
		logReader.start();
	}
	
	public void loadUserMessages(List<Integer> allUserIDs) {
		for(Integer ID : allUserIDs) {
			try 
	        {
	            String messageFile = Integer.toString(ID) + "userlog.txt";
	            
	            File myFile = null;
	            
	            try {
	            	myFile = new File(messageFile);

		            if (myFile.createNewFile()) {
		                continue; // file doesn't exist
		            } else {
		                System.out.println("File already exists.");
		            }
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
	            
	            Scanner reader = new Scanner(myFile);

	            //first populate the messages of the inbox
	            while (reader.hasNextLine())
	            {
	                //getline and set delimiters
	                Scanner line = new Scanner(reader.nextLine()).useDelimiter("|"); // \s+ means whitespace

	                ArrayList<String> token = new ArrayList<String>();
	                line.tokens();

	                //grab all the tokens
	                while(line.hasNext())
	                {
	                    token.add(line.next());
	                }

	                //if there are more or less than 4 tokens, then it is invalid
	                if (token.size() != 4)
	                {
	                    line.close(); //do nothing and skip this iteration
	                    continue;
	                }

	                //add all message to the arraylist
	                Message add;
	                MessageCreator create;
	                create = new MessageCreator(MessageType.UTU);
	                
	                create.setFromUserID(Integer.parseInt(token.get(0))); //add from user id
	                create.setContents(token.get(1)); //add the message
	                create.setDate(Long.parseLong(token.get(2))); // add the date


	                add = new Message(create);

	                userMessageLogs.get(ID).add(add);

	                line.close();
	            }
	            reader.close();

	        }
	        catch (IOException e) {
	            e.printStackTrace();
		        System.out.println("Error loading file: " + e.getMessage());
		        System.out.println("File " + ID + " does not exist.");
	        }
		}
	}
	
	public void loadChatroomMessages(List<Integer> allChatroomIDs) {
		for(Integer ID : allChatroomIDs) {
			try 
	        {
	            String messageFile = Integer.toString(ID) + "chatlog.txt";

	            File myFile = null;
	            
	            try {
	            	myFile = new File(messageFile);

		            if (myFile.createNewFile()) {
		                continue; // file doesn't exist
		            } else {
		                System.out.println("File already exists.");
		            }
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
	            
	            Scanner reader = new Scanner(myFile);

	            //first populate the messages of the inbox
	            while (reader.hasNextLine())
	            {
	                //getline and set delimiters
	                Scanner line = new Scanner(reader.nextLine()).useDelimiter("|"); // \s+ means whitespace

	                ArrayList<String> token = new ArrayList<String>();
	                line.tokens();

	                //grab all the tokens
	                while(line.hasNext())
	                {
	                    token.add(line.next());
	                }

	                //if there are more or less than 7 tokens, then it is invalid
	                if (token.size() != 5)
	                {
	                    line.close(); //do nothing and skip this iteration
	                    continue;
	                }

	                //add all message to the arraylist
	                Message add;
	                MessageCreator create;
	                create = new MessageCreator(MessageType.UTC);

	                create.setFromUserID(Integer.parseInt(token.get(0))); //add from user id
	                create.setContents(token.get(1)); //add the message
	                create.setDate(Long.parseLong(token.get(2))); // add the date
	                create.setToChatroom(Integer.parseInt(token.get(4)));

	                add = new Message(create);

	                chatroomMessageLogs.get(ID).add(add);

	                line.close();
	            }
	            reader.close();

	        }
	        catch (IOException e) {
	            e.printStackTrace();
		        System.out.println("Error loading file: " + e.getMessage());
		        System.out.println("File " + ID + " does not exist.");
	        }
		}
	}
	
	public void getUserMessages(ObjectOutputStream output, Message message) {
        int userID = message.getFromUserID(); 
        List<Message> messages = userMessageLogs.getOrDefault(userID, new ArrayList<>());
        
        try {
            output.writeObject(messages);  
        } catch (IOException e) {
            e.printStackTrace();
        }
	}// getUserMessages

	public void getChatroomMessages(ObjectOutputStream output, Message message) {
        int chatroomID = message.getToChatroomID(); 
        List<Message> messages = chatroomMessageLogs.getOrDefault(chatroomID, new ArrayList<>());
        
        try {
            output.writeObject(messages); 
        } catch (IOException e) {
            e.printStackTrace();
        }
	}// getChatroomMessages
	
	
	/******/

	public void answerLogRequest(Message message) {
        MessageType type = message.getMessageType();
        if (type == MessageType.GUL) {  // Get User Logs
            getUserMessages(null, message);
        } else if (type == MessageType.GCL) {  // Get Chatroom Logs
            getChatroomMessages(null, message);
        }
	}// answerLogRequest

	public void storeMessage(Message message) {
		// Check if the message is addressed to a specific user
		if (message.getToUserID() >= 0) {
			List<Message> userMessages = userMessageLogs.getOrDefault(message.getToUserID(), new ArrayList<>());
			userMessages.add(message);

			userMessageLogs.put(message.getToUserID(), userMessages);
			saveLogs(message);
		} // if

		
		if (message.getToChatroomID() >= 0) {
			List<Message> chatroomMessages = chatroomMessageLogs.getOrDefault(message.getToChatroomID(), new ArrayList<>());
			chatroomMessages.add(message);

			chatroomMessageLogs.put(message.getToChatroomID(), chatroomMessages);
			saveLogs(message);
		} // if

	}// storeMessage
	
	public static void saveLogs(Message message) {
		if(message.getMessageType().equals(MessageType.UTU)) {
			String logFilePath = String.valueOf(message.getFromUserID()) + "userlog.txt";
			try(FileWriter writer = new FileWriter(logFilePath, true)) { // True = will not overwrite
				writer.write(message.storeUserLogMessage() + "\n");
				writer.close();
			}
			catch(IOException e) {
				System.err.println("Error saving log of: " + logFilePath);
			}
		}
		else if(message.getMessageType().equals(MessageType.UTC)) {
			String logFilePath = String.valueOf(message.getToChatroomID()) + "chatlog.txt";
			try(FileWriter writer = new FileWriter(logFilePath, true)) {
				writer.write(message.storeChatLogMessage() + "\n");
				writer.close();
			}
			catch(IOException e) {
				System.err.println("Error saving log of: " + logFilePath);
			}
		}
	}
	
	public void addToLogQueue(Message message) {
		messageQueue.add(message);
	}
	
	public Boolean isLogQueueEmpty() {
		if(messageQueue.isEmpty()) return true;
		
		return false;
	}
	
	private void startLogReader() {
		
		while(true) {
			Message message = messageQueue.poll();
			if(message == null) continue;
			
			MessageType type = message.getMessageType();
			
			switch(type) {
				case UTU:
					storeMessage(message);
					break;
				case UTC:
					storeMessage(message);
					break;
				default:
					break;
			}
			
		}
	}

}

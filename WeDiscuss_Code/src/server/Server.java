package server;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import shared.*;


public class Server {
	private ConcurrentHashMap<Integer, ObjectOutputStream> listOfClients;
	private ConcurrentHashMap<Integer, Thread> clientThreads;
	private UserManager userManager;
	private LogManager logManager;
	private ChatroomManager chatroomManager;
	private ServerSocket serverSocket;
	private int port;
	private String serverIP;
	private boolean running;
	private ExecutorService executorService;
	private static final int MAX_THREADS = 10;
	
	public Server(int port) throws UnknownHostException {
		this.port = port;
		this.serverIP = InetAddress.getLocalHost().getHostAddress().trim();
		this.running = false;
		this.userManager = new UserManager();
		this.chatroomManager = new ChatroomManager(this);
		this.logManager = new LogManager(userManager.getAllUserIDs(), chatroomManager.getAllChatroomIDs());
		this.listOfClients = new ConcurrentHashMap<>();
		this.clientThreads = new ConcurrentHashMap<>();
		this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
	}
	
	public static void main(String[] args) throws UnknownHostException {
		Server server = new Server(8080); //temp port for testing
		server.start();	
	}
	
	public void start() {
		running = true;
		
		// shutdown hook so stop() is called if server terminates unexpectedly or System.exit(0) is reached
		Runtime.getRuntime().addShutdownHook(new Thread(() -> stop()));
		
		// Starts thread that listens for incoming client connections
		Thread listenThread = new Thread(() -> listenForConnections());
		listenThread.start();
		
		try(Scanner scanner = new Scanner(System.in)){
			while(running) {
				System.out.println("Enter 'end' to shutdown the server: ");
				if(scanner.hasNextLine()) {
					String input = scanner.nextLine();
					if(input.equalsIgnoreCase("end")) {
						System.out.println("Shutting down server...");
						running = false;
						listenThread.interrupt();
					}
				}
			}
		}
		
		System.exit(0);
	}
	
	public void stop() {		
		try {
			if (serverSocket != null) {
				if(!serverSocket.isClosed()) {
					serverSocket.close();
				}
				userManager.saveUsers();
				chatroomManager.saveUsers();
				while(!logManager.isLogQueueEmpty()) {
					System.out.println("Processing Log Message");
				}
				System.out.println("Server stopped.");
			}
		} catch (IOException e) {
			System.out.println("Error closing server: " + e.getMessage());
		}
		finally {
			executorService.shutdown();
		}
	}
	
	public UserManager getUserManager() {
		return userManager;
		
	}
	
	public LogManager getLogManager() {
		return logManager;
	}
	
	public ChatroomManager getChatroomManager() {
		return chatroomManager;
	}
	
	public ConcurrentHashMap<Integer, ObjectOutputStream> getListOfClients() {
		return listOfClients;
	}
	
	public ObjectOutputStream getSocketForUser(Integer userID) {
		return listOfClients.get(userID);
	}
	
	public ServerSocket getServerSocket() {
		return serverSocket;
	}
	
	public void listenForConnections() {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Server started on IPV4 Address: " +  serverIP + " Port: " + port);
			while (!Thread.currentThread().isInterrupted()) {
				Socket clientSocket = serverSocket.accept();
				System.out.println("New Connection! From: " + clientSocket.getLocalSocketAddress());
				Thread processThread = new Thread(() -> processResponse(clientSocket));
				executorService.submit(processThread);
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}
	
	public void processResponse(Socket clientSocket) {
		//Create appropriate Message objects and route them 
		//to the correct handler based on message type
		int userID = -1;
		ObjectInputStream input = null;
		ObjectOutputStream output = null;
		String clientIP = clientSocket.getLocalAddress().getHostAddress().trim();
		try {	
			input = new ObjectInputStream(clientSocket.getInputStream());
			output = new ObjectOutputStream(clientSocket.getOutputStream());
			
			while(running) {
				// If thread stopped for w/e reason (User deletion) stop processing & shutdown
			    if (Thread.interrupted()) {
			        System.out.println("Thread interrupted, shutting down for user: " + userID);
			        closeResources(clientSocket, input, output, userID);
			    }
				
				Message message = (Message) input.readObject();
				
				if(message == null) continue;
				
				System.out.println("Message recieved from client: " + message.getMessageType());
				
				MessageType type = message.getMessageType();
				
				switch(type) {
					case LOGIN:
							userID = userManager.authUser(output, message, chatroomManager);
							if(userID != -1) {
								 listOfClients.put(userID, output);
								 clientThreads.put(userID, Thread.currentThread());
								 sendUserMapUpdates(userID, userManager.getUsername(userID), true);
							}
						break;
					case LOGOUT:
							if(userManager.logout(output, message)) {
								closeResources(clientSocket, input, output, userID); // Sends usermap update
								listOfClients.remove(userID);
								clientThreads.remove(userID);
								return;
							}
						break;
					case ADDUSER:
							userManager.addUser(output, message);
						break;
					case DELUSER:
							int delUser = userManager.deleteUser(output, message);
							if(delUser != -1) {
								sendUserMapUpdates(delUser, userManager.getUsername(delUser), false); // Let everyone know User is no longer apart of the server
								
						    	// Remove user from all chatrooms they are apart of
						    	chatroomManager.removeUserFromChatrooms(userManager.getUser(delUser), listOfClients);
						    	
						    	// Stop servicing the client
								Thread clientThread = clientThreads.get(delUser);
								if(clientThread != null) {
									clientThread.interrupt();
								}
							}
						break;
					case CPWD:
							userManager.changeUserPassword(output, message);
						break;
					case GUL:
							logManager.getUserMessages(output, message);
						break;
					case GCL:
							logManager.getChatroomMessages(output, message);
						break;
					case CC:
							chatroomManager.createChatroom(output, message);
						break;
					case IUC:
							int invitedUserID = chatroomManager.addUsertoChatroom(output, message, listOfClients);
							if(invitedUserID != -1) {
								userManager.addChatroomToUser(invitedUserID, message.getToChatroomID()); // Add chatroomID to User Object's list of chatrooms
							}
						break;
					case JC:
							int joinedUserID = chatroomManager.joinChatroom(output, message, listOfClients);
							if(joinedUserID != -1) {
								userManager.addChatroomToUser(joinedUserID, message.getToChatroomID()); // Add chatroomID to User Object's list of chatrooms
								
							}
						break;
					case LC:
							chatroomManager.removeUserfromChatroom(output, message, listOfClients);
						break;
					case UTU:
							userManager.sendMessage(output, message, listOfClients);
							logManager.addToLogQueue(message);	
						break;
					case UTC:
							chatroomManager.sendMessageToChatroom(output, message, listOfClients);
							logManager.addToLogQueue(message);
						break;
					default:
						break;
				}	
			}
		}
		catch(IOException | ClassNotFoundException e) {
			System.err.println("Error processing Client Message: " + e.getMessage());
		}
		finally {
			clientThreads.remove(userID);
			MessageCreator create = new MessageCreator(MessageType.LOGOUT);
			create.setFromUserID(userID);
			create.setFromUserName(userManager.getUsername(userID));
			userManager.logout(create.createMessage());
			closeResources(clientSocket, input, output, userID);
		}
		
	}
	
	private void sendUserMapUpdates(Integer userID, String username, Boolean addUser) {
		MessageCreator messageCreator = new MessageCreator(MessageType.UPDATEUM);
		messageCreator.setFromUserID(userID);
		messageCreator.setFromUserName(username);
		if(addUser) {	
			messageCreator.setContents("Add");
			
			// Synchronizes the sending of the update message!
			listOfClients.values().parallelStream().forEach(output ->{
				try {
					output.writeObject(messageCreator.createMessage());
					output.flush();
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
				
			});
		}
		else {
			listOfClients.remove(userID);
			messageCreator.setContents("Remove");
			
			// Synchronizes the sending of the update message!
			listOfClients.values().parallelStream().forEach(output ->{
				try {
					output.writeObject(messageCreator.createMessage());
					output.flush();
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
				
			});
		}
		
	}
	
	protected void sendChatroomUpdates(Integer chatroomID, Boolean addChatroom) {
	    MessageCreator messageCreator = new MessageCreator(MessageType.UPDATECM);
	    messageCreator.setFromUserID(chatroomID);  // Use chatroom ID

	    if (addChatroom) {
	        messageCreator.setContents("Add");

	        // Retrieve the chatroom using the ChatroomManager
	        Chatroom chatroom = chatroomManager.getChatroom(chatroomID);
	        if (chatroom != null) {
	            messageCreator.setChatroom(chatroom);  // Set chatroom details
	            messageCreator.setToChatroom(chatroomID);

	            // Notify all clients about the chatroom addition
	            listOfClients.values().parallelStream().forEach(output -> {
	                try {
	                    output.writeObject(messageCreator.createMessage());
	                    output.flush();
	                } catch (IOException e) {
	                    System.err.println("Error sending chatroom update to a client!");
	                }
	            });
	        }
	    } else {
	        // Remove the chatroom from list
	        chatroomManager.deleteChatroom(null, new Message(new MessageCreator(MessageType.UPDATECM)), listOfClients);
	        messageCreator.setContents("Remove");

	        // Notify all clients about the chatroom removal
	        listOfClients.values().parallelStream().forEach(output -> {
	            try {
	                output.writeObject(messageCreator.createMessage());
	                output.flush();
	            } catch (IOException e) {
	                System.err.println("Error sending chatroom removal update to a client!");
	            }
	        });
	    }
	}

	
	public void closeResources(Socket clientSocket, ObjectInputStream input, ObjectOutputStream output, Integer userID) {
		try {
			if(clientSocket != null) clientSocket.close();
			if(input != null) input.close();
			if(output != null) output.close();
			sendUserMapUpdates(userID, userManager.getUsername(userID), false);
		}
		catch(IOException e) {
			System.err.println("Error closing resources!");
			e.printStackTrace();
		}
	}

}
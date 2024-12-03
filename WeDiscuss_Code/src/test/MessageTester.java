package test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.ConcurrentHashMap;
import java.util.ConcurrentMap;

import shared.*;

public class MessageTest {
    private MessageCreator creator;
    private Message message;
    private User testUser;
    private Chatroom testChatroom;
    private ConcurrentMap<Integer, Chatroom> chatroomMap;
    private ConcurrentMap<Integer, String> userMap;

    @BeforeEach
    public void setUp() {
        //Initialize basic test objects
        creator = new MessageCreator(MessageType.UTU);
        creator.setContents("Test message");
        creator.setFromUserName("sender");
        creator.setUserName("receiver");
        creator.setToUserID(2);

        //Initialize maps
        chatroomMap = new ConcurrentHashMap<>();
        userMap = new ConcurrentMap<>();

        //create test chatroom
        testChatroom = new Chatroom(1, 1);
        chatroomMap.put(1, testChatroom);

        //Add test data to maps
        userMap.put(1, "sender");
        userMap.put(2, "receiver");

        //Set maps in creator
        creator.setChatroomMap(chatroomMap);
        creator.setUserMap(userMap);

        //Create message
        message = creator.createMessage();
    }

    @Test
    public void testMessageConstructor() {
        assertNotNull(message, "Message should not be null");
        assertEquals("Test message", message.getContents());
        assertEquals("sender", message.getFromUserName());
        assertEquals(1, message.getFromUserName());
        assertEquals("receiver", message.getToUserName());
        assertEquals(2, message.getToUserID());
        assertEquals(MessageType.UTU, message.getMessageType());
    }


    @Test
    public void testGetDataSent() {
        Date now = new Date();
        assertTrue(Math.abs(getTime() - message.getDateSent().getTime()) < 1000,
            "Message date should be recent");
    }

    @Test
    public void testMessageIDCounter() {
        Message message1 = creator.createMessage();
        Message message2 = creator.createMessage();
        assertTrue(message2.getMessageID() > message1.getMessageID(),
            "Message IDs should be sequential");
    }

    @Test
    public void testToStringUTU() {
        MessageCreator utuCreator = new MessageCreator(MessageType.UTU);
        utuCreator.setContents("Hello");
        utuCreator.setFromUserName("Jane");
        Message utuMessage = utuCreator.createMessage();

        // UTU format: "FROM: fromUserName/ contents"
        String expected = "FROM: Jane/ Hello";
        assertEquals(expected, utuMessage.toString());
    }

    @Test
    public void testToStringUTC() {
        MessageCreator utcCreator = new MessageCreator(MessageType.UTC);
        utcCreator.setContents("Hello room");
        utcCreator.setFromUserName("John");
        Message utcMessage = utcCreator.createMessage();

        // UTC format: "FROM: fromUserName/ contents"
        String expected = "FROM: John/ Hello room";
        assertEquals(expected, utcMessage.toString());
    }

    @Test
    public void testToStringGUL() {
        MessageCreator gulCreator = new MessageCreator(MessageType.GUL);
        gulCreator.setContents("Log request");
        gulCreator.setFromUserName("Jane");
        gulCreator.setToUserName("John");
        Message gulMessage = gulCreator.createMessage();

        // GUL format: "FROM: fromUserName/ contents /TO toUserName"
        String expected = "FROM: Jane/ Log request  /TO John";
        assertEquals(expected, gulMessage.toString());
    }

    @Test
    public void testToStringGCL() {
        MessageCreator gclCreator = new MessageCreator(MessageType.GCL);
        gclCreator.setContents("Chatroom log request");
        gclCreator.setFromUserName("Jane");
        gclCreator.setToChatroom(123);
        Message gclMessage = gclCreator.createMessage();

        // GCL format: "FROM: fromUserName/ contents /TO Chatroom ID:toChatroomID"
        String expected = "FROM: Jane/ Chatroom log request  /TO Chatroom ID:123";
        assertEquals(expected, gclMessage.toString());
    }

    @Test
    public void testStoreChatroomMessage() {
        creator.setMessageType(MessageType.UTC);
        creator.setToChatroom(1);
        creator.setFromUserName("Jane");
        creator.setFromUserID(1);
        creator.setContents("Hello chatroom");
        Message chatroomMessage = creator.createMessage();

        String stored = chatroomMessage.storeChatroomMessage();
        String[] parts = stored.split("\\|");

        // Format: contents|timestamp|chatroomID|fromUserName|fromUserID|messageType
        assertEquals("Hello chatroom", parts[0], "Contents should match");
        assertTrue(parts[1].matches("\\d+"), "Timestamp should be numeric");
        assertEquals("1", parts[2], "Chatroom ID should match");
        assertEquals("Jane", parts[3], "Sender username should match");
        assertEquals("1", parts[4], "Sender ID should match");
        assertEquals("UTC", parts[5], "Message type should match");
    }

    @Test
    public void testStoreInboxMessage() {
        creator.setMessageType(MessageType.UTU);
        creator.setContents("Hello inbox");
        creator.setToUserName("John");
        creator.setToUserID(2);
        creator.setFromUserName("Jane");
        creator.setFromUserID(1);
        Message message = creator.createMessage();

        String stored = message.storeInboxMessage();
        String[] parts = stored.split("\\|");

        // Format: contents|timestamp|toUserName|toUserID|fromUserName|fromUserID|messageType
        assertEquals("Hello inbox", parts[0], "Contents should match");
        assertTrue(parts[1].matches("\\d+"), "Timestamp should be numeric");
        assertEquals("John", parts[2], "Receiver username should match");
        assertEquals("2", parts[3], "Receiver ID should match");
        assertEquals("Jane", parts[4], "Sender username should match");
        assertEquals("1", parts[5], "Sender ID should match");
        assertEquals("UTU", parts[6], "Message type should match");
    }
    @Test
    public void testTypeToString() {
        assertEquals("UTU", message.typeToString(MessageType.UTU));
        assertEquals("UTC", message.typeToString(MessageType.UTC));
        assertEquals("LOGIN", message.typeToString(MessageType.LOGIN));
        assertEquals("LOGOUT", message.typeToString(MessageType.LOGOUT));
        assertEquals("CC", message.typeToString(MessageType.CC));
    }

    @Test
    public void testGetUserMap() {
        ConcurrentMap<Integer, String> retrievedMap = message.getUserMap();
        assertNotNull(retrievedMap, "User map should not be null");
        assertEquals(userMap, retrievedMap);
        assertEquals("sender", retrievedMap.get(1));
        assertEquals("receiver", retrievedMap.get(2));
    }

    @Test
    public void testGetChatroomMap() {
        ConcurrentMap<Integer, Chatroom> retrievedMap = message.getChatroomMap();
        assertNotNull(retrievedMap, "Chatroom map should not be null");
        assertEquals(chatroomMap, retrievedMap);
        assertTrue(retrievedMap.containsKey(1));
    }

    @Test
    public void testGetChatroom() {
        creator.setChatroom(testChatroom);
        Message chatroomMessage = creator.createMessage();
        assertNotNull(chatroomMessage.getChatroom(), "Chatroom should not be null");
        assertEquals(1, chatroomMessage.getChatroom().getChatroomID());
    }
}
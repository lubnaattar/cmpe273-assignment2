package edu.sjsu.cmpe.library.repository;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsMessage;

import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;

public class BookRepository implements BookRepositoryInterface {
    /** In-memory map to store books. (Key, Value) -> (ISBN, Book) */
    private final ConcurrentHashMap<Long, Book> bookInMemoryMap;

    /** Never access this key directly; instead use generateISBNKey() */
    private long isbnKey;
    String user = "";
	String password = ""; 
	String host = "";
	int port =  0;
	String queueName = "";
	String topicName = "";
	String libraryName = "";



    public BookRepository() {
	bookInMemoryMap = seedData();
	isbnKey = 0;
    }

    private ConcurrentHashMap<Long, Book> seedData(){
	ConcurrentHashMap<Long, Book> bookMap = new ConcurrentHashMap<Long, Book>();
	Book book = new Book();
	book.setIsbn(1);
	book.setCategory("computer");
	book.setTitle("Java Concurrency in Practice");
	try {
	    book.setCoverimage(new URL("http://goo.gl/N96GJN"));
	} catch (MalformedURLException e) {
	    // eat the exception
	}
	bookMap.put(book.getIsbn(), book);

	book = new Book();
	book.setIsbn(2);
	book.setCategory("computer");
	book.setTitle("Restful Web Services");
	try {
	    book.setCoverimage(new URL("http://goo.gl/ZGmzoJ"));
	} catch (MalformedURLException e) {
	    // eat the exception
	}
	bookMap.put(book.getIsbn(), book);

	return bookMap;
    }

    /**
     * This should be called if and only if you are adding new books to the
     * repository.
     * 
     * @return a new incremental ISBN number
     */
    private final Long generateISBNKey() {
	// increment existing isbnKey and return the new value
	return Long.valueOf(++isbnKey);
    }

    /**
     * This will auto-generate unique ISBN for new books.
     */
    @Override
    public Book saveBook(Book newBook) {
	checkNotNull(newBook, "newBook instance must not be null");
	// Generate new ISBN
	Long isbn = generateISBNKey();
	newBook.setIsbn(isbn);
	// TODO: create and associate other fields such as author

	// Finally, save the new book into the map
	bookInMemoryMap.putIfAbsent(isbn, newBook);

	return newBook;
    }
    
    public Book addNewBook(Book newBook) {
    	checkNotNull(newBook, "newBook instance must not be null");
    	// Generate new ISBN
    	//Long isbn = generateISBNKey();
    	Long isbn =newBook.getIsbn();
    	 newBook.setIsbn(newBook.getIsbn());
    	// TODO: create and associate other fields such as author

    	// Finally, save the new book into the map
    	bookInMemoryMap.putIfAbsent(isbn, newBook);

    	return newBook;
        }


    /**
     * @see edu.sjsu.cmpe.library.repository.BookRepositoryInterface#getBookByISBN(java.lang.Long)
     */
    @Override
    public Book getBookByISBN(Long isbn) {
	checkArgument(isbn > 0,
		"ISBN was %s but expected greater than zero value", isbn);
	return bookInMemoryMap.get(isbn);
    }

    @Override
    public List<Book> getAllBooks() {
	return new ArrayList<Book>(bookInMemoryMap.values());
    }
    
    public void updateLibraryAfterResponse(Book newBook){
    	Long isbn = newBook.getIsbn();
    	List<Book> allBooks = getAllBooks();
    	for(Book b1 : allBooks){
    		if(b1.getIsbn() == isbn){
    			b1.setStatus(Book.Status.available);
    		}
    		else
    		{
    			Book addedBook = addNewBook(newBook);    			
    		}
    	}
    	
    }

    public void putConfiguration(LibraryServiceConfiguration configuration){
    	user = configuration.getApolloUser();
    	password = configuration.getApolloPassword(); 
    	host = configuration.getApolloHost();
    	port =  configuration.getApolloPort(); 	 
    	queueName = configuration.getStompQueueName();
    	topicName = configuration.getStompTopicName();
    	libraryName = configuration.getLibraryName();
    }
    
    public void producer(Long isbnValue,Book book) throws JMSException{
    	
    	String queue = queueName;
    	String libName = libraryName;
    	//String destination = queueName;

    	StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
    	factory.setBrokerURI("tcp://" + host + ":" + port);

    	Connection connection = factory.createConnection(user, password);
    	connection.start();
    	Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    	Destination dest = new StompJmsDestination(queue);
    	MessageProducer producer = session.createProducer(dest);
    //	producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

    	System.out.println("Sending messages to " + queue + "...");
    	String data = libName + ":" + isbnValue;
    	TextMessage msg = session.createTextMessage(data);
    	msg.setLongProperty("id", System.currentTimeMillis());
    	producer.send(msg);
    	
    	///queue/{last-5-digit-of-sjsu-id}.book.orders
    }
    

    /*
     * Delete a book from the map by the isbn. If the given ISBN was invalid, do
     * nothing.
     * 
     * @see
     * edu.sjsu.cmpe.library.repository.BookRepositoryInterface#delete(java.
     * lang.Long)
     */
    @Override
    public void delete(Long isbn) {
	bookInMemoryMap.remove(isbn);
    }
    
	public void listenService() {
		String receivedData = "";

		try {
			String user = "admin";
			String password = "password";
			String host = "54.219.156.168";
			int port = 61613;
			String destination = topicName; //topic name from configuration

			StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
			factory.setBrokerURI("tcp://" + host + ":" + port);

			Connection connection = factory.createConnection(user, password);
			connection.start();
			Session session = connection.createSession(false,
					Session.AUTO_ACKNOWLEDGE);
			Destination dest = new StompJmsDestination(destination);

			MessageConsumer consumer = session.createConsumer(dest);
			System.currentTimeMillis();
			System.out.println("Waiting for messages...");
			while (true) {
				Message msg = consumer.receive();
				
				if (msg instanceof TextMessage) {
					receivedData = ((TextMessage) msg).getText();
					if ("SHUTDOWN".equals(receivedData)) {
						break;
					}
					System.out.println("Received message = " + receivedData);					
					
					
					//String data = "1:"Restful Web Services":"computer"/":/""http://goo.gl/ZGmzoJ"";
						//String receivedData = "1" + ":\"" + "Restful Web Services" + "\":\"" + "computer" + "\":\"" + "http://goo.gl/ZGmzoJ" + "\"";
						String args1[] = receivedData.split("\"");
						
						String isbn = args1[0].split(":")[0];
						String title = args1[1];
						String category = args1[3];
						String coverImage = args1[5];
						Book receivedBook = new Book();
					
					receivedBook.setIsbn(Long.parseLong(isbn));					
					receivedBook.setTitle(title);
					receivedBook.setCategory(category);
					
					try {
						receivedBook.setCoverimage(new URL(coverImage));
					} catch (MalformedURLException e) {
					    // eat the exception
					}
					//BookRepositoryInterface bookRepository = new BookRepository();
					updateLibraryAfterResponse(receivedBook);
					///

				} else if (msg instanceof StompJmsMessage) {
					StompJmsMessage smsg = ((StompJmsMessage) msg);
					receivedData = smsg.getFrame().contentAsString();
					if ("SHUTDOWN".equals(receivedData)) {
						break;
					}
					System.out.println("Received message = " + receivedData);

				} else {
					System.out.println("Unexpected message type: "
							+ msg.getClass());
				}
			}
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

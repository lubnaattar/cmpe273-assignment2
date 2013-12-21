package edu.sjsu.cmpe.procurement.jobs;

import java.util.ArrayList;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.eclipse.jetty.util.ajax.JSONDateConvertor;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;
import edu.sjsu.cmpe.procurement.domain.Book;

/**
 * This job will run at every 5 second.
 */
@Every("300s")
public class ProcurementSchedulerJob extends Job {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void doJob() {

		try {

			ArrayList<Long> longArray = new ArrayList<Long>();
			longArray = consumeQueueMessages();
			log.info("Messages from Queue = {}", longArray.size());
			if (!longArray.isEmpty()) {
				PostMessagesToPublisher(longArray);
				getMessagesFromPublisher();
			}

		} catch (Exception e) {
			// System.out.println(e.printStackTrace());
		}
		String strResponse = ProcurementService.jerseyClient.resource(
				"http://ip.jsontest.com/").get(String.class);
		log.debug("Response from jsontest.com: {}", strResponse);

	}

	public ArrayList<Long> consumeQueueMessages() throws JMSException {
		String user = "admin";
		String password = "password";
		String host = "54.219.156.168";
		int port = 61613;

		// ArrayList<Book> booksList;

		// Long isbnArray[] = new Long[50];
		ArrayList<Long> isbnArray = new ArrayList<Long>();

		int i = 0;
		String queue = "/queue/84157.book.orders";
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + host + ":" + port);

		Connection connection = factory.createConnection(user, password);
		connection.start();
		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);
		Destination dest = new StompJmsDestination(queue);

		MessageConsumer consumer = session.createConsumer(dest);
		System.out.println("Waiting for messages from " + queue + "...");

		long waitUntil = 5000; // wait for 5 sec

		while (true) {
			Message msg = consumer.receive(waitUntil);
			if (msg == null) {
				break;
			}
			if (msg instanceof TextMessage) {
				String body = ((TextMessage) msg).getText();
				System.out.println("Received message = " + body);
				String isbnSplitArray[] = body.split(":");
				Long isbnValue = Long.parseLong(isbnSplitArray[1]);
				System.out.println(isbnValue);
				isbnArray.add(i, isbnValue);
				i++;

			} else if (msg == null) {
				System.out
						.println("No new messages. Existing due to timeout - "
								+ waitUntil / 1000 + " sec");
				break;
			} else {
				System.out
						.println("Unexpected message type: " + msg.getClass());
			}
		} // end while loop
		connection.close();
		System.out.println("Done");
		return isbnArray;
	}

	public void PostMessagesToPublisher(ArrayList<Long> longArray) {
		try {

			Client client = Client.create();
			WebResource webResource = client
					.resource("http://54.219.156.168:9000/orders");

			// String isbnArrayValue = "";
			/*
			 * for (int i = 0; i <longArray.size(); i++ ){ Long isbnValue =
			 * longArray.get(i); if(i== 0){ isbnArrayValue =
			 * isbnArrayValue.concat(isbnValue.toString()); } isbnArrayValue =
			 * isbnArrayValue.concat(",").concat(isbnValue.toString()); }
			 */

			// String input =
			// "{\"id\":\"84157\",\"order_book_isbns\":["+isbnArrayValue+"]}";
			String input = "{\"id\":\"84157\",\"order_book_isbns\":"
					+ longArray + "}";

			ClientResponse response = webResource.type("application/json")
					.post(ClientResponse.class, input);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ response.getStatus());
			}

			System.out.println("Output from Server .... \n");
			String output = response.getEntity(String.class);
			System.out.println(output);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void getMessagesFromPublisher() {
		String output = "";
		String user = "admin";
		String password = "password";
		String host = "54.219.156.168";
		int port = 61613;

		try {

			Client client = Client.create();

			WebResource webResource = client
					.resource("http://54.219.156.168:9000/orders/84157");

			ClientResponse response = webResource.accept("application/json")
					.get(ClientResponse.class);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ response.getStatus());
			}
			output = response.getEntity(String.class);
			if (!output.isEmpty()) {

				System.out.println("Output from Server .... \n");
				System.out.println(output);

				String array1[] = output.split("\\[");
				 array1 = array1[1].split("\\}");
				for (int i = 0; i < array1.length; i++) {
					array1[i] = array1[i] + "}";
					if (i != 0)
						array1[i] = array1[i].substring(2);
				}

				/*String[] a3 = new String[a2.length];
				for (int i = 0; i < a2.length - 1; i++) {
					a3[i] = a2[i];
				}*/

			//	log.info("Array size = " + a3.length);
				JSONArray nameArray = (JSONArray) JSONSerializer.toJSON(array1);

				//log.info("Array 2= {}", a2.length);
				log.info("NameArray size = {}", nameArray.size());
				if (nameArray != null) {
					for (Object jsonObject : nameArray) {
						JSONObject json = (JSONObject) JSONSerializer
								.toJSON(jsonObject);

						// System.out.println("category is:" +
						// json.get("category"));
						// System.out.println("isbn is:" + json.get("isbn"));
						if (json != null) {
							log.info("Before getting ISBN");
							Integer isbn = 0;
							String title = "";
							String category = "";
							String coverImage ="";
							String data = "";
							
							if(json.get("isbn") != null){
								isbn = Integer.valueOf(json.get("isbn")	+ "");
								title = (String) json.get("title");
								category = (String) json.get("category");
								coverImage = (String) json.get("coverimage");

								data = isbn + ":\"" + title + "\":\""
										+ category + "\":\"" + coverImage + "\"";

							}
							
							log.info("Afer ISBN=" + isbn);
							
							if (category.equalsIgnoreCase("computer")) {
								String destination = "/topic/84157.book.computer";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category.equalsIgnoreCase("management")) {
								String destination = "/topic/84157.book.management";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category.equalsIgnoreCase("comics")) {
								String destination = "/topic/84157.book.comics";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category
									.equalsIgnoreCase("selfimprovement")) {
								String destination = "/topic/84157.book.selfimprovement";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							}

						}
					}// for

				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/*
	 * public void PublishMessagesToLibrary() throws JMSException {
	 * 
	 * 
	 * 
	 * 
	 * // producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	 * 
	 * //
	 * 
	 * //
	 * 
	 * String data = "123:Restful Web Services:computer:http://goo.gl/ZGmzoJ";
	 * // String data = isbn+ ":" + title + ":" + category + ":" + coverImage
	 * 
	 * }
	 */
}

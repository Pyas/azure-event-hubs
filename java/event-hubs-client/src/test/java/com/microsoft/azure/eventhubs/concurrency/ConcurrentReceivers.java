package com.microsoft.azure.eventhubs.concurrency;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.azure.eventhubs.*;
import com.microsoft.azure.eventhubs.lib.TestBase;
import com.microsoft.azure.eventhubs.lib.TestEventHubInfo;
import com.microsoft.azure.servicebus.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.ServiceBusException;

public class ConcurrentReceivers
{
	TestEventHubInfo eventHubInfo;
	ConnectionStringBuilder connStr;
	int partitionCount = 4;
	EventHubClient sender;
	
	@Before
	public void initializeEventHub()  throws Exception
	{
		Assume.assumeTrue(TestBase.isServiceRun());
		
    	eventHubInfo = TestBase.checkoutTestEventHub();
		connStr = new ConnectionStringBuilder(
				eventHubInfo.getNamespaceName(), eventHubInfo.getName(), eventHubInfo.getSasRule().getKey(), eventHubInfo.getSasRule().getValue());
	
		sender = EventHubClient.createFromConnectionString(connStr.toString(), true).get();
		
		for (int i=0; i < partitionCount; i++)
		{
			TestBase.pushEventsToPartition(sender, Integer.toString(i), 10);
		}
	}
	
	@Test()
	public void testParallelReceivers() throws ServiceBusException, InterruptedException, ExecutionException, IOException
	{
		String consumerGroupName = eventHubInfo.getRandomConsumerGroup();
		
		for (int repeatCount = 0; repeatCount< 4; repeatCount++)
		{
			EventHubClient[] ehClients = new EventHubClient[partitionCount];
			PartitionReceiver[] receivers = new PartitionReceiver[partitionCount];
			try
			{
				for(int i=0; i < partitionCount; i++)
				{
					ehClients[i] = EventHubClient.createFromConnectionString(connStr.toString(), true).get();
					receivers[i] = ehClients[i].createReceiver(consumerGroupName, Integer.toString(i), Instant.now()).get();
					receivers[i].setReceiveHandler(new EventCounter(Integer.toString(i)));
					System.out.println("created receiver on partition: " + Integer.toString(i));
				}
			}
			finally
			{
				for (int i=0; i < partitionCount; i++)
				{
					System.out.println("closing receivers: " + Integer.toString(i));
					if (receivers[i] != null)
					{
						receivers[i].close();
					}
					
					if (ehClients[i] != null)
					{
						ehClients[i].close();
					}
				}
			}
		}
	}
	
	@After()
	public void cleanup()
	{
		if (this.sender != null)
		{
			this.sender.close();
		}
	}

	public static final class EventCounter extends PartitionReceiveHandler
	{
		private long count;
		private String partitionId;

		public EventCounter(final String partitionId)
		{ 
			count = 0;
			this.partitionId = partitionId;
		}
		
		@Override
		public void onReceive(Iterable<EventData> events)
		{
			for(EventData event: events)
			{
				System.out.println(String.format("Partition(%s): Counter: %s, Offset: %s, SeqNo: %s, EnqueueTime: %s, PKey: %s", 
						 this.partitionId, this.count, event.getSystemProperties().getOffset(), event.getSystemProperties().getSequenceNumber(), event.getSystemProperties().getEnqueuedTime(), event.getSystemProperties().getPartitionKey()));
				
				count++;
			}
		}

		@Override
		public void onError(Exception exception)
		{
		}		
	}
}

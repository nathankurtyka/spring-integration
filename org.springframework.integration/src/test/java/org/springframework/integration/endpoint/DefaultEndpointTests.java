/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.endpoint;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.springframework.integration.bus.DefaultMessageBus;
import org.springframework.integration.channel.ChannelRegistry;
import org.springframework.integration.channel.DefaultChannelRegistry;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.handler.MessageHandler;
import org.springframework.integration.handler.TestHandlers;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessageBuilder;
import org.springframework.integration.message.MessageHandlingException;
import org.springframework.integration.message.MessageRejectedException;
import org.springframework.integration.message.MessageTarget;
import org.springframework.integration.message.StringMessage;
import org.springframework.integration.message.selector.MessageSelector;
import org.springframework.integration.message.selector.MessageSelectorChain;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class DefaultEndpointTests {

	@Test
	public void outputChannel() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setOutputChannel(channel);
		Message<?> message = MessageBuilder.fromPayload("foo").build();
		endpoint.send(message);
		Message<?> reply = channel.receive(0);
		assertNotNull(reply);
		assertEquals("FOO", reply.getPayload());
	}

	@Test
	public void returnAddressHeader() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		Message<?> message = MessageBuilder.fromPayload("foo").setReturnAddress(channel).build();
		endpoint.send(message);
		Message<?> reply = channel.receive(0);
		assertNotNull(reply);
		assertEquals("FOO", reply.getPayload());
	}

	@Test
	public void nextTargetHeaderTakesPrecedence() {
		QueueChannel channel1 = new QueueChannel(1);
		QueueChannel channel2 = new QueueChannel(1);
		QueueChannel channel3 = new QueueChannel(1);
		MessageHandler handler = new TestNextTargetSettingHandler(channel1);
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setOutputChannel(channel2);
		Message<?> message = MessageBuilder.fromPayload("foo").setReturnAddress(channel3).build();
		endpoint.send(message);
		Message<?> reply1 = channel1.receive(0);
		assertNotNull(reply1);
		assertEquals("foo", reply1.getPayload());
		Message<?> reply2 = channel2.receive(0);
		assertNull(reply2);
		Message<?> reply3 = channel3.receive(0);
		assertNull(reply3);
	}

	@Test
	public void returnAddressHeaderWithChannelName() {
		QueueChannel channel = new QueueChannel(1);
		channel.setBeanName("testChannel");
		ChannelRegistry channelRegistry = new DefaultMessageBus();
		channelRegistry.registerChannel(channel);
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setChannelRegistry(channelRegistry);
		Message<?> message = MessageBuilder.fromPayload("foo").setReturnAddress("testChannel").build();
		endpoint.send(message);
		Message<?> reply = channel.receive(0);
		assertNotNull(reply);
		assertEquals("FOO", reply.getPayload());
	}

	@Test
	public void nextTargetHeaderWithChannelName() {
		QueueChannel channel1 = new QueueChannel(1);
		QueueChannel channel2 = new QueueChannel(1);
		QueueChannel channel3 = new QueueChannel(1);
		channel1.setBeanName("testChannel");
		ChannelRegistry channelRegistry = new DefaultMessageBus();
		channelRegistry.registerChannel(channel1);
		MessageHandler handler = new TestNextTargetSettingHandler("testChannel");
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setChannelRegistry(channelRegistry);
		endpoint.setOutputChannel(channel2);
		Message<?> message = MessageBuilder.fromPayload("foo").setReturnAddress(channel3).build();
		endpoint.send(message);
		Message<?> reply1 = channel1.receive(0);
		assertNotNull(reply1);
		assertEquals("foo", reply1.getPayload());
		Message<?> reply2 = channel2.receive(0);
		assertNull(reply2);
		Message<?> reply3 = channel3.receive(0);
		assertNull(reply3);
	}

	@Test
	public void dynamicReplyChannel() throws Exception {
		final QueueChannel replyChannel1 = new QueueChannel();
		final QueueChannel replyChannel2 = new QueueChannel();
		replyChannel2.setBeanName("replyChannel2");
		ChannelRegistry channelRegistry = new DefaultChannelRegistry();
		channelRegistry.registerChannel(replyChannel2);
		MessageHandler handler = new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				return new StringMessage("foo" + message.getPayload());
			}
		};
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setChannelRegistry(channelRegistry);
		Message<String> testMessage1 = MessageBuilder.fromPayload("bar")
				.setReturnAddress(replyChannel1).build();
		endpoint.send(testMessage1);
		Message<?> reply1 = replyChannel1.receive(50);
		assertNotNull(reply1);
		assertEquals("foobar", reply1.getPayload());
		Message<?> reply2 = replyChannel2.receive(0);
		assertNull(reply2);
		Message<String> testMessage2 = MessageBuilder.fromMessage(testMessage1)
				.setReturnAddress("replyChannel2").build();
		endpoint.send(testMessage2);
		reply1 = replyChannel1.receive(0);
		assertNull(reply1);
		reply2 = replyChannel2.receive(0);
		assertNotNull(reply2);	
		assertEquals("foobar", reply2.getPayload());	
	}

	@Test
	public void noOutputChannelFallsBackToReturnAddress() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		Message<?> message = MessageBuilder.fromPayload("foo").setReturnAddress(channel).build();
		endpoint.send(message);
		Message<?> reply = channel.receive(0);
		assertNotNull(reply);
		assertEquals("FOO", reply.getPayload());
	}

	@Test
	public void unknownNextTargetChannelFallsBackToOutputChannel() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setOutputChannel(channel);
		Message<?> message = MessageBuilder.fromPayload("foo").setNextTarget("unknown").build();
		endpoint.send(message);
		Message<?> reply = channel.receive(0);
		assertNotNull(reply);
		assertEquals("FOO", reply.getPayload());
	}

	@Test(expected = MessageEndpointReplyException.class)
	public void noReplyTarget() {
		MessageHandler handler = new TestHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		Message<?> message = MessageBuilder.fromPayload("foo").build();
		endpoint.send(message);
	}

	@Test
	public void noReplyMessage() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestNullReplyHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setOutputChannel(channel);
		Message<?> message = MessageBuilder.fromPayload("foo").build();
		endpoint.send(message);
		assertNull(channel.receive(0));
	}

	@Test(expected = MessageHandlingException.class)
	public void noReplyMessageWithRequiresReply() {
		QueueChannel channel = new QueueChannel(1);
		MessageHandler handler = new TestNullReplyHandler();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(handler);
		endpoint.setRequiresReply(true);
		endpoint.setOutputChannel(channel);
		Message<?> message = MessageBuilder.fromPayload("foo").build();
		endpoint.send(message);
	}

	@Test(expected=MessageRejectedException.class)
	public void endpointWithSelectorRejecting() {
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(TestHandlers.nullHandler());
		endpoint.setSelector(new MessageSelector() {
			public boolean accept(Message<?> message) {
				return false;
			}
		});
		endpoint.send(new StringMessage("test"));
	}

	@Test
	public void endpointWithSelectorAccepting() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(TestHandlers.countDownHandler(latch));
		endpoint.setSelector(new MessageSelector() {
			public boolean accept(Message<?> message) {
				return true;
			}
		});
		endpoint.send(new StringMessage("test"));
		latch.await(100, TimeUnit.MILLISECONDS);
		assertEquals("handler should have been invoked", 0, latch.getCount());
	}

	@Test
	public void endpointWithMultipleSelectorsAndFirstRejects() {
		final AtomicInteger counter = new AtomicInteger();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(TestHandlers.countingHandler(counter));
		MessageSelectorChain selectorChain = new MessageSelectorChain();
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				counter.incrementAndGet();
				return false;
			}
		});
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				counter.incrementAndGet();
				return true;
			}
		});
		endpoint.setSelector(selectorChain);
		boolean exceptionWasThrown = false;
		try {
			endpoint.send(new StringMessage("test"));
		}
		catch (MessageRejectedException e) {
			exceptionWasThrown = true;
		}
		assertTrue(exceptionWasThrown);
		assertEquals("only the first selector should have been invoked", 1, counter.get());
	}

	@Test
	public void endpointWithMultipleSelectorsAndFirstAccepts() {
		final AtomicInteger selectorCounter = new AtomicInteger();
		AtomicInteger handlerCounter = new AtomicInteger();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(TestHandlers.countingHandler(handlerCounter));
		MessageSelectorChain selectorChain = new MessageSelectorChain();
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				selectorCounter.incrementAndGet();
				return true;
			}
		});
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				selectorCounter.incrementAndGet();
				return false;
			}
		});
		endpoint.setSelector(selectorChain);
		boolean exceptionWasThrown = false;
		try {
			endpoint.send(new StringMessage("test"));
		}
		catch (MessageRejectedException e) {
			exceptionWasThrown = true;
		}
		assertTrue(exceptionWasThrown);
		assertEquals("both selectors should have been invoked", 2, selectorCounter.get());
		assertEquals("the handler should not have been invoked", 0, handlerCounter.get());
	}

	@Test
	public void endpointWithMultipleSelectorsAndBothAccept() {
		final AtomicInteger counter = new AtomicInteger();
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(TestHandlers.countingHandler(counter));
		MessageSelectorChain selectorChain = new MessageSelectorChain();
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				counter.incrementAndGet();
				return true;
			}
		});
		selectorChain.add(new MessageSelector() {
			public boolean accept(Message<?> message) {
				counter.incrementAndGet();
				return true;
			}
		});
		endpoint.setSelector(selectorChain);
		assertTrue(endpoint.send(new StringMessage("test")));
		assertEquals("both selectors and handler should have been invoked", 3, counter.get());
	}

	@Test
	public void correlationId() {
		QueueChannel replyChannel = new QueueChannel(1);
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				return message;
			}
		});
		Message<String> message = MessageBuilder.fromPayload("test")
				.setReturnAddress(replyChannel).build();
		endpoint.send(message);
		Message<?> reply = replyChannel.receive(500);
		assertEquals(message.getHeaders().getId(), reply.getHeaders().getCorrelationId());
	}

	@Test
	public void correlationIdSetByHandlerTakesPrecedence() {
		QueueChannel replyChannel = new QueueChannel(1);
		DefaultEndpoint<MessageHandler> endpoint = new DefaultEndpoint<MessageHandler>(new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				return MessageBuilder.fromMessage(message)
						.setCorrelationId("ABC-123").build();
			}
		});
		Message<String> message = MessageBuilder.fromPayload("test")
				.setReturnAddress(replyChannel).build();
		endpoint.send(message);
		Message<?> reply = replyChannel.receive(500);
		Object correlationId = reply.getHeaders().getCorrelationId();
		assertFalse(message.getHeaders().getId().equals(correlationId));
		assertEquals("ABC-123", correlationId);
	}


    @Test
    public void nextTargetNotPropagatedPastCurrentEndpoint() {
        final QueueChannel intermediateItemChannel = new QueueChannel(1);
        final QueueChannel finalChannel = new QueueChannel(1);
        DefaultEndpoint<MessageHandler> primaryEndpoint = new DefaultEndpoint<MessageHandler>(new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				return MessageBuilder.fromMessage(message).setNextTarget(intermediateItemChannel).build();
			}
		});
        DefaultEndpoint<MessageHandler> secondaryEndpoint = new DefaultEndpoint<MessageHandler>(new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				return message;
			}
		});
        secondaryEndpoint.setOutputChannel(finalChannel);
        Message<String> message = MessageBuilder.fromPayload("test").build();
        primaryEndpoint.send(message);
        Message<?> reply = intermediateItemChannel.receive(500);
        secondaryEndpoint.send(reply);
        Message<?> replyOnIntermediateChannel = intermediateItemChannel.receive(500);
        assertNull(replyOnIntermediateChannel);
        Message<?> replyOnFinalChannel = finalChannel.receive(500);
        assertNotNull(replyOnFinalChannel);
    }


	private static class TestHandler implements MessageHandler {
		
		public Message<?> handle(Message<?> message) {
			return new StringMessage(message.getPayload().toString().toUpperCase());
		}
	}


	private static class TestNextTargetSettingHandler implements MessageHandler {

		private final Object nextTarget;

		TestNextTargetSettingHandler(Object nextTarget) {
			this.nextTarget = nextTarget;
		}

		public Message<?> handle(Message<?> message) {
			if (nextTarget instanceof MessageTarget) {
				return MessageBuilder.fromPayload(message.getPayload())
						.setNextTarget((MessageTarget) nextTarget).build();
			}
			return MessageBuilder.fromPayload(message.getPayload())
					.setNextTarget((String) nextTarget).build();
		}
	}


	private static class TestNullReplyHandler implements MessageHandler {

		public Message<?> handle(Message<?> message) {
			return null;
		}
	}

}

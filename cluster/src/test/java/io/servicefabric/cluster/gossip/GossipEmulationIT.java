package io.servicefabric.cluster.gossip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.servicefabric.transport.*;
import org.junit.*;

import rx.functions.Action1;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import io.servicefabric.cluster.ClusterEndpoint;

import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

public class GossipEmulationIT {
	private ScheduledExecutorService[] executors = new ScheduledExecutorService[16];

	private int counter = 0;
	private List<GossipProtocol> protocols;
	private LocalEventLoopGroup eventLoop;
	private DefaultEventExecutorGroup eventExecutor;

	private int lambda = 50; // milliseconds lambda to get valid time on slow computer

	private ScheduledExecutorService getNextExecutor() {
		return executors[counter++ % executors.length];
	}

	private GossipProtocol initComponent(ClusterEndpoint clusterEndpoint, List<ClusterEndpoint> members, int lostPercent, int delay) {
		TransportEndpoint endpoint = clusterEndpoint.endpoint();
		NetworkEmulatorSettings.setDefaultSettings(lostPercent, delay);

		GossipProtocol gossipProtocol = new GossipProtocol(clusterEndpoint, getNextExecutor());
		gossipProtocol.setClusterMembers(members);

		Transport transport = (Transport) TransportBuilder.newInstance(endpoint, UUID.randomUUID().toString(), eventLoop, eventExecutor)
				.useLocalChannel()
				.useNetworkEmulator()
				.build();
		gossipProtocol.setTransport(transport);

		transport.start();
		gossipProtocol.start();

		return gossipProtocol;
	}

	private static List<ClusterEndpoint> initMembers(int num) {
		List<ClusterEndpoint> result = new ArrayList<>(num);
		for (int i = 0; i < num; i++) {
			result.add(ClusterEndpoint.from("local://" + i + "@endpoint/" + i));
		}
		return result;
	}

	@Before
	public void init() {
		for (int i = 0; i < executors.length; i++) {
			executors[i] = Executors.newSingleThreadScheduledExecutor();
		}
		eventLoop = new LocalEventLoopGroup(4);
		eventExecutor = new DefaultEventExecutorGroup(4);
	}

	@After
	public void destroy() throws Exception {
		for (GossipProtocol protocol : protocols) {
			protocol.stop();
			SettableFuture<Void> close = SettableFuture.create();
			protocol.getTransport().stop(close);
			try {
				close.get(1, TimeUnit.SECONDS);
			} catch (Exception ignore) {
			}
		}
		try {
			eventExecutor.shutdownGracefully().get(1, TimeUnit.SECONDS);
		} catch (Exception ignore) {
		}
		try {
			eventLoop.shutdownGracefully().get(1, TimeUnit.SECONDS);
		} catch (Exception ignore) {
		}
		for (ScheduledExecutorService executor : executors) {
			executor.shutdownNow();
		}
	}

	@Test
	public void test10WithoutLostSmallDelay() throws Exception {
		int members = 10;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 0, 2);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}

	@Test
	public void test10Lost20SmallDelay() throws Exception {
		int members = 10;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 20, 2);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}

	@Test
	public void test100WithoutLostSmallDelay() throws Exception {
		int members = 100;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 0, 2);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}

	@Test
	public void test100Lost5BigDelay() throws Exception {
		int members = 100;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 5, 500);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}

	@Ignore
	@Test
	public void test1000Lost10BigDelay() throws Exception {
		int members = 1000;
		final List<ClusterEndpoint> TransportEndpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(TransportEndpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, TransportEndpoints, 10, 1000);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}

	@Ignore
	@Test
	public void test10000Lost5SmallDelay() throws Exception {
		int members = 10000;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 5, 2);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(30, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time + "count: " + latch.getCount(), time < 30000);
		System.out.println("Time: " + time);
	}

	@Ignore
	@Test
	public void test1000WithoutLostSmallDelay() throws Exception {
		int members = 1000;
		final List<ClusterEndpoint> endpoints = initMembers(members);
		protocols = Lists.newArrayList(Collections2.transform(endpoints,
				new Function<ClusterEndpoint, GossipProtocol>() {
					@Override
					public GossipProtocol apply(final ClusterEndpoint input) {
						return initComponent(input, endpoints, 0, 2);
					}
				}));

		final CountDownLatch latch = new CountDownLatch(members - 1);
		for (final GossipProtocol protocol : protocols) {
			protocol.listen().subscribe(new Action1<Gossip>() {
				@Override
				public void call(Gossip gossip) {
					latch.countDown();
				}
			});
		}

		long start = System.currentTimeMillis();
		protocols.get(0).spread("qualifier1", null);
		latch.await(20, TimeUnit.SECONDS);
		long time = System.currentTimeMillis() - start;
		Assert.assertTrue("Time: " + time, time < 20000 + lambda);
		System.out.println("Time: " + time);
	}
}

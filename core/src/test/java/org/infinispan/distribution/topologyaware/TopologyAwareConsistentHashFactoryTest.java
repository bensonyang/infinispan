/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.distribution.topologyaware;

import org.infinispan.commons.hash.MurmurHash3;
import org.infinispan.distribution.TestTopologyAwareAddress;
import org.infinispan.distribution.ch.ConsistentHashFactory;
import org.infinispan.distribution.ch.DefaultConsistentHash;
import org.infinispan.distribution.ch.TopologyAwareConsistentHashFactory;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.TopologyAwareAddress;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * @author Mircea.Markus@jboss.com
 * @author Dan Berindei
 * @since 4.2
 */
@Test(groups = "unit", testName = "topologyaware.TopologyAwareConsistentHashFactoryTest")
public class TopologyAwareConsistentHashFactoryTest extends AbstractInfinispanTest {
   
   private static final Log log = LogFactory.getLog(TopologyAwareConsistentHashFactoryTest.class);
   private static final int CLUSTER_SIZE = 10;
   public int numSegments = 100;

   private TestTopologyAwareAddress[] testAddresses;
   private List<Address> chMembers;
   private ConsistentHashFactory<DefaultConsistentHash> chf;
   private DefaultConsistentHash ch;

   @BeforeMethod()
   public void setUp() {
      chf = createConsistentHashFactory();
      chMembers = new ArrayList<Address>(CLUSTER_SIZE);
      testAddresses = new TestTopologyAwareAddress[CLUSTER_SIZE];
      for (int i = 0; i < 10; i++) {
         testAddresses[i] = new TestTopologyAwareAddress(i * 100);
         testAddresses[i].setName(Character.toString((char) ('A' + i)));
      }
   }

   protected ConsistentHashFactory<DefaultConsistentHash> createConsistentHashFactory() {
      return new TopologyAwareConsistentHashFactory();
   }

   public void testNumberOfOwners() {
      addNode(testAddresses[0], "m0", null, null);

      updateConsistentHash(1);
      assertEquals(ch.locateOwners(testAddresses[0]).size(), 1);
      updateConsistentHash(2);
      assertEquals(ch.locateOwners(testAddresses[0]).size(), 1);

      addNode(testAddresses[1], "m1", null, null);

      updateConsistentHash(1);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 1);
      }
      updateConsistentHash(2);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 2);
      }
      updateConsistentHash(3);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 2);
      }

      addNode(testAddresses[2], "m0", null, null);

      updateConsistentHash(1);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 1);
      }
      updateConsistentHash(2);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 2);
      }
      updateConsistentHash(3);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 3);
      }
      updateConsistentHash(4);
      for (Address testAddress : testAddresses) {
         assertEquals(ch.locateOwners(testAddress).size(), 3);
      }
   }

   public void testDifferentMachines() {
      addNode(testAddresses[0], "m0", null, null);
      addNode(testAddresses[1], "m1", null, null);
      addNode(testAddresses[2], "m0", null, null);
      addNode(testAddresses[3], "m1", null, null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
   }

   public void testNumOwnerBiggerThanAvailableNodes() {
      // test first with one node
      addNode(testAddresses[0], "m0", null, null);
      addNode(testAddresses[1], "m0", null, null);
      addNode(testAddresses[2], "m0", null, null);

      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
      assertAllLocationsWithRebalance(99);
   }
   
   public void testDifferentMachines2() {
      addNode(testAddresses[0], "m0", null, null);
      addNode(testAddresses[1], "m0", null, null);
      addNode(testAddresses[2], "m1", null, null);
      addNode(testAddresses[3], "m1", null, null);
      addNode(testAddresses[4], "m2", null, null);
      addNode(testAddresses[5], "m2", null, null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testDifferentRacksAndMachines() {
      addNode(testAddresses[0], "m0", "r0", null);
      addNode(testAddresses[1], "m1", "r0", null);
      addNode(testAddresses[2], "m2", "r1", null);
      addNode(testAddresses[3], "m3", "r2", null);
      addNode(testAddresses[4], "m2", "r1", null);
      addNode(testAddresses[5], "m2", "r2", null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testAllSameMachine() {
      addNode(testAddresses[0], "m0", null, null);
      addNode(testAddresses[1], "m0", null, null);
      addNode(testAddresses[2], "m0", null, null);
      addNode(testAddresses[3], "m0", null, null);
      addNode(testAddresses[4], "m0", null, null);
      addNode(testAddresses[5], "m0", null, null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testDifferentSites() {
      addNode(testAddresses[0], "m0", null, "s0");
      addNode(testAddresses[1], "m1", null, "s0");
      addNode(testAddresses[2], "m2", null, "s1");
      addNode(testAddresses[3], "m3", null, "s1");

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testSitesMachines2() {
      addNode(testAddresses[0], "m0", null, "s0");
      addNode(testAddresses[1], "m1", null, "s1");
      addNode(testAddresses[2], "m2", null, "s0");
      addNode(testAddresses[3], "m3", null, "s2");
      addNode(testAddresses[4], "m4", null, "s1");
      addNode(testAddresses[5], "m5", null, "s1");

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testSitesMachinesSameMachineName() {
      addNode(testAddresses[0], "m0", null, "r0");
      addNode(testAddresses[1], "m0", null, "r1");
      addNode(testAddresses[2], "m0", null, "r0");
      addNode(testAddresses[3], "m0", null, "r2");
      addNode(testAddresses[4], "m0", null, "r1");
      addNode(testAddresses[5], "m0", null, "r1");

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testDifferentRacks() {
      addNode(testAddresses[0], "m0", "r0", null);
      addNode(testAddresses[1], "m1", "r0", null);
      addNode(testAddresses[2], "m2", "r1", null);
      addNode(testAddresses[3], "m3", "r1", null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testRacksMachines2() {
      addNode(testAddresses[0], "m0", "r0", null);
      addNode(testAddresses[1], "m1", "r1", null);
      addNode(testAddresses[2], "m2", "r0", null);
      addNode(testAddresses[3], "m3", "r2", null);
      addNode(testAddresses[4], "m4", "r1", null);
      addNode(testAddresses[5], "m5", "r1", null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testRacksMachinesSameMachineName() {
      addNode(testAddresses[0], "m0", "r0", null);
      addNode(testAddresses[1], "m0", "r1", null);
      addNode(testAddresses[2], "m0", "r0", null);
      addNode(testAddresses[3], "m0", "r2", null);
      addNode(testAddresses[4], "m0", "r1", null);
      addNode(testAddresses[5], "m0", "r1", null);

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   public void testComplexScenario() {
      // {s0: {r0: {m0, m1}}, s1: {r0: {m0, m1, m2}, r1: {m0}}}
      addNode(testAddresses[0], "m2", "r0", "s1");
      addNode(testAddresses[1], "m1", "r0", "s0");
      addNode(testAddresses[2], "m1", "r0", "s1");
      addNode(testAddresses[3], "m1", "r1", "s0");
      addNode(testAddresses[4], "m0", "r0", "s1");
      addNode(testAddresses[5], "m0", "r1", "s1");
      addNode(testAddresses[6], "m0", "r1", "s0");
      addNode(testAddresses[7], "m0", "r0", "s1");
      addNode(testAddresses[8], "m0", "r0", "s0");
      addNode(testAddresses[9], "m0", "r0", "s0");

      assertAllLocationsWithRebalance(1);
      assertAllLocationsWithRebalance(2);
      assertAllLocationsWithRebalance(3);
      assertAllLocationsWithRebalance(4);
   }

   private void assertAllLocationsWithRebalance(int numOwners) {
      ch = chf.create(new MurmurHash3(), numOwners, numSegments, chMembers.subList(0, 1));
      assertAllLocations(1, 1, 1, 1);

      for (int i = 2; i < chMembers.size(); i++) {
         List<Address> currentMembers = chMembers.subList(0, i);
         int expectedOwners = Math.min(numOwners, i);
         int expectedMachines = Math.min(expectedOwners, countMachines(currentMembers));
         int expectedRacks = Math.min(expectedOwners, countRacks(currentMembers));
         int expectedSites = Math.min(expectedOwners, countSites(currentMembers));
         ch = chf.updateMembers(ch, currentMembers);
         ch = chf.rebalance(ch);
         assertAllLocations(expectedOwners, expectedMachines, expectedRacks, expectedSites);
      }
      log.debugf("Created CH with members %s", chMembers);
   }

   private int countMachines(List<Address> addresses) {
      Set<String> machines = new HashSet<String>(addresses.size());
      for (Address a : addresses) {
         TopologyAwareAddress taa = (TopologyAwareAddress) a;
         machines.add(taa.getMachineId() + taa.getRackId() + taa.getSiteId());
      }
      return machines.size();
   }

   private int countRacks(List<Address> addresses) {
      Set<String> racks = new HashSet<String>(addresses.size());
      for (Address a : addresses) {
         TopologyAwareAddress taa = (TopologyAwareAddress) a;
         racks.add(taa.getRackId() + taa.getSiteId());
      }
      return racks.size();
   }

   private int countSites(List<Address> addresses) {
      Set<String> sites = new HashSet<String>(addresses.size());
      for (Address a : addresses) {
         TopologyAwareAddress taa = (TopologyAwareAddress) a;
         sites.add(taa.getSiteId());
      }
      return sites.size();
   }

   private void assertAllLocations(int expectedOwners, int expectedMachines, int expectedRacks, int expectedSites) {
      for (int segment = 0; segment < numSegments; segment++) {
         assertSegmentLocation(segment, expectedOwners, expectedMachines, expectedRacks, expectedSites);
      }
   }

   public void testConsistencyWhenNodeLeaves() {
      addNode(testAddresses[0], "m2", "r0", "s1");
      addNode(testAddresses[1], "m1", "r0", "s0");
      addNode(testAddresses[2], "m1", "r0", "s1");
      addNode(testAddresses[3], "m1", "r1", "s0");
      addNode(testAddresses[4], "m0", "r0", "s1");
      addNode(testAddresses[5], "m0", "r1", "s1");
      addNode(testAddresses[6], "m0", "r1", "s0");
      addNode(testAddresses[7], "m0", "r0", "s3");
      addNode(testAddresses[8], "m0", "r0", "s2");
      addNode(testAddresses[9], "m0", "r0", "s0");

      updateConsistentHash(3);

      for (Address addr: chMembers) {
         log.debugf("Removing node %s" + addr);
         List<Address> addressCopy = new ArrayList<Address>(chMembers);
         addressCopy.remove(addr);
         DefaultConsistentHash newCH = chf.updateMembers(ch, addressCopy);
         newCH = chf.rebalance(newCH);

         // Allow a small number of segment moves, even though this is a leave, because the CH factory
         // generates extra moves trying to balance the CH.
         AtomicInteger movedSegmentsCount = new AtomicInteger(0);
         for (int segment = 0; segment < numSegments; segment++) {
            checkConsistency(segment, 3, ch.locateOwnersForSegment(segment), addr, newCH, movedSegmentsCount);
         }
         assert movedSegmentsCount.get() < 5 :
               String.format("Too many moved segments after leave: %d. CH after leave is: %s\nPrevious: %s",
                     movedSegmentsCount.get(), newCH, ch);
      }
   }

   private void checkConsistency(int segment, int replCount, List<Address> originalOwners,
                                 Address removedAddress, DefaultConsistentHash newCH,
                                 AtomicInteger movedSegmentsCount) {
      List<Address> currentOwners = newCH.locateOwnersForSegment(segment);
      originalOwners = new ArrayList<Address>(originalOwners);
      originalOwners.remove(removedAddress);

      assertEquals(replCount, currentOwners.size(), currentOwners.toString());
      if (!currentOwners.containsAll(originalOwners))
         movedSegmentsCount.incrementAndGet();
   }


   private void assertSegmentLocation(int segment, int expectedOwners, int expectedMachines, int expectedRacks,
                                      int expectedSites) {
      List<Address> received = ch.locateOwnersForSegment(segment);

      // Check the number of addresses and uniqueness
      assertEquals(received.size(), expectedOwners);
      Set<Address> receivedUnique = new HashSet<Address>(received);
      assertEquals(receivedUnique.size(), expectedOwners);

      // Check the number of machines
      Set<String> receivedMachines = new HashSet<String>();
      for (Address a : received) {
         TopologyAwareAddress taa = (TopologyAwareAddress) a;
         receivedMachines.add(taa.getMachineId() + "|" + taa.getRackId() + "|" + taa.getSiteId());
      }
      assertEquals(receivedMachines.size(), expectedMachines);

      // Check the number of racks
      Set<String> receivedRacks = new HashSet<String>();
      for (Address a : received) {
         TopologyAwareAddress taa = (TopologyAwareAddress) a;
         receivedRacks.add(taa.getRackId() + "|" + taa.getSiteId());
      }
      assertEquals(receivedRacks.size(), expectedRacks);

      // Check the number of sites
      Set<String> receivedSites = new HashSet<String>();
      for (Address a : received) {
         receivedSites.add(((TopologyAwareAddress) a).getSiteId());
      }
      assertEquals(receivedSites.size(), expectedSites);
   }

   private void addNode(TestTopologyAwareAddress address,
                        String machineId, String rackId, String siteId) {
      address.setSiteId(siteId);
      address.setRackId(rackId);
      address.setMachineId(machineId);
      chMembers.add(address);
   }

   protected void updateConsistentHash(int numOwners) {
      ch = chf.create(new MurmurHash3(), numOwners, numSegments, chMembers);
      log.debugf("Created CH with members %s", chMembers);
   }
}
